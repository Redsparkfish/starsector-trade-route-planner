package org.tradeplanner.service;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.impl.campaign.submarkets.OpenMarketPlugin;

/**
 * Unbiased estimate of shop-shelf quantity relative to vanilla restock.
 *
 * <p>Unvisited: {@code E[L] = L0 × S}. {@code L0} is vanilla
 * {@code getApproximateStockpileLimit} (import × 0.1, production × 0.4, extra × 1,
 * deficit × 0.2). Opening trade uses {@code L0 × U × S} with {@code E[U]=1}, so
 * {@code L0 × S} is unbiased for the restock cap, not a rewrite of those coefficients.
 *
 * <p>Visited: leftover via {@code getCargoNullOk} only, then vanilla add
 * {@code L/30} per day or remove {@code (Q-L)×2/30} per day toward that cap.
 * Never call {@code SubmarketAPI.getCargo()} — that materializes an empty hold
 * on unvisited markets.
 */
public final class ShelfQuantityEstimator {

    /** Vanilla add rate is {@code limit / 30} units per day. */
    public static final float RESTOCK_DAYS = 30f;

    private ShelfQuantityEstimator() {
    }

    public static float stabilityMultOpen(float stability) {
        return 0.25f + 0.75f * clamp01(stability / 10f);
    }

    public static float stabilityMultBlack(float stability) {
        return 0.25f + 0.75f * clamp01(1f - stability / 10f);
    }

    public static float stabilityMult(float stability, boolean black) {
        return black ? stabilityMultBlack(stability) : stabilityMultOpen(stability);
    }

    /** Vanilla restock base \(L_0\). Same as {@link #plannedBaseLimit}. */
    public static int vanillaBaseLimit(CommodityOnMarketAPI com) {
        if (com == null || com.getCommodity() == null) {
            return 0;
        }
        return Math.max(0, OpenMarketPlugin.getApproximateStockpileLimit(com));
    }

    /** Planning uses vanilla \(L_0\); kept as a named alias for call sites. */
    public static int plannedBaseLimit(CommodityOnMarketAPI com) {
        return vanillaBaseLimit(com);
    }

    /**
     * Expected fully-restocked shelf for the planned submarket (open or black).
     * Illegal goods on the open market are 0 — vanilla never adds them.
     */
    public static int expectedLimit(MarketAPI market, CommodityOnMarketAPI com, boolean black) {
        if (market == null || com == null || com.getCommodity() == null) {
            return 0;
        }
        String subId = submarketId(black);
        if (!market.hasSubmarket(subId)) {
            return 0;
        }
        if (!black && market.isIllegal(com.getId())) {
            return 0;
        }
        int l0 = vanillaBaseLimit(com);
        float s = stabilityMult(market.getStabilityValue(), black);
        return (int) Math.floor(l0 * s);
    }

    /**
     * Buy cap before safety margin: \(L_0 \times S\) if unvisited, otherwise leftover
     * projected toward that cap with vanilla restock / drawdown rates.
     */
    public static int estimateBuy(MarketAPI market, CommodityOnMarketAPI com, boolean black) {
        int limit = expectedLimit(market, com, black);
        if (limit <= 0) {
            return 0;
        }
        return projectTowardLimit(market, com, black, limit);
    }

    /**
     * Sell cap before safety margin. Asymmetric with buy: a shortage shop has little
     * stock but still wants goods; dumping into a surplus producer is skipped.
     */
    public static int estimateSell(MarketAPI market, CommodityOnMarketAPI com, boolean black) {
        if (market == null || com == null || com.getCommodity() == null) {
            return 0;
        }
        if (!black && market.isIllegal(com.getId())) {
            return 0;
        }
        int deficit = Math.max(0, com.getDeficitQuantity());
        int excess = Math.max(0, com.getExcessQuantity());
        if (deficit > 0) {
            return deficit;
        }
        if (excess > 0) {
            return 0;
        }
        return expectedLimit(market, com, black);
    }

    static int projectTowardLimit(MarketAPI market, CommodityOnMarketAPI com, boolean black, int limit) {
        if (limit <= 0) {
            return 0;
        }
        ShelfState state = shelfState(market, black);
        if (state.cargo == null || com.getId() == null) {
            return limit;
        }
        float curr = Math.max(0f, state.cargo.getCommodityQuantity(com.getId()));
        float days = Math.max(0f, state.days);
        float target = limit;
        if (curr < target - 0.0001f) {
            float add = (target / RESTOCK_DAYS) * days;
            return (int) Math.floor(Math.min(target, curr + add));
        }
        if (curr > target + 0.0001f) {
            float remove = (curr - target) * 2f / RESTOCK_DAYS * days;
            return (int) Math.floor(Math.max(target, curr - remove));
        }
        return limit;
    }

    private static ShelfState shelfState(MarketAPI market, boolean black) {
        ShelfState state = new ShelfState();
        if (market == null) {
            return state;
        }
        String subId = submarketId(black);
        if (!market.hasSubmarket(subId)) {
            return state;
        }
        SubmarketAPI sub = market.getSubmarket(subId);
        if (sub == null || !(sub.getPlugin() instanceof BaseSubmarketPlugin)) {
            return state;
        }
        BaseSubmarketPlugin plugin = (BaseSubmarketPlugin) sub.getPlugin();
        state.cargo = plugin.getCargoNullOk();
        state.days = plugin.getSinceLastCargoUpdate();
        return state;
    }

    private static String submarketId(boolean black) {
        return black ? Submarkets.SUBMARKET_BLACK : Submarkets.SUBMARKET_OPEN;
    }

    private static float clamp01(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }

    private static final class ShelfState {
        CargoAPI cargo;
        float days;
    }
}
