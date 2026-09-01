package org.tradeplanner.engine;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.tradeplanner.config.PlannerConfig;
import org.tradeplanner.config.TradeAccess;
import org.tradeplanner.data.CommodityTradeInfo;
import org.tradeplanner.data.MarketSnapshot;
import org.tradeplanner.model.CargoLoad;
import org.tradeplanner.model.TradeAction;
import org.tradeplanner.service.PriceQuoter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bounded knapsack for one directed pair: buy at A, sell at B.
 * When both submarkets are allowed, fills black first then open with leftover cargo/cash.
 * Quantity is limited by source shelf, cargo, cash, and positive batch-quote profit.
 * Fuel stays operational (tank, not cargo). Supplies can be packed as cargo trade goods;
 * execution still will not sell below the reserve floor.
 */
public final class KnapsackSolver {

    private KnapsackSolver() {
    }

    public static CargoLoad solve(MarketSnapshot buyAt, MarketSnapshot sellAt,
                                  float cargoLeft, float cash, PlannerConfig config) {
        boolean blackBuy = canBlack(buyAt, config);
        boolean openBuy = canOpen(buyAt, config);
        boolean blackSell = canBlack(sellAt, config);
        boolean openSell = canOpen(sellAt, config);
        return solve(buyAt, sellAt, cargoLeft, cash,
                blackBuy, openBuy, blackSell, openSell, config);
    }

    /** Single-channel pack used by older call sites and tests. */
    public static CargoLoad solve(MarketSnapshot buyAt, MarketSnapshot sellAt,
                                  float cargoLeft, float cash,
                                  boolean blackBuy, boolean blackSell,
                                  PlannerConfig config) {
        return solve(buyAt, sellAt, cargoLeft, cash,
                blackBuy, !blackBuy, blackSell, !blackSell, config);
    }

    static CargoLoad solve(MarketSnapshot buyAt, MarketSnapshot sellAt,
                           float cargoLeft, float cash,
                           boolean canBlackBuy, boolean canOpenBuy,
                           boolean canBlackSell, boolean canOpenSell,
                           PlannerConfig config) {
        if (buyAt == null || sellAt == null) {
            return CargoLoad.EMPTY;
        }
        if (cargoLeft <= 0.01f || cash <= 0f) {
            return CargoLoad.EMPTY;
        }
        if (buyAt.getMarketId().equals(sellAt.getMarketId())) {
            return CargoLoad.EMPTY;
        }
        MarketAPI buyMarket = buyAt.resolveMarket();
        MarketAPI sellMarket = sellAt.resolveMarket();
        if (buyMarket == null || sellMarket == null) {
            return CargoLoad.EMPTY;
        }

        Map<String, Integer> buyBlackLeft = caps(buyAt, true);
        Map<String, Integer> buyOpenLeft = caps(buyAt, false);

        List<TradeAction> taken = new ArrayList<>();
        float cargo = cargoLeft;
        float money = cash;

        if (canBlackBuy && (canBlackSell || canOpenSell)) {
            boolean sellBlack = canBlackSell;
            FillResult fill = packPass(buyAt, buyMarket, sellMarket, cargo, money, true, sellBlack,
                    buyBlackLeft, config);
            taken.addAll(fill.actions);
            cargo = fill.cargo;
            money = fill.cash;
        }
        if (canOpenBuy && cargo > 0.01f && money > 0f) {
            if (canBlackSell) {
                FillResult fill = packPass(buyAt, buyMarket, sellMarket, cargo, money, false, true,
                        buyOpenLeft, config);
                taken.addAll(fill.actions);
                cargo = fill.cargo;
                money = fill.cash;
            }
            if (canOpenSell && cargo > 0.01f && money > 0f) {
                FillResult fill = packPass(buyAt, buyMarket, sellMarket, cargo, money, false, false,
                        buyOpenLeft, config);
                taken.addAll(fill.actions);
            }
        }
        return CargoLoad.of(taken);
    }

    private static FillResult packPass(MarketSnapshot buyAt, MarketAPI buyMarket, MarketAPI sellMarket,
                                       float cargoLeft, float cash,
                                       boolean blackBuy, boolean blackSell,
                                       Map<String, Integer> buyLeft,
                                       PlannerConfig config) {
        FillResult out = new FillResult();
        out.cargo = cargoLeft;
        out.cash = cash;
        if (buyLeft == null || cargoLeft <= 0.01f || cash <= 0f) {
            return out;
        }

        List<Pack> packs = new ArrayList<>();
        for (CommodityTradeInfo buyRow : buyAt.getCommodities()) {
            if (buyRow.isFuel()) {
                continue;
            }
            if (!blackBuy && buyRow.isIllegalOnOpenMarket()) {
                continue;
            }
            if (!blackSell && sellMarket.isIllegal(buyRow.getId())) {
                continue;
            }
            float space = buyRow.getCargoSpace();
            if (space <= 0.0001f) {
                continue;
            }
            int qtyCap = minPositive(
                    remaining(buyLeft, buyRow.getId()),
                    (int) Math.floor(cargoLeft / space));
            if (qtyCap <= 0) {
                continue;
            }
            int affordable = maxAffordable(buyMarket, buyRow.getId(), qtyCap, cash, blackBuy, config);
            if (affordable <= 0) {
                continue;
            }
            Pack ranked = bestBreakpoint(buyMarket, sellMarket, buyRow, affordable,
                    blackBuy, blackSell, config);
            if (ranked != null && ranked.density > 0f) {
                packs.add(new Pack(ranked.id, ranked.name, affordable, ranked.space,
                        ranked.profit, ranked.density));
            }
        }
        if (packs.isEmpty()) {
            return out;
        }
        packs.sort(Comparator.comparingDouble((Pack p) -> (double) p.density).reversed());

        for (Pack pack : packs) {
            int maxFit = (int) Math.floor(out.cargo / pack.space);
            int cap = minPositive(pack.qty, remaining(buyLeft, pack.id));
            cap = Math.min(cap, maxFit);
            if (cap <= 0) {
                continue;
            }
            int qty = cap;
            if (PriceQuoter.quoteBuy(buyMarket, pack.id, qty, blackBuy, config) > out.cash + 0.01f) {
                qty = maxAffordable(buyMarket, pack.id, cap, out.cash, blackBuy, config);
            }
            if (qty <= 0) {
                continue;
            }
            qty = bestQtyFitting(buyMarket, sellMarket, pack.id, qty, out.cargo, out.cash,
                    pack.space, blackBuy, blackSell, config);
            if (qty <= 0) {
                continue;
            }
            float buy = PriceQuoter.quoteBuy(buyMarket, pack.id, qty, blackBuy, config);
            float sell = PriceQuoter.quoteSell(sellMarket, pack.id, qty, blackSell, config);
            float profit = sell - buy;
            if (profit <= 0f || buy > out.cash + 0.01f) {
                continue;
            }
            out.actions.add(new TradeAction(pack.id, pack.name, qty, pack.space, buy, sell,
                    Boolean.valueOf(blackBuy), Boolean.valueOf(blackSell)));
            out.cargo -= qty * pack.space;
            out.cash -= buy;
            consume(buyLeft, pack.id, qty);
            if (out.cargo <= 0.01f || out.cash <= 0f) {
                break;
            }
        }
        return out;
    }

    private static Map<String, Integer> caps(MarketSnapshot snap, boolean black) {
        Map<String, Integer> map = new HashMap<>();
        if (snap == null) {
            return map;
        }
        for (CommodityTradeInfo row : snap.getCommodities()) {
            int cap = row.getEstimatedBuyMax(black);
            if (cap > 0) {
                map.put(row.getId(), cap);
            }
        }
        return map;
    }

    private static int remaining(Map<String, Integer> caps, String id) {
        if (caps == null || id == null) {
            return 0;
        }
        Integer v = caps.get(id);
        return v == null ? 0 : Math.max(0, v.intValue());
    }

    private static void consume(Map<String, Integer> caps, String id, int qty) {
        if (caps == null || id == null || qty <= 0) {
            return;
        }
        caps.put(id, Math.max(0, remaining(caps, id) - qty));
    }

    public static boolean canBlack(MarketSnapshot snap, PlannerConfig config) {
        return TradeAccess.canBlack(snap, config);
    }

    public static boolean canOpen(MarketSnapshot snap, PlannerConfig config) {
        return TradeAccess.canOpen(snap, config);
    }

    private static Pack bestBreakpoint(MarketAPI buyMarket, MarketAPI sellMarket,
                                       CommodityTradeInfo row, int qtyCap,
                                       boolean blackBuy, boolean blackSell, PlannerConfig config) {
        Pack best = null;
        for (int qty : breakpoints(qtyCap, row.getEconUnit())) {
            if (qty <= 0 || qty > qtyCap) {
                continue;
            }
            float buy = PriceQuoter.quoteBuy(buyMarket, row.getId(), qty, blackBuy, config);
            float sell = PriceQuoter.quoteSell(sellMarket, row.getId(), qty, blackSell, config);
            float profit = sell - buy;
            if (profit <= 0f) {
                continue;
            }
            float used = qty * row.getCargoSpace();
            if (used <= 0.0001f) {
                continue;
            }
            float density = profit / used;
            if (best == null || density > best.density + 0.0001f
                    || (Math.abs(density - best.density) <= 0.0001f && profit > best.profit)) {
                best = new Pack(row.getId(), row.getName(), qty, row.getCargoSpace(), profit, density);
            }
        }
        return best;
    }

    /**
     * Re-evaluate a downscaled quantity so slippage still yields positive profit.
     */
    private static int bestQtyFitting(MarketAPI buyMarket, MarketAPI sellMarket, String id,
                                      int qtyCap, float cargoLeft, float cash, float space,
                                      boolean blackBuy, boolean blackSell, PlannerConfig config) {
        int hi = Math.min(qtyCap, (int) Math.floor(cargoLeft / space));
        hi = maxAffordable(buyMarket, id, hi, cash, blackBuy, config);
        if (hi <= 0) {
            return 0;
        }
        int bestQty = 0;
        float bestProfit = 0f;
        for (int qty : breakpoints(hi, 1f)) {
            if (qty <= 0 || qty > hi) {
                continue;
            }
            float buy = PriceQuoter.quoteBuy(buyMarket, id, qty, blackBuy, config);
            if (buy > cash + 0.01f) {
                continue;
            }
            float sell = PriceQuoter.quoteSell(sellMarket, id, qty, blackSell, config);
            float profit = sell - buy;
            if (profit > bestProfit) {
                bestProfit = profit;
                bestQty = qty;
            }
        }
        if (bestQty <= 0) {
            float buy = PriceQuoter.quoteBuy(buyMarket, id, hi, blackBuy, config);
            float sell = PriceQuoter.quoteSell(sellMarket, id, hi, blackSell, config);
            if (sell - buy > 0f && buy <= cash + 0.01f) {
                return hi;
            }
        }
        return bestQty;
    }

    static int maxAffordable(MarketAPI market, String commodityId, int qtyCap, float cash,
                             boolean blackMarket, PlannerConfig config) {
        if (qtyCap <= 0 || cash <= 0f) {
            return 0;
        }
        if (PriceQuoter.quoteBuy(market, commodityId, 1, blackMarket, config) > cash) {
            return 0;
        }
        if (PriceQuoter.quoteBuy(market, commodityId, qtyCap, blackMarket, config) <= cash) {
            return qtyCap;
        }
        int lo = 1;
        int hi = qtyCap;
        while (lo < hi) {
            int mid = lo + (hi - lo + 1) / 2;
            if (PriceQuoter.quoteBuy(market, commodityId, mid, blackMarket, config) <= cash) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    static int[] breakpoints(int max, float econUnit) {
        if (max <= 0) {
            return new int[0];
        }
        Set<Integer> set = new LinkedHashSet<>();
        int unit = Math.max(1, Math.round(econUnit));
        set.add(max);
        set.add(Math.max(1, max / 2));
        set.add(Math.max(1, max / 4));
        set.add(Math.min(max, unit));
        if (unit * 2 < max) {
            set.add(unit * 2);
        }
        if (max >= 10) {
            set.add(Math.min(max, 10));
        }
        int[] extra = {25, 50, 100, 250, 500, 1000, 2000};
        for (int q : extra) {
            if (q < max) {
                set.add(q);
            }
        }
        List<Integer> list = new ArrayList<>(set);
        Collections.sort(list);
        int[] out = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    private static int minPositive(int a, int b) {
        return Math.max(0, Math.min(a, b));
    }

    private static final class Pack {
        final String id;
        final String name;
        final int qty;
        final float space;
        final float profit;
        final float density;

        Pack(String id, String name, int qty, float space, float profit, float density) {
            this.id = id;
            this.name = name;
            this.qty = qty;
            this.space = space;
            this.profit = profit;
            this.density = density;
        }
    }

    private static final class FillResult {
        final List<TradeAction> actions = new ArrayList<>();
        float cargo;
        float cash;
    }
}
