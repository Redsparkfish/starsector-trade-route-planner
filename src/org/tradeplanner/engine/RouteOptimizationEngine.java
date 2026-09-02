package org.tradeplanner.engine;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import org.apache.log4j.Logger;
import org.tradeplanner.config.PlannerConfig;
import org.tradeplanner.config.TradeAccess;
import org.tradeplanner.data.CommodityTradeInfo;
import org.tradeplanner.data.FleetState;
import org.tradeplanner.data.LogisticsReserve;
import org.tradeplanner.data.MarketSnapshot;
import org.tradeplanner.model.CargoLoad;
import org.tradeplanner.model.RouteLeg;
import org.tradeplanner.model.RoutePlan;
import org.tradeplanner.model.TradeAction;
import org.tradeplanner.service.DistanceCalculator;
import org.tradeplanner.service.DistanceCalculator.TravelEstimate;
import org.tradeplanner.service.PriceQuoter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Time-budgeted multi-stop search maximizing {@code P / (T_loop + α T_pos)}.
 * Positioning is a feasibility constraint and a discounted term in the search score.
 * UI credits/day uses total time {@code P / (T_pos + T_loop)}. Graph construction ranks
 * pairs with a cheap excess/deficit heuristic, then full-knapsacks only the top outgoing
 * edges so a 10s budget is not spent on a complete graph (the old 55% prescan wall).
 */
public final class RouteOptimizationEngine {

    private static final int MAX_GRAPH = 200;
    private static final int MAX_OPEN = 4000;
    /** Full knapsacks per start among candidates (Wave1). Cheap rank picks which dests. */
    private static final int START_FULL_OUT = 48;
    /** Full knapsacks per candidate in Wave2, skipping already-quoted pairs. */
    private static final int WAVE2_FULL_OUT = 16;
    /** Soft cap on Wave1 knapsacks across the whole start pool. */
    private static final int WAVE1_QUOTE_BUDGET = 960;
    private static final int MAX_TWO_STOP_SEEDS = 800;
    private static final Logger log = Global.getLogger(RouteOptimizationEngine.class);

    private final FleetState fleet;
    private final PlannerConfig config;
    private final float posWeight;
    private final List<MarketSnapshot> allMarkets;
    private final Map<String, MarketSnapshot> byId = new HashMap<>();
    private final Map<String, TravelEstimate> travelCache = new HashMap<>();
    private final Set<String> quotedPairs = new HashSet<>();
    private final Map<String, QuotedPack> packCache = new HashMap<>();
    private final long deadlineMs;
    private final long startedMs;

    private Map<String, List<ScoredEdge>> graphEdges = new HashMap<>();
    private float maxEdgeNet;
    private float minEdgeDays = 0.05f;
    private int searchExpanded;
    private int searchPruned;
    private int graphQuotes;

    private RouteOptimizationEngine(FleetState fleet, List<MarketSnapshot> markets, PlannerConfig config) {
        this.fleet = fleet;
        this.config = config;
        this.allMarkets = markets == null ? new ArrayList<MarketSnapshot>() : markets;
        this.posWeight = config == null
                ? PlannerConfig.DEFAULT_POS_TIME_WEIGHT
                : config.getPosTimeWeight();
        this.startedMs = System.currentTimeMillis();
        int budget = config == null ? PlannerConfig.DEFAULT_COMPUTE_BUDGET_MS : config.getComputeBudgetMs();
        this.deadlineMs = this.startedMs + Math.max(50, budget);
    }

    public static RoutePlan optimize(FleetState fleet, List<MarketSnapshot> markets, PlannerConfig config) {
        return new RouteOptimizationEngine(fleet, markets, config).run();
    }

    private RoutePlan run() {
        if (fleet == null || config == null) {
            return RoutePlan.empty(RoutePlan.REASON_NO_MARKETS, false, 0L);
        }
        if (fleet.getCredits() <= 0f) {
            return RoutePlan.empty(RoutePlan.REASON_NO_CASH, false, nowElapsed());
        }
        if (fleet.getCargoLeft() <= 0.01f) {
            return RoutePlan.empty(RoutePlan.REASON_NO_CARGO, false, nowElapsed());
        }

        List<MarketSnapshot> usable = new ArrayList<>();
        for (MarketSnapshot snap : allMarkets) {
            if (!TradeAccess.isUsable(snap, config)) {
                continue;
            }
            if (snap.getPrimaryEntity() == null) {
                continue;
            }
            usable.add(snap);
            byId.put(snap.getMarketId(), snap);
        }
        if (usable.isEmpty()) {
            return RoutePlan.empty(RoutePlan.REASON_NO_MARKETS, false, nowElapsed());
        }
        if (usable.size() < 2) {
            return RoutePlan.empty(RoutePlan.REASON_NO_PROFIT, false, nowElapsed());
        }

        List<MarketSnapshot> startPool = inStartRange(usable);
        if (startPool.isEmpty()) {
            return RoutePlan.empty(RoutePlan.REASON_NO_START_IN_RANGE, false, nowElapsed());
        }

        Map<String, List<ScoredEdge>> scored = new HashMap<>();
        long graphStarted = System.currentTimeMillis();
        List<MarketSnapshot> candidates = buildGraph(usable, startPool, scored);
        long graphMs = System.currentTimeMillis() - graphStarted;
        if (candidates.size() < 2) {
            return RoutePlan.empty(RoutePlan.REASON_NO_PROFIT, false, nowElapsed());
        }
        int edgeCount = countEdges(scored);
        if (timedOut() && edgeCount == 0) {
            return RoutePlan.empty(RoutePlan.REASON_TIMEOUT, true, nowElapsed());
        }
        this.graphEdges = scored;
        refreshBoundStats(scored);

        List<MarketSnapshot> startCandidates = inCandidates(startPool, candidates);
        log.info("TradeRoutePlanner seeds: " + startNames(startCandidates)
                + " candidates=" + candidates.size()
                + " startRangeLY=" + config.getMaxStartRangeLy()
                + " startPool=" + startPool.size()
                + " quotes=" + graphQuotes
                + " edges=" + edgeCount
                + " graphTruncated=" + prescanTimedOut()
                + " alpha=" + posWeight);

        Map<String, SearchState> positioned = new HashMap<>();
        List<SearchState> startStates = new ArrayList<>();
        boolean fuelBlockedAll = true;
        long posStarted = System.currentTimeMillis();
        for (MarketSnapshot start : startCandidates) {
            if (timedOut()) {
                break;
            }
            SearchState at = positionAt(start);
            if (at == null) {
                continue;
            }
            fuelBlockedAll = false;
            positioned.put(start.getMarketId(), at);
            startStates.add(at);
        }
        long posMs = System.currentTimeMillis() - posStarted;
        if (startStates.isEmpty()) {
            if (fuelBlockedAll && !startCandidates.isEmpty()) {
                return RoutePlan.empty(RoutePlan.REASON_NO_FUEL, timedOut(), nowElapsed());
            }
            return RoutePlan.empty(RoutePlan.REASON_NO_PROFIT, timedOut(), nowElapsed());
        }

        SearchState best = null;
        long twoStopMs = 0L;
        if (config.isLoop()) {
            long twoStopStarted = System.currentTimeMillis();
            best = findTwoStopLoops(startCandidates, scored, positioned, best);
            twoStopMs = System.currentTimeMillis() - twoStopStarted;
        }
        long bbStarted = System.currentTimeMillis();
        best = branchAndBound(startStates, scored, best);
        long bbMs = System.currentTimeMillis() - bbStarted;

        long elapsed = nowElapsed();
        boolean truncated = timedOut();
        String phaseMs = " graphMs=" + graphMs
                + " posMs=" + posMs
                + " twoStopMs=" + twoStopMs
                + " bbMs=" + bbMs;
        if (best != null && best.netProfit() > 0f) {
            MarketSnapshot startSnap = byId.get(best.startId);
            String startName = startSnap == null ? best.startId : startSnap.getName();
            log.info("TradeRoutePlanner search: start=" + startName
                    + " loopCpd=" + (int) best.loopCpd()
                    + " score=" + (int) best.score()
                    + " posDays=" + String.format("%.2f", best.posDays)
                    + " loopDays=" + String.format("%.2f", best.loopDays)
                    + " net=" + (int) best.netProfit()
                    + " graph=" + candidates.size()
                    + " quotes=" + graphQuotes
                    + " startOut=" + Math.min(START_FULL_OUT,
                    Math.max(8, WAVE1_QUOTE_BUDGET / Math.max(1, startPool.size())))
                    + " edges=" + edgeCount
                    + " expanded=" + searchExpanded
                    + " pruned=" + searchPruned
                    + " starts=" + startStates.size()
                    + " alpha=" + posWeight
                    + " truncated=" + truncated
                    + phaseMs
                    + " ms=" + elapsed);
            return RoutePlan.of(best.legs, config.isLoop(), truncated, elapsed, best.extraStops);
        }
        log.info("TradeRoutePlanner search miss: loop=" + config.isLoop()
                + " graph=" + candidates.size()
                + " quotes=" + graphQuotes
                + " edges=" + edgeCount
                + " expanded=" + searchExpanded
                + " pruned=" + searchPruned
                + " starts=" + startStates.size()
                + " alpha=" + posWeight
                + " truncated=" + truncated
                + " bestNet=" + (best == null ? "none" : String.format("%.0f", best.netProfit()))
                + " cargo=" + (int) fleet.getCargoLeft()
                + " fuel=" + (int) fleet.getFuel()
                + phaseMs
                + " ms=" + elapsed);
        if (truncated) {
            return RoutePlan.empty(RoutePlan.REASON_TIMEOUT, true, elapsed);
        }
        return RoutePlan.empty(RoutePlan.REASON_NO_PROFIT, false, elapsed);
    }

    private List<MarketSnapshot> buildGraph(List<MarketSnapshot> usable, List<MarketSnapshot> startPool,
                                            Map<String, List<ScoredEdge>> scored) {
        float cargo = LogisticsReserve.tradeCargoLeft(fleet, config);
        float cash = fleet.getCredits();
        Map<String, Float> destBestCheap = new HashMap<>();
        for (MarketSnapshot start : startPool) {
            for (MarketSnapshot dest : usable) {
                if (start.getMarketId().equals(dest.getMarketId())) {
                    continue;
                }
                float cpd = cheapCpd(start, dest);
                if (cpd <= 0f) {
                    continue;
                }
                Float prev = destBestCheap.get(dest.getMarketId());
                if (prev == null || cpd > prev) {
                    destBestCheap.put(dest.getMarketId(), cpd);
                }
            }
        }

        List<MarketSnapshot> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (MarketSnapshot start : startPool) {
            if (seen.add(start.getMarketId())) {
                candidates.add(start);
            }
        }
        List<MarketSnapshot> ranked = new ArrayList<>();
        for (MarketSnapshot snap : usable) {
            if (!seen.contains(snap.getMarketId())) {
                ranked.add(snap);
            }
        }
        ranked.sort(Comparator.comparingDouble((MarketSnapshot m) -> {
            Float cpd = destBestCheap.get(m.getMarketId());
            return cpd == null ? 0d : (double) cpd;
        }).reversed());
        for (MarketSnapshot snap : ranked) {
            if (candidates.size() >= MAX_GRAPH) {
                break;
            }
            if (seen.add(snap.getMarketId())) {
                candidates.add(snap);
            }
        }

        int startOut = Math.min(START_FULL_OUT,
                Math.max(8, WAVE1_QUOTE_BUDGET / Math.max(1, startPool.size())));
        for (MarketSnapshot start : startPool) {
            if (prescanTimedOut()) {
                break;
            }
            quoteTopOutgoing(start, candidates, scored, cargo, cash, startOut);
        }
        for (MarketSnapshot from : candidates) {
            if (prescanTimedOut()) {
                break;
            }
            quoteTopOutgoing(from, candidates, scored, cargo, cash, WAVE2_FULL_OUT);
        }
        for (List<ScoredEdge> edges : scored.values()) {
            edges.sort(Comparator.comparingDouble((ScoredEdge e) -> (double) e.score).reversed());
        }
        return candidates;
    }

    private void quoteTopOutgoing(MarketSnapshot from, List<MarketSnapshot> pool,
                                  Map<String, List<ScoredEdge>> scored,
                                  float cargo, float cash, int limit) {
        if (from == null || pool == null || limit <= 0) {
            return;
        }
        List<MarketSnapshot> ranked = rankByCheap(from, pool);
        int quoted = 0;
        for (MarketSnapshot dest : ranked) {
            if (prescanTimedOut() || quoted >= limit) {
                break;
            }
            if (quotedPairs.contains(pairKey(from, dest))) {
                continue;
            }
            ScoredEdge edge = quoteEdge(from, dest, cargo, cash);
            quoted++;
            if (edge != null) {
                putEdge(scored, from.getMarketId(), edge);
            }
        }
    }

    private List<MarketSnapshot> rankByCheap(MarketSnapshot from, List<MarketSnapshot> pool) {
        List<MarketSnapshot> ranked = new ArrayList<>();
        for (MarketSnapshot snap : pool) {
            if (snap != null && !snap.getMarketId().equals(from.getMarketId())) {
                ranked.add(snap);
            }
        }
        ranked.sort(Comparator.comparingDouble((MarketSnapshot m) -> (double) cheapCpd(from, m)).reversed());
        return ranked;
    }

    /**
     * Snapshot-only rank: surplus at source x shortage at dest, scaled by travel days.
     * Used to pick which directed pairs get a full knapsack, not as a profit estimate.
     */
    private float cheapCpd(MarketSnapshot from, MarketSnapshot to) {
        float hint = cheapHint(from, to);
        if (hint <= 0f) {
            return 0f;
        }
        float days = Math.max(0.05f, between(from, to).getTotalDays());
        return hint / days;
    }

    private float cheapHint(MarketSnapshot from, MarketSnapshot to) {
        if (from == null || to == null || from.getMarketId().equals(to.getMarketId())) {
            return 0f;
        }
        boolean destBlack = KnapsackSolver.canBlack(to, config);
        boolean destOpen = KnapsackSolver.canOpen(to, config);
        if (!destBlack && !destOpen) {
            return 0f;
        }
        float hint = 0f;
        for (CommodityTradeInfo buy : from.getCommodities()) {
            int cap = buy.getEstimatedBuyMax();
            if (cap <= 0) {
                continue;
            }
            CommodityTradeInfo sell = to.getCommodity(buy.getId());
            boolean destIllegalOpen = sell != null && sell.isIllegalOnOpenMarket();
            if (!destBlack && destIllegalOpen) {
                continue;
            }
            float space = buy.getCargoSpace();
            if (buy.isFuel()) {
                space = 1f;
            } else if (space <= 0.0001f) {
                continue;
            }
            int excess = Math.max(0, buy.getExcessQty());
            int deficit = sell == null ? 0 : Math.max(0, sell.getDeficitQty());
            float weight = cap;
            if (excess > 0 && deficit > 0) {
                weight *= 4f;
            } else if (excess > 0 || deficit > 0) {
                weight *= 2f;
            }
            hint += weight / space;
        }
        return hint;
    }

    private SearchState considerCompletions(SearchState state, SearchState best) {
        if (state.extraStops < 1) {
            return best;
        }
        if (!config.isLoop()) {
            return better(best, state);
        }
        SearchState closed = tryTradeHop(state, state.startId, true);
        if (closed != null) {
            return better(best, closed);
        }
        return best;
    }

    /**
     * Enumerate A→B→A using prescan profitable edges, highest estimated R_α first.
     * Return hop may be empty cargo. Does not fly back to the fleet origin.
     */
    private SearchState findTwoStopLoops(List<MarketSnapshot> startPool,
                                         Map<String, List<ScoredEdge>> scored,
                                         Map<String, SearchState> positioned,
                                         SearchState best) {
        List<LoopSeed> seeds = new ArrayList<>();
        for (MarketSnapshot start : startPool) {
            List<ScoredEdge> outs = scored.get(start.getMarketId());
            if (outs == null) {
                continue;
            }
            float posDays = Math.max(0f, fleetTo(start).getTotalDays());
            for (ScoredEdge e : outs) {
                MarketSnapshot dest = byId.get(e.toId);
                if (dest == null) {
                    continue;
                }
                float backDays = Math.max(0.05f, between(dest, start).getTotalDays());
                float backNet = edgeNet(scored, e.toId, start.getMarketId());
                float loopDays = Math.max(0.05f, e.days + backDays);
                float profit = e.net + Math.max(0f, backNet);
                float est = profit / Math.max(0.05f, loopDays + posWeight * posDays);
                seeds.add(new LoopSeed(start, e.toId, est));
            }
        }
        seeds.sort(Comparator.comparingDouble((LoopSeed s) -> (double) s.est).reversed());
        int cap = Math.min(seeds.size(), MAX_TWO_STOP_SEEDS);
        for (int i = 0; i < cap; i++) {
            if (timedOut()) {
                break;
            }
            LoopSeed seed = seeds.get(i);
            SearchState at = positioned.get(seed.start.getMarketId());
            if (at == null) {
                at = positionAt(seed.start);
                if (at == null) {
                    continue;
                }
                positioned.put(seed.start.getMarketId(), at);
            }
            SearchState outbound = tryTradeHop(at, seed.destId, false);
            if (outbound == null) {
                continue;
            }
            SearchState closed = tryTradeHop(outbound, seed.start.getMarketId(), true);
            if (closed != null) {
                best = better(best, closed);
            } else if (outbound.netProfit() > 0f && log.isDebugEnabled()) {
                log.debug("TradeRoutePlanner loop reject: " + seed.start.getName()
                        + " -> " + seed.destId + " oneWayNet=" + (int) outbound.netProfit());
            }
        }
        return best;
    }

    private SearchState branchAndBound(List<SearchState> startStates,
                                       Map<String, List<ScoredEdge>> scored,
                                       SearchState best) {
        PriorityQueue<SearchState> open = new PriorityQueue<>(SearchState.BY_BOUND_DESC);
        for (SearchState start : startStates) {
            start.bound = computeBound(start);
            open.add(start);
        }
        int maxStops = config.getMaxStops();
        while (!open.isEmpty() && !timedOut()) {
            SearchState state = open.poll();
            if (state == null) {
                break;
            }
            if (shouldPrune(state, best)) {
                searchPruned++;
                continue;
            }
            searchExpanded++;
            best = considerCompletions(state, best);
            if (state.extraStops >= maxStops) {
                continue;
            }
            List<ScoredEdge> outs = scored.get(state.currentId);
            if (outs == null || outs.isEmpty()) {
                continue;
            }
            for (ScoredEdge edge : outs) {
                if (timedOut()) {
                    break;
                }
                if (state.visited.contains(edge.toId)) {
                    continue;
                }
                SearchState expanded = tryTradeHop(state, edge.toId, false);
                if (expanded == null) {
                    continue;
                }
                expanded.bound = computeBound(expanded);
                if (shouldPrune(expanded, best)) {
                    searchPruned++;
                    continue;
                }
                open.add(expanded);
            }
            trimOpen(open);
        }
        return best;
    }

    private void trimOpen(PriorityQueue<SearchState> open) {
        if (open.size() <= MAX_OPEN) {
            return;
        }
        List<SearchState> kept = new ArrayList<>(open);
        open.clear();
        kept.sort(SearchState.BY_BOUND_DESC);
        int n = Math.min(MAX_OPEN, kept.size());
        searchPruned += kept.size() - n;
        for (int i = 0; i < n; i++) {
            open.add(kept.get(i));
        }
    }

    private boolean shouldPrune(SearchState state, SearchState best) {
        if (best == null || state == null) {
            return false;
        }
        return state.bound + 0.01f < best.score();
    }

    private float computeBound(SearchState s) {
        if (s == null) {
            return 0f;
        }
        int remainingStops = Math.max(0, config.getMaxStops() - s.extraStops);
        float tLeft = Math.max(0f, config.getMaxDays() - s.totalDays());
        float hatT = Math.max(minEdgeDays, 0.05f);
        int kTime = (int) Math.floor(tLeft / hatT);
        int k = Math.min(remainingStops, Math.max(0, kTime));
        float localBestNet = 0f;
        float localMinDays = Float.MAX_VALUE;
        int localOut = 0;
        List<ScoredEdge> outs = graphEdges == null ? null : graphEdges.get(s.currentId);
        if (outs != null) {
            for (ScoredEdge e : outs) {
                if (s.visited.contains(e.toId)) {
                    continue;
                }
                localOut++;
                if (e.net > localBestNet) {
                    localBestNet = e.net;
                }
                if (e.days < localMinDays) {
                    localMinDays = e.days;
                }
            }
        }
        if (localMinDays > 1.0e8f) {
            localMinDays = hatT;
        }
        float extraP = 0f;
        float remainMin = 0f;
        if (k > 0 && localOut > 0) {
            extraP = localBestNet + Math.max(0, k - 1) * Math.max(0f, maxEdgeNet);
            remainMin = Math.max(localMinDays, 0.05f) + Math.max(0, k - 1) * hatT;
        }
        if (config.isLoop() && s.currentId != null && !s.currentId.equals(s.startId) && k <= 0) {
            remainMin = Math.max(remainMin, closeDays(s));
        }
        float denom = s.loopDays + remainMin + s.posWeight * s.posDays;
        return (s.netProfit() + extraP) / Math.max(denom, 0.05f);
    }

    private float closeDays(SearchState s) {
        MarketSnapshot cur = byId.get(s.currentId);
        MarketSnapshot start = byId.get(s.startId);
        if (cur == null || start == null) {
            return 0.05f;
        }
        return Math.max(0.05f, between(cur, start).getTotalDays());
    }

    private static SearchState better(SearchState current, SearchState candidate) {
        if (candidate == null || candidate.netProfit() <= 0f) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        float a = candidate.score();
        float b = current.score();
        float band = Math.max(0.01f, 0.0001f * Math.max(Math.abs(a), Math.abs(b)));
        if (Math.abs(a - b) > band) {
            return a > b ? candidate : current;
        }
        if (candidate.netProfit() > current.netProfit()) {
            return candidate;
        }
        return current;
    }

    private SearchState positionAt(MarketSnapshot start) {
        SectorEntityToken entity = start.getPrimaryEntity();
        if (entity == null) {
            return null;
        }
        TravelEstimate est = fleetTo(start);
        if (est.getTotalDays() > config.getMaxDays() + 0.0001f) {
            return null;
        }
        if (est.getHyperspaceFuel() > fleet.getFuel() + 0.05f) {
            return null;
        }
        SearchState state = new SearchState();
        state.startId = start.getMarketId();
        state.currentId = start.getMarketId();
        state.visited.add(start.getMarketId());
        state.cash = fleet.getCredits();
        float consumed = Math.min(est.getSupplyUnits(), fleet.getSupplies());
        state.supplies = fleet.getSupplies() - consumed;
        state.cargoFree = fleet.getCargoLeft() + consumed * fleet.getSupplyCargoSpace();
        state.fuel = fleet.getFuel() - est.getHyperspaceFuel();
        state.posDays = est.getTotalDays();
        state.loopDays = 0f;
        state.extraStops = 0;
        state.posWeight = posWeight;
        // boolean quoteBlack = useBlack(start, config);
        // 旅行油/补给成本暂不计（调试）：原先 opsCostBreakdown + 计入 state.ops
        // float[] ops = opsCostBreakdown(start, quoteBlack, est);
        float[] ops = new float[] {0f, 0f};
        state.gross = 0f;
        // state.ops = ops[0] + ops[1];
        state.ops = 0f;
        // Always keep the start market as stop 0 so the job sheet shows +买 there,
        // even when the fleet is already docked (0-day hop).
        state.legs.add(new RouteLeg(
                RouteLeg.FLEET_ORIGIN_ID, "舰队",
                start.getMarketId(), start.getName(),
                new ArrayList<TradeAction>(),
                est.getHyperspaceLY(), est.getHyperspaceDays(), est.getInSystemDays(), est.getTotalDays(),
                est.getHyperspaceFuel(), est.getSupplyUnits(),
                ops[0], ops[1],
                0f, 0f,
                0f, 0f,
                state.cash, 0f, state.fuel));
        return state;
    }

    private SearchState tryTradeHop(SearchState from, String destId, boolean returning) {
        if (destId == null || destId.equals(from.currentId)) {
            return null;
        }
        if (!returning && from.visited.contains(destId)) {
            return null;
        }
        if (returning && !destId.equals(from.startId)) {
            return null;
        }
        MarketSnapshot origin = byId.get(from.currentId);
        MarketSnapshot dest = byId.get(destId);
        if (origin == null || dest == null) {
            return null;
        }
        TravelEstimate est = between(origin, dest);
        if (from.totalDays() + est.getTotalDays() > config.getMaxDays() + 0.0001f) {
            return null;
        }
        Refuel refuel = refuelForHop(origin, from.fuel, est.getHyperspaceFuel(), from.cash);
        float fuelReady = from.fuel + refuel.qty;
        if (fuelReady + 0.05f < est.getHyperspaceFuel()) {
            return null;
        }
        float cashAfterFuel = from.cash - refuel.cost;
        if (cashAfterFuel < 0f) {
            return null;
        }
        Restock supply = restockSupplies(origin, from.supplies, from.cargoFree, cashAfterFuel);
        float cashAfterOps = cashAfterFuel - supply.cost;
        if (cashAfterOps < 0f) {
            return null;
        }
        float space = fleet.getSupplyCargoSpace();
        float cargoAfterRestock = Math.max(0f, from.cargoFree - supply.qty * space);
        float fuelRoom = Math.max(0f, fleet.getFuelMax() - fuelReady);
        CargoLoad load = packForHop(origin, dest, cargoAfterRestock, cashAfterOps, fuelRoom);
        if (load.isEmpty() || load.getGrossProfit() <= 0f) {
            if (!returning) {
                return null;
            }
            // Loop close: sell at B then fly empty back to A. Return cargo is a bonus, not required.
            load = CargoLoad.EMPTY;
        }
        if (load.getBuyCost() > cashAfterOps + 0.01f) {
            return null;
        }
        // 旅行油/补给成本暂不计（调试）
        // float[] ops = opsCostBreakdown(origin, useBlack(origin, config), est);
        float[] ops = new float[] {0f, 0f};
        float cashAfter = cashAfterOps - load.getBuyCost() + load.getSellRevenue();
        float fuelAfter = fuelReady - est.getHyperspaceFuel();
        float suppliesAfterBuy = from.supplies + supply.qty;
        float burned = Math.min(est.getSupplyUnits(), suppliesAfterBuy);
        float suppliesAfter = suppliesAfterBuy - burned;

        SearchState next = from.copy();
        next.currentId = destId;
        if (!returning) {
            next.visited.add(destId);
            next.extraStops = from.extraStops + 1;
        }
        next.cash = cashAfter;
        next.fuel = fuelAfter;
        next.supplies = Math.max(0f, suppliesAfter);
        next.cargoFree = cargoAfterRestock + burned * space;
        next.posDays = from.posDays;
        next.loopDays = from.loopDays + est.getTotalDays();
        next.gross = from.gross + load.getGrossProfit();
        // next.ops = from.ops + ops[0] + ops[1];
        next.ops = from.ops;
        next.legs.add(new RouteLeg(
                origin.getMarketId(), origin.getName(),
                dest.getMarketId(), dest.getName(),
                load.getItems(),
                est.getHyperspaceLY(), est.getHyperspaceDays(), est.getInSystemDays(), est.getTotalDays(),
                est.getHyperspaceFuel(), est.getSupplyUnits(),
                ops[0], ops[1],
                refuel.qty, refuel.cost,
                supply.qty, supply.cost,
                cashAfter, load.getCargoUsed(), fuelAfter));
        return next;
    }

    private ScoredEdge quoteEdge(MarketSnapshot from, MarketSnapshot to, float cargo, float cash) {
        if (from == null || to == null || from.getMarketId().equals(to.getMarketId())) {
            return null;
        }
        quotedPairs.add(pairKey(from, to));
        graphQuotes++;
        CargoLoad load = KnapsackSolver.solve(from, to, cargo, cash,
                LogisticsReserve.tradeFuelRoom(config, fleet), config);
        if (load.isEmpty() || load.getGrossProfit() <= 0f) {
            return null;
        }
        packCache.put(pairKey(from, to), new QuotedPack(load));
        TravelEstimate est = between(from, to);
        float days = Math.max(0.05f, est.getTotalDays());
        float net = load.getGrossProfit();
        return new ScoredEdge(to.getMarketId(), net, days, net / days);
    }

    /**
     * Reuse a graph pack when the hop still has enough cargo and cash to buy it.
     * Richer leftover can keep the cached list (feasible, maybe a bit short of a re-solve).
     */
    private CargoLoad packForHop(MarketSnapshot from, MarketSnapshot to, float cargo, float cash,
                                 float fuelRoom) {
        String key = pairKey(from, to);
        QuotedPack cached = packCache.get(key);
        if (cached != null && cached.load.getCargoUsed() <= cargo + 0.01f
                && cached.load.getBuyCost() <= cash + 0.01f
                && cached.load.getFuelQty() <= fuelRoom + 0.01f) {
            return cached.load;
        }
        CargoLoad load = KnapsackSolver.solve(from, to, cargo, cash, fuelRoom, config);
        if (!load.isEmpty() && load.getGrossProfit() > 0f) {
            packCache.put(key, new QuotedPack(load));
        }
        return load;
    }

    private static String pairKey(MarketSnapshot from, MarketSnapshot to) {
        return from.getMarketId() + ">" + to.getMarketId();
    }

    private static void putEdge(Map<String, List<ScoredEdge>> scored, String fromId, ScoredEdge edge) {
        List<ScoredEdge> list = scored.get(fromId);
        if (list == null) {
            list = new ArrayList<>();
            scored.put(fromId, list);
        }
        list.add(edge);
    }

    private static int countEdges(Map<String, List<ScoredEdge>> scored) {
        int n = 0;
        for (List<ScoredEdge> edges : scored.values()) {
            n += edges.size();
        }
        return n;
    }

    private void refreshBoundStats(Map<String, List<ScoredEdge>> scored) {
        maxEdgeNet = 0f;
        minEdgeDays = Float.MAX_VALUE;
        for (List<ScoredEdge> edges : scored.values()) {
            for (ScoredEdge e : edges) {
                if (e.net > maxEdgeNet) {
                    maxEdgeNet = e.net;
                }
                if (e.days < minEdgeDays) {
                    minEdgeDays = e.days;
                }
            }
        }
        if (minEdgeDays > 1.0e8f) {
            minEdgeDays = 0.05f;
        }
    }

    private List<MarketSnapshot> inStartRange(List<MarketSnapshot> usable) {
        List<MarketSnapshot> out = new ArrayList<>();
        for (MarketSnapshot snap : usable) {
            if (withinStartRange(snap)) {
                out.add(snap);
            }
        }
        return out;
    }

    private boolean withinStartRange(MarketSnapshot snap) {
        if (snap == null) {
            return false;
        }
        return fleetTo(snap).getHyperspaceLY() <= config.getMaxStartRangeLy() + 0.001f;
    }

    private static List<MarketSnapshot> inCandidates(List<MarketSnapshot> pool,
                                                     List<MarketSnapshot> candidates) {
        List<MarketSnapshot> out = new ArrayList<>();
        for (MarketSnapshot snap : pool) {
            if (candidatesContain(candidates, snap)) {
                out.add(snap);
            }
        }
        return out;
    }

    private static float edgeNet(Map<String, List<ScoredEdge>> scored, String from, String to) {
        List<ScoredEdge> edges = scored.get(from);
        if (edges == null) {
            return 0f;
        }
        for (ScoredEdge e : edges) {
            if (to.equals(e.toId)) {
                return e.net;
            }
        }
        return 0f;
    }

    private static String startNames(List<MarketSnapshot> starts) {
        StringBuilder sb = new StringBuilder();
        for (MarketSnapshot s : starts) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(s.getName());
        }
        return sb.toString();
    }

    private static boolean candidatesContain(List<MarketSnapshot> candidates, MarketSnapshot start) {
        for (MarketSnapshot snap : candidates) {
            if (snap.getMarketId().equals(start.getMarketId())) {
                return true;
            }
        }
        return false;
    }

    private Refuel refuelForHop(MarketSnapshot at, float currentFuel,
                                float need, float cash) {
        float tankMax = fleet.getFuelMax();
        float floor = LogisticsReserve.fuelFloor(config, fleet);
        float targetAfter = Math.min(floor, Math.max(0f, tankMax - need));
        float targetReady = Math.min(tankMax, need + targetAfter);
        if (currentFuel + 0.05f >= need && currentFuel + 0.05f >= targetReady) {
            return Refuel.NONE;
        }
        float tankRoom = Math.max(0f, tankMax - currentFuel);
        float want = Math.min(tankRoom, Math.max(0f, targetReady - currentFuel));
        CommodityTradeInfo fuelRow = at.getCommodity(Commodities.FUEL);
        MarketAPI market = at.resolveMarket();
        if (market == null || want <= 0.05f) {
            return Refuel.NONE;
        }
        float qty = 0f;
        float cost = 0f;
        float remaining = want;
        float cashLeft = cash;
        if (KnapsackSolver.canBlack(at, config)) {
            float[] got = buyOps(market, fuelRow, Commodities.FUEL, remaining, cashLeft, true);
            qty += got[0];
            cost += got[1];
            remaining -= got[0];
            cashLeft -= got[1];
        }
        if (remaining > 0.05f && KnapsackSolver.canOpen(at, config)) {
            float[] got = buyOps(market, fuelRow, Commodities.FUEL, remaining, cashLeft, false);
            qty += got[0];
            cost += got[1];
        }
        if (qty <= 0f) {
            return Refuel.NONE;
        }
        return new Refuel(qty, cost);
    }

    private Restock restockSupplies(MarketSnapshot at, float currentSupplies,
                                    float cargoFree, float cash) {
        int floor = LogisticsReserve.supplyFloor(config, fleet);
        float want = floor - currentSupplies;
        if (want <= 0.5f || cargoFree <= 0.01f || cash <= 0f) {
            return Restock.NONE;
        }
        float space = fleet.getSupplyCargoSpace();
        int fit = space <= 0.0001f ? (int) Math.floor(want) : (int) Math.floor(cargoFree / space);
        CommodityTradeInfo row = at.getCommodity(Commodities.SUPPLIES);
        MarketAPI market = at.resolveMarket();
        if (market == null) {
            return Restock.NONE;
        }
        float remaining = Math.min(want, fit);
        float cashLeft = cash;
        float qty = 0f;
        float cost = 0f;
        if (KnapsackSolver.canBlack(at, config)) {
            float[] got = buyOps(market, row, Commodities.SUPPLIES, remaining, cashLeft, true);
            qty += got[0];
            cost += got[1];
            remaining -= got[0];
            cashLeft -= got[1];
        }
        if (remaining > 0.5f && KnapsackSolver.canOpen(at, config)) {
            float[] got = buyOps(market, row, Commodities.SUPPLIES, remaining, cashLeft, false);
            qty += got[0];
            cost += got[1];
        }
        if (qty <= 0f) {
            return Restock.NONE;
        }
        return new Restock(qty, cost);
    }

    private float[] buyOps(MarketAPI market, CommodityTradeInfo row, String id,
                           float want, float cash, boolean black) {
        int buyMax = row == null ? 0 : row.getEstimatedBuyMax(black);
        int qty = (int) Math.floor(Math.min(want, buyMax));
        if (qty <= 0 || cash <= 0f) {
            return new float[] {0f, 0f};
        }
        qty = KnapsackSolver.maxAffordable(market, id, qty, cash, black, config);
        if (qty <= 0) {
            return new float[] {0f, 0f};
        }
        float cost = PriceQuoter.quoteBuy(market, id, qty, black, config);
        return new float[] {qty, cost};
    }

    /**
     * Credit cost of burned hyperspace fuel and consumed supplies.
     * Temporarily disabled: return zeros so search maximizes gross trade profit only.
     */
    @SuppressWarnings("unused")
    private float[] opsCostBreakdown(MarketSnapshot quoteAt, boolean black, TravelEstimate est) {
        // 旅行油/补给成本暂不计（调试）
        return new float[] {0f, 0f};
        /*
        MarketAPI market = quoteAt.resolveMarket();
        float fuelCost = 0f;
        float supplyCost = 0f;
        if (market != null) {
            // Opportunity cost of fuel/supplies already in the tanks: unit price × qty.
            // Do not batch-quote the whole burn — slippage on 200+ fuel would dwarf a real hold of goods.
            if (est.getHyperspaceFuel() > 0.01f) {
                fuelCost = PriceQuoter.quoteBuy(market, Commodities.FUEL, 1d, black, config)
                        * est.getHyperspaceFuel();
            }
            if (est.getSupplyUnits() > 0.01f) {
                supplyCost = PriceQuoter.quoteBuy(market, Commodities.SUPPLIES, 1d, black, config)
                        * est.getSupplyUnits();
            }
        }
        return new float[] {fuelCost, supplyCost};
        */
    }

    private TravelEstimate fleetTo(MarketSnapshot dest) {
        String key = RouteLeg.FLEET_ORIGIN_ID + ">" + dest.getMarketId();
        TravelEstimate cached = travelCache.get(key);
        if (cached != null) {
            return cached;
        }
        TravelEstimate est = DistanceCalculator.estimateTo(fleet, dest.getPrimaryEntity());
        travelCache.put(key, est);
        return est;
    }

    private TravelEstimate between(MarketSnapshot from, MarketSnapshot to) {
        String key = from.getMarketId() + ">" + to.getMarketId();
        TravelEstimate cached = travelCache.get(key);
        if (cached != null) {
            return cached;
        }
        TravelEstimate est = DistanceCalculator.estimateBetween(from, to, fleet);
        travelCache.put(key, est);
        return est;
    }

    private boolean timedOut() {
        return System.currentTimeMillis() >= deadlineMs;
    }

    /** Leave roughly half the wall-clock budget for branch-and-bound after graph construction. */
    private boolean prescanTimedOut() {
        long cap = startedMs + Math.max(50L, (deadlineMs - startedMs) * 55L / 100L);
        return System.currentTimeMillis() >= cap;
    }

    private long nowElapsed() {
        return System.currentTimeMillis() - startedMs;
    }

    private static final class Refuel {
        static final Refuel NONE = new Refuel(0f, 0f);
        final float qty;
        final float cost;

        Refuel(float qty, float cost) {
            this.qty = qty;
            this.cost = cost;
        }
    }

    private static final class QuotedPack {
        final CargoLoad load;

        QuotedPack(CargoLoad load) {
            this.load = load;
        }
    }

    private static final class ScoredEdge {
        final String toId;
        final float net;
        final float days;
        final float score;

        ScoredEdge(String toId, float net, float days, float score) {
            this.toId = toId;
            this.net = net;
            this.days = days;
            this.score = score;
        }
    }

    private static final class LoopSeed {
        final MarketSnapshot start;
        final String destId;
        final float est;

        LoopSeed(MarketSnapshot start, String destId, float est) {
            this.start = start;
            this.destId = destId;
            this.est = est;
        }
    }

    private static final class Restock {
        static final Restock NONE = new Restock(0f, 0f);
        final float qty;
        final float cost;

        Restock(float qty, float cost) {
            this.qty = qty;
            this.cost = cost;
        }
    }

    private static final class SearchState {
        static final Comparator<SearchState> BY_BOUND_DESC =
                Comparator.comparingDouble((SearchState s) -> (double) s.bound).reversed();

        String startId;
        String currentId;
        final Set<String> visited = new HashSet<>();
        float cash;
        float fuel;
        float supplies;
        float cargoFree;
        float posDays;
        float loopDays;
        float gross;
        float ops;
        float bound;
        float posWeight;
        int extraStops;
        final List<RouteLeg> legs = new ArrayList<>();

        SearchState copy() {
            SearchState s = new SearchState();
            s.startId = startId;
            s.currentId = currentId;
            s.visited.addAll(visited);
            s.cash = cash;
            s.fuel = fuel;
            s.supplies = supplies;
            s.cargoFree = cargoFree;
            s.posDays = posDays;
            s.loopDays = loopDays;
            s.gross = gross;
            s.ops = ops;
            s.posWeight = posWeight;
            s.extraStops = extraStops;
            s.legs.addAll(legs);
            return s;
        }

        float totalDays() {
            return posDays + loopDays;
        }

        float netProfit() {
            // 旅行油/补给成本暂不计（调试）
            // return gross - ops;
            return gross;
        }

        float loopCpd() {
            return netProfit() / Math.max(loopDays, 0.05f);
        }

        float score() {
            return netProfit() / Math.max(loopDays + posWeight * posDays, 0.05f);
        }
    }
}
