package org.tradeplanner.service;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import org.tradeplanner.config.PlannerConfig;
import org.tradeplanner.data.CommodityTradeInfo;
import org.tradeplanner.data.FleetState;
import org.tradeplanner.data.MarketSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Economy-layer market scan. Must not call {@code SubmarketAPI.getCargo()} — that
 * materializes an empty hold on unvisited markets. Buy/sell caps come from
 * {@link ShelfQuantityEstimator} ({@code getCargoNullOk} leftover only). Sell qty is not
 * capped here — the knapsack uses batch demand quotes.
 */
public final class MarketDataCollector {

    public static final Set<String> ECON_COMMODITY_IDS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            Commodities.SUPPLIES,
            Commodities.FUEL,
            Commodities.FOOD,
            Commodities.ORGANICS,
            Commodities.VOLATILES,
            Commodities.ORE,
            Commodities.RARE_ORE,
            Commodities.METALS,
            Commodities.RARE_METALS,
            Commodities.HEAVY_MACHINERY,
            Commodities.DOMESTIC_GOODS,
            Commodities.ORGANS,
            Commodities.DRUGS,
            Commodities.HAND_WEAPONS,
            Commodities.LUXURY_GOODS,
            Commodities.LOBSTER
    )));

    private MarketDataCollector() {
    }

    public static List<MarketSnapshot> collect(PlannerConfig config, FleetState fleet) {
        List<MarketSnapshot> out = new ArrayList<>();
        List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
        FactionAPI playerFaction = Global.getSector().getPlayerFaction();
        boolean transponderOn = fleet != null && fleet.isTransponderOn();
        for (MarketAPI market : markets) {
            if (!isCandidateMarket(market)) {
                continue;
            }
            out.add(snapshot(market, config, playerFaction, transponderOn));
        }
        return out;
    }

    /** Faction IDs that currently own at least one economy-layer market. Sorted by display name. */
    public static List<String> economyFactionIds() {
        Set<String> ids = new LinkedHashSet<>();
        try {
            List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
            for (MarketAPI market : markets) {
                if (!isCandidateMarket(market)) {
                    continue;
                }
                String id = market.getFactionId();
                if (id != null && !id.isEmpty()) {
                    ids.add(id);
                }
            }
        } catch (Exception ignored) {
        }
        List<String> out = new ArrayList<>(ids);
        Collections.sort(out, (a, b) -> PlannerConfig.factionDisplayName(a)
                .compareToIgnoreCase(PlannerConfig.factionDisplayName(b)));
        return out;
    }

    public static boolean isCandidateMarket(MarketAPI market) {
        if (market == null) {
            return false;
        }
        if (!market.isInEconomy()) {
            return false;
        }
        if (market.isHidden()) {
            return false;
        }
        if (market.isPlanetConditionMarketOnly()) {
            return false;
        }
        return true;
    }

    public static boolean isTradeCommodity(CommoditySpecAPI spec) {
        if (spec == null) {
            return false;
        }
        if (spec.isNonEcon() || spec.isMeta() || spec.isPersonnel()) {
            return false;
        }
        return ECON_COMMODITY_IDS.contains(spec.getId());
    }

    private static MarketSnapshot snapshot(MarketAPI market, PlannerConfig config,
                                          FactionAPI playerFaction, boolean transponderOn) {
        boolean hasOpen = market.hasSubmarket(Submarkets.SUBMARKET_OPEN);
        boolean hasBlack = market.hasSubmarket(Submarkets.SUBMARKET_BLACK);
        float openTariff = hasOpen
                ? market.getSubmarket(Submarkets.SUBMARKET_OPEN).getTariff()
                : market.getTariff().getModifiedValue();
        boolean hostile = market.getFaction() != null && playerFaction != null
                && market.getFaction().isHostileTo(playerFaction);
        StarSystemAPI system = market.getStarSystem();
        String systemName = market.isInHyperspace() || system == null
                ? "超空间"
                : system.getNameWithLowercaseType();
        String factionName = market.getFaction() == null ? "?" : market.getFaction().getDisplayName();
        float margin = config == null ? PlannerConfig.DEFAULT_QTY_SAFETY_MARGIN : config.getQtySafetyMargin();

        List<CommodityTradeInfo> rows = new ArrayList<>();
        for (CommodityOnMarketAPI com : market.getAllCommodities()) {
            CommoditySpecAPI spec = com.getCommodity();
            if (!isTradeCommodity(spec)) {
                continue;
            }
            int excess = Math.max(0, com.getExcessQuantity());
            int deficit = Math.max(0, com.getDeficitQuantity());
            int available = com.getAvailable();
            int stockpile = ShelfQuantityEstimator.plannedBaseLimit(com);
            int buyOpen = scaled(ShelfQuantityEstimator.estimateBuy(market, com, false), margin);
            int buyBlack = scaled(ShelfQuantityEstimator.estimateBuy(market, com, true), margin);
            int sellOpen = scaled(ShelfQuantityEstimator.estimateSell(market, com, false), margin);
            int sellBlack = scaled(ShelfQuantityEstimator.estimateSell(market, com, true), margin);
            rows.add(new CommodityTradeInfo(
                    spec.getId(),
                    spec.getName(),
                    spec.getCargoSpace(),
                    spec.getEconUnit(),
                    spec.isFuel(),
                    market.isIllegal(spec.getId()),
                    excess,
                    deficit,
                    available,
                    stockpile,
                    buyOpen,
                    buyBlack,
                    sellOpen,
                    sellBlack
            ));
        }

        return new MarketSnapshot(
                market.getId(),
                market.getName(),
                market.getFactionId(),
                factionName,
                market.getSize(),
                systemName,
                market.isInHyperspace(),
                hasOpen,
                hasBlack,
                openTariff,
                0f,
                hostile,
                hasOpen && transponderOn,
                hasBlack,
                rows
        );
    }

    /**
     * Pick a few surplus/deficit rows for diagnostic UI. Fuel stays in the snapshot
     * (tank, not cargo) for later solvers.
     */
    public static List<CommodityTradeInfo> interestingRows(MarketSnapshot snap, int limit) {
        List<CommodityTradeInfo> ranked = new ArrayList<>(snap.getCommodities());
        ranked.sort((a, b) -> {
            int sa = a.getExcessQty() + a.getDeficitQty();
            int sb = b.getExcessQty() + b.getDeficitQty();
            return Integer.compare(sb, sa);
        });
        if (ranked.size() <= limit) {
            return ranked;
        }
        return new ArrayList<>(ranked.subList(0, limit));
    }

    private static int scaled(int cap, float margin) {
        return (int) Math.floor(Math.max(0, cap) * margin);
    }
}
