package org.tradeplanner.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.tradeplanner.data.FleetState;
import org.tradeplanner.service.DistanceCalculator;
import org.tradeplanner.service.DistanceCalculator.TravelEstimate;

import java.awt.Color;

/**
 * Next-stop line using the same campaign course APIs as the vanilla destination widget:
 * {@link CampaignUIAPI#getNameForCourseTarget}, {@link CampaignUIAPI#getLastLegDistance},
 * {@link Misc#getLYPerDayAtBurn}. Name color is the location faction's {@code baseUIColor}.
 */
final class NextStopReadout {

    final String name;
    final Color nameColor;
    final String distText;
    final String daysText;
    final boolean arrived;
    final String signature;

    private NextStopReadout(String name, Color nameColor, String distText, String daysText,
                            boolean arrived, String signature) {
        this.name = name;
        this.nameColor = nameColor;
        this.distText = distText;
        this.daysText = daysText;
        this.arrived = arrived;
        this.signature = signature;
    }

    static NextStopReadout of(TradeRouteIntelPlugin intel) {
        Color hl = Misc.getHighlightColor();
        if (intel == null || intel.getLastPlan() == null || intel.getLastPlan().isEmpty()) {
            return new NextStopReadout(UiText.NO_PLAN_YET, hl, "", "", false, "none");
        }
        if (intel.isTripFinished()) {
            return new NextStopReadout(UiText.TRIP_FINISHED, hl, "", "", false, "done");
        }
        SectorEntityToken dest = intel.currentWaypointEntity();
        String fallback = intel.getLastPlan().getStopMarketName(intel.getNextWaypointIndex());
        if (dest == null) {
            String name = fallback == null ? "?" : fallback;
            return new NextStopReadout(name, hl, "?", "?", false, "unresolved:" + name);
        }
        String name = courseName(dest, true);
        Color color = locationColor(dest);
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet != null && TradeRouteIntelPlugin.isArrivedAt(fleet, dest)) {
            return new NextStopReadout(name, color, UiText.IN_DOCK_RANGE, "0.0", true,
                    dest.getId() + ":arrived");
        }
        float distLY = remainingLY(dest, fleet);
        float days = remainingDays(dest, fleet, distLY);
        String distText = UiText.ly(distLY);
        String daysText = String.format("%.1f", days);
        return new NextStopReadout(name, color, distText, daysText, false,
                dest.getId() + ":" + Math.round(distLY * 10f) + ":" + Math.round(days * 10f));
    }

    void append(TooltipMakerAPI info, float pad) {
        Color hl = Misc.getHighlightColor();
        if (distText == null || distText.isEmpty()) {
            info.addPara("%s", pad, nameColor, name);
            return;
        }
        if (arrived) {
            LabelAPI label = info.addPara(UiText.NEXT_STOP_ARRIVED, pad, nameColor, name, distText);
            if (label != null) {
                label.setHighlight(name, distText);
                label.setHighlightColors(nameColor, hl);
            }
            return;
        }
        LabelAPI label = info.addPara(UiText.NEXT_STOP_ETA,
                pad, nameColor, name, distText, daysText);
        if (label != null) {
            label.setHighlight(name, distText, daysText);
            label.setHighlightColors(nameColor, hl, hl);
        }
    }

    private static String courseName(SectorEntityToken dest, boolean endpoint) {
        try {
            String name = Global.getSector().getCampaignUI().getNameForCourseTarget(dest, endpoint);
            if (name != null && !name.isEmpty()) {
                return name;
            }
        } catch (Exception ignored) {
        }
        return dest.getName() == null ? "?" : dest.getName();
    }

    private static Color locationColor(SectorEntityToken dest) {
        try {
            MarketAPI market = dest.getMarket();
            FactionAPI faction = market != null ? market.getFaction() : dest.getFaction();
            if (faction != null && faction.getBaseUIColor() != null) {
                return faction.getBaseUIColor();
            }
        } catch (Exception ignored) {
        }
        return Misc.getHighlightColor();
    }

    /**
     * Prefer vanilla current-leg distance (same number as the dest widget). If that looks like
     * pixels, convert with {@code unitsPerLightYear}. Fallback: hyperspace LY or in-system map LY.
     */
    private static float remainingLY(SectorEntityToken dest, CampaignFleetAPI fleet) {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        SectorEntityToken hop = null;
        try {
            hop = ui.getNextStepForCourse(dest);
        } catch (Exception ignored) {
        }
        try {
            float raw = ui.getLastLegDistance(dest);
            if (raw > 0.001f && (hop == null || sameEntity(hop, dest))) {
                return toLY(raw);
            }
        } catch (Exception ignored) {
        }
        if (fleet == null) {
            return 0f;
        }
        if (fleet.getContainingLocation() != null
                && fleet.getContainingLocation() == dest.getContainingLocation()
                && !fleet.isInHyperspace()) {
            return Misc.getDistance(fleet, dest) / unitsPerLY();
        }
        return Misc.getDistanceLY(fleet, dest);
    }

    private static float remainingDays(SectorEntityToken dest, CampaignFleetAPI fleet, float distLY) {
        if (fleet == null) {
            return 0f;
        }
        SectorEntityToken hop = null;
        try {
            hop = Global.getSector().getCampaignUI().getNextStepForCourse(dest);
        } catch (Exception ignored) {
        }
        if (hop == null || sameEntity(hop, dest)) {
            try {
                float burn = Math.max(1f, fleet.getFleetData().getBurnLevel());
                float lyPerDay = Misc.getLYPerDayAtBurn(fleet, burn);
                if (lyPerDay > 0.001f) {
                    return distLY / lyPerDay;
                }
            } catch (Exception ignored) {
            }
        }
        try {
            TravelEstimate est = DistanceCalculator.estimateTo(FleetState.fromPlayer(), dest);
            if (est.getTotalDays() > 0.001f) {
                return est.getTotalDays();
            }
        } catch (Exception ignored) {
        }
        return 0f;
    }

    private static boolean sameEntity(SectorEntityToken a, SectorEntityToken b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.getId() == null) {
            return false;
        }
        return a.getId().equals(b.getId());
    }

    private static float toLY(float raw) {
        if (raw > 80f) {
            return raw / unitsPerLY();
        }
        return raw;
    }

    private static float unitsPerLY() {
        try {
            float per = Global.getSettings().getUnitsPerLightYear();
            if (per >= 1f) {
                return per;
            }
        } catch (Exception ignored) {
        }
        return 2000f;
    }
}
