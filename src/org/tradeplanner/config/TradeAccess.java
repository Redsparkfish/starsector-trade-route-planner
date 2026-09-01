package org.tradeplanner.config;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import org.tradeplanner.data.MarketSnapshot;

/**
 * Open vs black-market channel for a market under the current planner config.
 * Used by search packing and by stop execution; not a UI concern.
 */
public final class TradeAccess {

    private TradeAccess() {
    }

    public static boolean isUsable(MarketSnapshot snap, PlannerConfig config) {
        if (snap == null) {
            return false;
        }
        if (useBlack(snap, config) && snap.isBlackUsable()) {
            return true;
        }
        if (config != null && !config.allowOpenMarket(snap.getFactionId())) {
            return false;
        }
        return isOpenUsable(snap);
    }

    public static boolean isOpenUsable(MarketSnapshot snap) {
        if (snap == null || !snap.hasOpenMarket()) {
            return false;
        }
        if (snap.isHostileToPlayer()) {
            return snap.isOpenUsableWithCurrentTransponder();
        }
        return true;
    }

    /**
     * True when this market has a usable black-market channel for packing.
     * Dual-channel markets also keep the open market (black first, then open).
     */
    public static boolean useBlack(MarketSnapshot snap, PlannerConfig config) {
        if (snap == null || !snap.hasBlackMarket() || config == null) {
            return false;
        }
        return config.allowBlackMarket(snap.getFactionId());
    }

    /** Live-market equivalent of {@link #useBlack(MarketSnapshot, PlannerConfig)}. */
    public static boolean useBlack(MarketAPI market, PlannerConfig config) {
        if (market == null || config == null) {
            return false;
        }
        if (!market.hasSubmarket(Submarkets.SUBMARKET_BLACK)) {
            return false;
        }
        return config.allowBlackMarket(market.getFactionId());
    }

    public static boolean canBlack(MarketSnapshot snap, PlannerConfig config) {
        if (snap == null || config == null || !snap.hasBlackMarket()) {
            return false;
        }
        return config.allowBlackMarket(snap.getFactionId());
    }

    public static boolean canOpen(MarketSnapshot snap, PlannerConfig config) {
        if (snap == null || config == null || !snap.hasOpenMarket()) {
            return false;
        }
        if (!config.allowOpenMarket(snap.getFactionId())) {
            return false;
        }
        if (snap.isHostileToPlayer()) {
            return snap.isOpenUsableWithCurrentTransponder();
        }
        return true;
    }

    public static boolean canOpen(MarketAPI market, PlannerConfig config) {
        if (market == null || config == null) {
            return false;
        }
        if (!config.allowOpenMarket(market.getFactionId())) {
            return false;
        }
        if (!market.hasSubmarket(Submarkets.SUBMARKET_OPEN)) {
            return false;
        }
        FactionAPI player = Global.getSector().getPlayerFaction();
        boolean hostile = market.getFaction() != null && player != null
                && market.getFaction().isHostileTo(player);
        if (!hostile) {
            return true;
        }
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        return fleet != null && fleet.isTransponderOn();
    }
}
