package org.tradeplanner.service;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import org.tradeplanner.data.FleetState;
import org.tradeplanner.data.MarketSnapshot;

import java.util.List;

/**
 * Approximate travel time and hyperspace fuel. Times must be labeled 预计 — this ignores
 * slipstreams, Sustained Burn, storms, and orbital motion.
 *
 * <p>In-game check still needed (docs/starsector-dev.md §9 item 4): first-leg composition
 * when the player is in-system vs already in hyperspace. Calculate-route logs
 * {@code TradeRoutePlanner firstLeg:} for that comparison.
 *
 * <p>Hyperspace fuel is {@code distLY × getFuelCostPerLightYear()}. Do not bill that rate
 * for in-system legs. In-system fuel ({@code getFuelUseNormalMult}) is omitted here (usually small).
 */
public final class DistanceCalculator {

    private DistanceCalculator() {
    }

    public static final class TravelEstimate {
        private final float hyperspaceLY;
        private final float hyperspaceDays;
        private final float inSystemDays;
        private final float totalDays;
        private final float hyperspaceFuel;
        private final float supplyUnits;
        private final String destName;
        private final boolean sameLocation;

        public TravelEstimate(float hyperspaceLY, float hyperspaceDays, float inSystemDays,
                              float totalDays, float hyperspaceFuel, float supplyUnits,
                              String destName, boolean sameLocation) {
            this.hyperspaceLY = hyperspaceLY;
            this.hyperspaceDays = hyperspaceDays;
            this.inSystemDays = inSystemDays;
            this.totalDays = totalDays;
            this.hyperspaceFuel = hyperspaceFuel;
            this.supplyUnits = supplyUnits;
            this.destName = destName;
            this.sameLocation = sameLocation;
        }

        public float getHyperspaceLY() {
            return hyperspaceLY;
        }

        public float getHyperspaceDays() {
            return hyperspaceDays;
        }

        public float getInSystemDays() {
            return inSystemDays;
        }

        /** Estimated days only — not a clock. */
        public float getTotalDays() {
            return totalDays;
        }

        public float getHyperspaceFuel() {
            return hyperspaceFuel;
        }

        public float getSupplyUnits() {
            return supplyUnits;
        }

        public String getDestName() {
            return destName;
        }

        public boolean isSameLocation() {
            return sameLocation;
        }
    }

    public static final class NearestMarket {
        public final MarketSnapshot market;
        public final TravelEstimate estimate;

        public NearestMarket(MarketSnapshot market, TravelEstimate estimate) {
            this.market = market;
            this.estimate = estimate;
        }
    }

    public static TravelEstimate estimateTo(FleetState fleetState, SectorEntityToken dest) {
        CampaignFleetAPI fleet = fleetState.getFleet();
        return estimateBetween(fleet, dest, fleet, fleetState.getBurnLevel(),
                fleetState.getFuelPerLightYear(), fleetState.getSuppliesPerDay());
    }

    public static TravelEstimate estimate(CampaignFleetAPI fleet, SectorEntityToken dest,
                                          float burnLevel, float fuelPerLY, float suppliesPerDay) {
        return estimateBetween(fleet, dest, fleet, burnLevel, fuelPerLY, suppliesPerDay);
    }

    public static TravelEstimate estimateBetween(MarketSnapshot from, MarketSnapshot to, FleetState fleetState) {
        if (from == null || to == null || fleetState == null) {
            return new TravelEstimate(0f, 0f, 0f, 0f, 0f, 0f, "?", false);
        }
        return estimateBetween(from.getPrimaryEntity(), to.getPrimaryEntity(), fleetState.getFleet(),
                fleetState.getBurnLevel(), fleetState.getFuelPerLightYear(), fleetState.getSuppliesPerDay());
    }

    /**
     * Approximate travel from one entity to another (market↔market or fleet→market).
     * Hyperspace fuel is billed per light-year only; in-system legs do not use that rate.
     */
    public static TravelEstimate estimateBetween(SectorEntityToken from, SectorEntityToken to,
                                                 CampaignFleetAPI fleet,
                                                 float burnLevel, float fuelPerLY, float suppliesPerDay) {
        if (from == null || to == null || fleet == null) {
            return new TravelEstimate(0f, 0f, 0f, 0f, 0f, 0f, "?", false);
        }
        String destName = to.getName();
        LocationAPI fromLoc = from.getContainingLocation();
        LocationAPI toLoc = to.getContainingLocation();
        boolean sameLoc = fromLoc != null && fromLoc == toLoc;
        boolean fromInHyper = isInHyperspace(from, fromLoc);

        float inSystemDays = 0f;
        float hyperLY = 0f;

        if (sameLoc && fromLoc != null && !fromLoc.isHyperspace()) {
            inSystemDays += pixelDays(from, to, fleet);
        } else if (sameLoc && fromLoc != null && fromLoc.isHyperspace()) {
            hyperLY = Misc.getDistanceLY(from, to);
        } else {
            if (fromLoc != null && !fromInHyper) {
                JumpPointAPI exit = Misc.findNearestJumpPointTo(from);
                if (exit != null) {
                    inSystemDays += pixelDays(from, exit, fleet);
                }
            }
            hyperLY = Misc.getDistanceLY(from, to);
            if (toLoc != null && !toLoc.isHyperspace()) {
                JumpPointAPI entry = Misc.findNearestJumpPointTo(to);
                if (entry != null) {
                    inSystemDays += pixelDays(entry, to, fleet);
                }
            }
        }

        float burn = Math.max(1f, burnLevel);
        float lyPerDay = Misc.getLYPerDayAtBurn(fleet, burn);
        if (lyPerDay < 0.001f) {
            lyPerDay = 0.001f;
        }
        float hyperDays = hyperLY / lyPerDay;
        float totalDays = hyperDays + inSystemDays;
        float hyperFuel = hyperLY * fuelPerLY;
        float supplies = totalDays * suppliesPerDay;
        return new TravelEstimate(hyperLY, hyperDays, inSystemDays, totalDays, hyperFuel, supplies, destName, sameLoc);
    }

    private static boolean isInHyperspace(SectorEntityToken token, LocationAPI loc) {
        if (token instanceof CampaignFleetAPI && ((CampaignFleetAPI) token).isInHyperspace()) {
            return true;
        }
        return loc == null || loc.isHyperspace();
    }

    /**
     * In-system days from pixel distance and fleet travel speed (px/s, ~10 real seconds per day).
     */
    public static float pixelDays(SectorEntityToken from, SectorEntityToken to, CampaignFleetAPI fleet) {
        if (from == null || to == null) {
            return 0f;
        }
        if (from.getContainingLocation() != to.getContainingLocation()) {
            return 0f;
        }
        float dist = Misc.getDistance(from, to);
        float pxPerSec = fleet.getFleetData().getTravelSpeed();
        if (pxPerSec < 1f) {
            float burn = Math.max(1f, fleet.getFleetData().getBurnLevel());
            pxPerSec = Global.getSettings().getSpeedPerBurnLevel() * burn;
        }
        float secPerDay = Global.getSector().getClock().getSecondsPerDay();
        if (secPerDay < 1f) {
            secPerDay = 10f;
        }
        return dist / (pxPerSec * secPerDay);
    }

    public static NearestMarket nearestMarket(FleetState fleet, List<MarketSnapshot> markets) {
        MarketSnapshot best = null;
        TravelEstimate bestEst = null;
        float bestDays = Float.MAX_VALUE;
        for (MarketSnapshot snap : markets) {
            SectorEntityToken entity = snap.getPrimaryEntity();
            if (entity == null) {
                continue;
            }
            TravelEstimate est = estimateTo(fleet, entity);
            if (est.getTotalDays() < bestDays) {
                bestDays = est.getTotalDays();
                best = snap;
                bestEst = est;
            }
        }
        return new NearestMarket(best, bestEst);
    }
}
