package org.tradeplanner.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.tradeplanner.config.PlannerConfig;
import org.tradeplanner.data.FleetState;
import org.tradeplanner.data.LogisticsReserve;
import org.tradeplanner.model.RouteLeg;
import org.tradeplanner.model.RoutePlan;
import org.tradeplanner.model.TradeAction;
import org.tradeplanner.service.MarketDataCollector;

import java.awt.Color;
import java.util.List;

/**
 * Large intel layout: summary banner, next-stop job sheet, per-stop cards.
 * Uses TooltipMakerAPI inside the intel CustomPanel (same path as Stelnet).
 */
public final class TradeRouteCustomPanel {

    private TradeRouteCustomPanel() {
    }

    public static void render(TradeRouteIntelPlugin intel, CustomPanelAPI panel, float width, float height) {
        float pad = 10f;
        TooltipMakerAPI info = panel.createUIElement(width, height, true);
        Color h = Misc.getHighlightColor();
        Color pos = Misc.getPositiveHighlightColor();
        Color neg = Misc.getNegativeHighlightColor();
        PlannerConfig cfg = intel.activeConfig();
        RoutePlan plan = intel.getLastPlan();
        int index = intel.getNextWaypointIndex();

        info.addSectionHeading(UiText.TITLE, Alignment.MID, 0f);

        renderButtons(info, plan, intel.isTripFinished(), width, pad);
        if (intel.getLastNavMessage() != null) {
            info.addPara("%s", 3f, h, intel.getLastNavMessage());
        }

        if (plan == null) {
            info.addPara(UiText.NOT_CALCULATED_HINT, pad);
        } else if (plan.isEmpty()) {
            info.addPara(UiText.EMPTY_PLAN, pad, neg, UiText.emptyReason(plan.getEmptyReason()));
        } else if (intel.isTripFinished()) {
            renderBanner(info, plan, pad, h);
            info.addSectionHeading(UiText.SECTION_JOB, Alignment.MID, pad);
            info.addPara(UiText.TRIP_FINISHED_RECALC, pad, h);
            appendTripSummary(info, intel, 3f);
            renderAllStopCards(info, plan, index, pad, h, pos, neg);
        } else {
            renderBanner(info, plan, pad, h);
            renderNextStopSheet(info, plan, index, pad, h, pos, neg);
            renderAllStopCards(info, plan, index, pad, h, pos, neg);
        }

        renderFleetLine(info, intel.getFleetState(), pad, h, cfg);
        info.addButton(UiText.BTN_REFRESH, TradeRouteIntelPlugin.BUTTON_REFRESH, width - 20f, 24f, pad * 2f);

        List<String> factionIds = MarketDataCollector.economyFactionIds();
        String policy = UiText.factionPolicy(intel.getFactionTrade(), cfg, factionIds);
        info.addPara(UiText.SETTINGS_SUMMARY, pad, h,
                UiText.loopKind(cfg.isLoop()),
                String.valueOf(cfg.getMaxDays()),
                policy);
        info.addButton(UiText.BTN_SETTINGS, TradeRouteIntelPlugin.BUTTON_SETTINGS, width - 20f, 24f, 6f);
        panel.addUIElement(info).inTL(0, 0);
    }

    public static void appendSmallSummary(TooltipMakerAPI info, TradeRouteIntelPlugin intel) {
        float pad = 10f;
        Color pos = Misc.getPositiveHighlightColor();
        Color neg = Misc.getNegativeHighlightColor();
        RoutePlan plan = intel.getLastPlan();
        if (plan != null && !plan.isEmpty()) {
            appendPlanTotals(info, plan, pad, false);
            if (intel.isTripFinished()) {
                info.addPara(UiText.TRIP_FINISHED_PERIOD, 3f);
                appendTripSummary(info, intel, 3f);
                return;
            }
            int index = intel.getNextWaypointIndex();
            NextStopReadout.of(intel).append(info, 3f);
            appendTradePreview(info, plan.sellsAtStop(index), false, neg, 2);
            appendTradePreview(info, plan.buysAtStop(index), true, pos, 2);
            appendOperationalBuys(info, plan.getOutgoingLeg(index), pos, 2);
        } else if (plan != null && plan.isEmpty()) {
            info.addPara(UiText.EMPTY_PLAN_SHORT, pad, neg, UiText.emptyReason(plan.getEmptyReason()));
        } else {
            info.addPara(UiText.OPEN_DETAIL_TO_CALCULATE, pad);
        }
    }

    private static void renderBanner(TooltipMakerAPI info, RoutePlan plan, float pad, Color h) {
        info.addSectionHeading(UiText.SECTION_SUMMARY, Alignment.MID, pad);
        if (plan == null || plan.isEmpty()) {
            return;
        }
        appendPlanTotals(info, plan, pad, true);
        info.addPara(UiText.EXTRA_STOPS,
                3f, h,
                UiText.loopKind(plan.isLoop()),
                String.valueOf(plan.getExtraStops()));
    }

    private static void renderButtons(TooltipMakerAPI info, RoutePlan plan, boolean finished,
                                      float width, float pad) {
        float w = width - 20f;
        info.addButton(UiText.BTN_CALCULATE, TradeRouteIntelPlugin.BUTTON_CALCULATE, w, 24f, pad);
        if (plan != null && !plan.isEmpty()) {
            String nav = finished ? UiText.TRIP_FINISHED : UiText.BTN_NAV;
            info.addButton(nav, TradeRouteIntelPlugin.BUTTON_NAVIGATE, w, 24f, pad);
            if (!finished) {
                info.addButton(UiText.BTN_EXECUTE, TradeRouteIntelPlugin.BUTTON_EXECUTE, w, 24f, pad);
                info.addButton(UiText.BTN_ARRIVE, TradeRouteIntelPlugin.BUTTON_ARRIVE, w, 24f, pad);
            }
        }
        if (plan != null) {
            info.addButton(UiText.BTN_CLEAR, TradeRouteIntelPlugin.BUTTON_CLEAR, w, 24f, pad);
        }
    }

    private static void renderNextStopSheet(TooltipMakerAPI info, RoutePlan plan, int index,
                                            float pad, Color h, Color pos, Color neg) {
        info.addSectionHeading(UiText.SECTION_JOB, Alignment.MID, pad);
        RouteLeg incoming = plan.getIncomingLeg(index);
        if (incoming == null) {
            info.addPara(UiText.NO_NEXT_STOP, pad);
            return;
        }
        info.addPara(UiText.JOB_QTY_NOTE, 3f);
        NextStopReadout.of(TradeRouteIntelPlugin.getInstance()).append(info, pad);
        info.addPara(UiText.PLANNED_TRAVEL,
                3f, h,
                String.format("%.1f", incoming.getTotalDays()),
                String.format("%.1f", incoming.getHyperspaceDays()),
                String.format("%.1f", incoming.getInSystemDays()));
        // 旅行油/补给成本暂不计（调试）
        // info.addPara("超空间油 %s，补给 %s，油、补给成本 %s（预计）",
        //         3f, h,
        //         String.format("%.0f", incoming.getHyperspaceFuel()),
        //         String.format("%.1f", incoming.getSupplyUnits()),
        //         Misc.getDGSCredits(incoming.getOpsCost()));

        List<TradeAction> sells = plan.sellsAtStop(index);
        List<TradeAction> buys = plan.buysAtStop(index);
        RouteLeg outgoing = plan.getOutgoingLeg(index);
        boolean opsBuy = outgoing != null
                && (outgoing.getFuelPurchased() > 0.5f || outgoing.getSupplyPurchased() > 0.5f);
        if (incoming.isAlreadyThere()) {
            info.addPara(UiText.ALREADY_HERE_BUY, 3f, h);
        } else if (incoming.isPositioning()) {
            info.addPara(UiText.POSITIONING_EMPTY, 3f, h);
        } else if (incoming.isEmptyCargo()) {
            if (opsBuy) {
                info.addPara(UiText.EMPTY_ARRIVE_RESTOCK, 3f, h);
            } else {
                info.addPara(UiText.EMPTY_RETURN, 3f, h);
            }
        } else {
            info.addPara(UiText.BUY_AT_ORIGIN,
                    3f, h, incoming.getFromName() == null ? "?" : incoming.getFromName());
        }
        renderSellList(info, sells, pad, neg);
        renderBuyList(info, buys, pad, pos);
        appendOperationalBuys(info, outgoing, pos, 8);
        if (!incoming.isPositioning() && sells.isEmpty() && buys.isEmpty() && !opsBuy) {
            info.addPara(UiText.NO_STOP_TRADE, 3f);
        }
    }

    private static void renderAllStopCards(TooltipMakerAPI info, RoutePlan plan, int current,
                                           float pad, Color h, Color pos, Color neg) {
        info.addSectionHeading(UiText.SECTION_ITINERARY, Alignment.MID, pad);
        int n = plan.getStopCount();
        for (int i = 0; i < n; i++) {
            RouteLeg incoming = plan.getIncomingLeg(i);
            if (incoming == null) {
                continue;
            }
            boolean here = i == current;
            String mark = here ? UiText.HERE_MARK : "";
            String system = systemName(incoming.getToMarketId());
            info.addPara(UiText.STOP_CARD,
                    pad, here ? pos : h,
                    String.valueOf(i + 1),
                    incoming.getToName(),
                    system.isEmpty() ? "" : "（" + system + "）",
                    mark);
            info.addPara(UiText.CARD_ETA,
                    3f, h,
                    String.format("%.1f", incoming.getTotalDays()));
            // 旅行油/补给成本暂不计（调试）
            // info.addPara("    预计 %s 天，油 %s，补给 %s，油、补给成本 %s",
            //         3f, h,
            //         String.format("%.1f", incoming.getTotalDays()),
            //         String.format("%.0f", incoming.getHyperspaceFuel()),
            //         String.format("%.1f", incoming.getSupplyUnits()),
            //         Misc.getDGSCredits(incoming.getOpsCost()));
            if (incoming.getFuelPurchased() > 0.5f) {
                info.addPara(UiText.MID_FUEL, 3f, h,
                        String.format("%.0f", incoming.getFuelPurchased()),
                        Misc.getDGSCredits(incoming.getFuelPurchaseCost()));
            }
            if (incoming.getSupplyPurchased() > 0.5f) {
                info.addPara(UiText.MID_SUPPLY, 3f, h,
                        String.format("%.0f", incoming.getSupplyPurchased()),
                        Misc.getDGSCredits(incoming.getSupplyPurchaseCost()));
            }
            if (incoming.isAlreadyThere()) {
                info.addPara(UiText.CARD_ALREADY_HERE, 3f);
            } else if (incoming.isPositioning()) {
                info.addPara(UiText.CARD_POSITIONING, 3f);
            } else if (incoming.isEmptyCargo()) {
                info.addPara(UiText.CARD_EMPTY_RETURN, 3f);
            }
            renderSellList(info, plan.sellsAtStop(i), 3f, neg);
            renderBuyList(info, plan.buysAtStop(i), 3f, pos);
            appendOperationalBuys(info, plan.getOutgoingLeg(i), pos, 8);
        }
    }

    private static void renderSellList(TooltipMakerAPI info, List<TradeAction> sells, float pad, Color neg) {
        if (sells == null || sells.isEmpty()) {
            return;
        }
        for (TradeAction action : sells) {
            info.addPara(UiText.SELL_LINE,
                    pad, neg,
                    UiText.channelName(action, false),
                    String.valueOf(action.getQuantity()),
                    Misc.getDGSCredits(action.getSellRevenue()));
        }
    }

    private static void renderBuyList(TooltipMakerAPI info, List<TradeAction> buys, float pad, Color pos) {
        if (buys == null || buys.isEmpty()) {
            return;
        }
        for (TradeAction action : buys) {
            info.addPara(UiText.BUY_LINE,
                    pad, pos,
                    UiText.channelName(action, true),
                    String.valueOf(action.getQuantity()),
                    Misc.getDGSCredits(action.getBuyCost()));
        }
    }

    public static void appendPlanTotals(TooltipMakerAPI info, RoutePlan plan, float pad) {
        appendPlanTotals(info, plan, pad, false);
    }

    public static void appendPlanTotals(TooltipMakerAPI info, RoutePlan plan, float pad, boolean details) {
        if (plan == null || plan.isEmpty()) {
            return;
        }
        Color value = plan.getNetProfit() >= 0f
                ? Misc.getPositiveHighlightColor()
                : Misc.getNegativeHighlightColor();
        info.addPara(UiText.PLAN_TOTALS,
                pad, value,
                Misc.getDGSCredits(plan.getNetProfit()),
                String.format("%.1f", plan.getTotalDays()),
                Misc.getDGSCredits(plan.getCreditsPerDay()));
        if (!details) {
            return;
        }
        if (plan.getPositioningDays() > 0.05f) {
            info.addPara(UiText.POSITIONING_NOTE,
                    3f, Misc.getHighlightColor(),
                    String.format("%.1f", plan.getPositioningDays()));
        }
        info.addPara(UiText.PROFIT_COMPARE_NOTE, 3f);
        appendTruncationNote(info, plan, 3f);
    }

    public static String formatComputeTime(RoutePlan plan) {
        if (plan == null) {
            return UiText.formatSeconds(0L);
        }
        return UiText.searchDuration(plan.getDisplayMs(), plan.isTruncated());
    }

    public static String formatComputeTimeDetail(RoutePlan plan) {
        return formatComputeTime(plan);
    }

    public static void appendComputeTime(TooltipMakerAPI info, RoutePlan plan, float pad) {
        appendTruncationNote(info, plan, pad);
    }

    public static void appendTruncationNote(TooltipMakerAPI info, RoutePlan plan, float pad) {
        if (info == null || plan == null || !plan.isTruncated()) {
            return;
        }
        info.addPara(UiText.SEARCH_TIME_UP, pad, Misc.getHighlightColor());
    }

    public static void appendTripSummary(TooltipMakerAPI info, TradeRouteIntelPlugin intel, float pad) {
        if (intel == null || !intel.hasTripSummary()) {
            return;
        }
        Color value = intel.getTripActualNet() >= 0f
                ? Misc.getPositiveHighlightColor()
                : Misc.getNegativeHighlightColor();
        String title = intel.getLastPlan() != null && intel.getLastPlan().isLoop()
                ? UiText.LOOP_SUMMARY_TITLE
                : UiText.TRIP_SUMMARY_TITLE;
        info.addPara(UiText.TRIP_SUMMARY,
                pad, value,
                title,
                String.format("%.1f", intel.getTripActualDays()),
                Misc.getDGSCredits(intel.getTripActualNet()),
                Misc.getDGSCredits(intel.getTripActualCpd()));
    }

    public static void appendTradePreview(TooltipMakerAPI info, List<TradeAction> actions,
                                          boolean buy, Color color, int limit) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        int n = Math.min(limit, actions.size());
        for (int i = 0; i < n; i++) {
            TradeAction action = actions.get(i);
            String prefix = buy ? UiText.BUY_PREFIX : UiText.SELL_PREFIX;
            info.addPara(UiText.PREVIEW_LINE, 3f, color,
                    prefix,
                    UiText.channelName(action, buy),
                    String.valueOf(action.getQuantity()));
        }
        if (actions.size() > n) {
            info.addPara(UiText.PREVIEW_MORE, 3f, Misc.getHighlightColor(),
                    "" + (actions.size() - n));
        }
    }

    public static void appendOperationalBuys(TooltipMakerAPI info, RouteLeg outgoing, Color pos, int limit) {
        if (info == null || outgoing == null || pos == null || limit <= 0) {
            return;
        }
        int shown = 0;
        if (outgoing.getSupplyPurchased() > 0.5f && shown < limit) {
            info.addPara(UiText.PREVIEW_SUPPLY, 3f, pos,
                    String.format("%.0f", outgoing.getSupplyPurchased()));
            shown++;
        }
        if (outgoing.getFuelPurchased() > 0.5f && shown < limit) {
            info.addPara(UiText.PREVIEW_FUEL, 3f, pos,
                    String.format("%.0f", outgoing.getFuelPurchased()));
        }
    }

    private static void renderFleetLine(TooltipMakerAPI info, FleetState fleet, float pad, Color h,
                                        PlannerConfig cfg) {
        info.addSectionHeading(UiText.SECTION_FLEET, Alignment.MID, pad);
        if (fleet == null) {
            info.addPara(UiText.NO_FLEET_SNAPSHOT, pad);
            return;
        }
        info.addPara(UiText.FLEET_LINE,
                pad, h,
                Misc.getDGSCredits(fleet.getCredits()),
                String.format("%.0f", fleet.getCargoLeft()),
                String.format("%.0f", fleet.getSupplies()),
                String.valueOf(LogisticsReserve.supplyFloor(cfg, fleet)),
                String.format("%.0f", fleet.getFuel()),
                String.format("%.0f", fleet.getFuelMax()),
                String.valueOf(LogisticsReserve.fuelFloor(cfg, fleet)),
                fleet.isInHyperspace() ? UiText.HYPERSPACE : fleet.getLocationName());
    }

    public static float travelDays(RouteLeg leg) {
        return leg == null ? 0f : leg.getTotalDays();
    }

    private static String systemName(String marketId) {
        if (marketId == null || RouteLeg.FLEET_ORIGIN_ID.equals(marketId)) {
            return "";
        }
        MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
        if (market == null || market.isInHyperspace() || market.getStarSystem() == null) {
            return "";
        }
        return market.getStarSystem().getNameWithLowercaseType();
    }
}
