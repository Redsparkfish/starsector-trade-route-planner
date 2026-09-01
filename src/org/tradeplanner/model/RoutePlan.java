package org.tradeplanner.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Multi-stop tour at calculate-time snapshot. Numbers are estimates and go stale after the
 * player actually trades (local available units change).
 */
public final class RoutePlan {

    public static final String REASON_NO_MARKETS = "NO_MARKETS";
    public static final String REASON_NO_CASH = "NO_CASH";
    public static final String REASON_NO_CARGO = "NO_CARGO";
    public static final String REASON_NO_FUEL = "NO_FUEL";
    public static final String REASON_NO_START_IN_RANGE = "NO_START_IN_RANGE";
    public static final String REASON_NO_PROFIT = "NO_PROFIT";
    public static final String REASON_TIMEOUT = "TIMEOUT";

    private List<RouteLeg> legs;
    private boolean loop;
    private boolean truncated;
    private String emptyReason;
    private float totalDays;
    /** Fleet → first market only. Included in {@link #creditsPerDay} with the loop. */
    private float positioningDays;
    private float grossProfit;
    private float opsCost;
    private float netProfit;
    private float creditsPerDay;
    private long computeMs;
    /** Wall time of {@code refreshSnapshot} before search. 0 on old saves. */
    private long snapshotMs;
    private int extraStops;

    /** XStream / campaign save. */
    @SuppressWarnings("unused")
    private RoutePlan() {
        this.legs = new ArrayList<>();
    }

    private RoutePlan(List<RouteLeg> legs, boolean loop, boolean truncated, String emptyReason,
                      float totalDays, float positioningDays, float grossProfit, float opsCost, float netProfit,
                      float creditsPerDay, long computeMs, int extraStops) {
        this.legs = new ArrayList<>(legs == null ? Collections.emptyList() : legs);
        this.loop = loop;
        this.truncated = truncated;
        this.emptyReason = emptyReason;
        this.totalDays = totalDays;
        this.positioningDays = positioningDays;
        this.grossProfit = grossProfit;
        this.opsCost = opsCost;
        this.netProfit = netProfit;
        this.creditsPerDay = creditsPerDay;
        this.computeMs = computeMs;
        this.snapshotMs = 0L;
        this.extraStops = extraStops;
    }

    public static RoutePlan empty(String reason, boolean truncated, long computeMs) {
        return new RoutePlan(Collections.emptyList(), false, truncated, reason,
                0f, 0f, 0f, 0f, 0f, 0f, computeMs, 0);
    }

    public static RoutePlan of(List<RouteLeg> legs, boolean loop, boolean truncated,
                               long computeMs, int extraStops) {
        float posDays = 0f;
        float loopDays = 0f;
        float gross = 0f;
        float ops = 0f;
        if (legs != null) {
            for (RouteLeg leg : legs) {
                if (leg.isPositioning()) {
                    posDays += leg.getTotalDays();
                } else {
                    loopDays += leg.getTotalDays();
                }
                gross += leg.getGrossProfit();
                // 旅行油/补给成本暂不计（调试）
                // ops += leg.getOpsCost();
            }
        }
        // float net = gross - ops;
        float net = gross;
        float total = posDays + loopDays;
        float cpd = total > 0.0001f ? net / Math.max(total, 0.05f) : 0f;
        return new RoutePlan(legs, loop, truncated, null, total, posDays,
                gross, ops, net, cpd, computeMs, extraStops);
    }

    public boolean isEmpty() {
        return legs == null || legs.isEmpty();
    }

    public List<RouteLeg> getLegs() {
        if (legs == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(legs);
    }

    public boolean isLoop() {
        return loop;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public String getEmptyReason() {
        return emptyReason;
    }

    /** Estimated total days (positioning + loop) — not a clock. */
    public float getTotalDays() {
        return totalDays;
    }

    /** Fleet → first market. Included in {@link #getCreditsPerDay()}. */
    public float getPositioningDays() {
        return positioningDays;
    }

    /** Trade legs only (A→…→A for a loop). */
    public float getLoopDays() {
        return Math.max(0f, totalDays - positioningDays);
    }

    public float getGrossProfit() {
        return grossProfit;
    }

    public float getOpsCost() {
        return opsCost;
    }

    public float getNetProfit() {
        return netProfit;
    }

    /** Credits/day over total estimated days (positioning + loop). */
    public float getCreditsPerDay() {
        if (totalDays > 0.0001f) {
            return netProfit / Math.max(totalDays, 0.05f);
        }
        return creditsPerDay;
    }

    public long getComputeMs() {
        return computeMs;
    }

    /** Snapshot collect duration; 0 if unset (old saves / engine-only plans). */
    public long getSnapshotMs() {
        return snapshotMs;
    }

    public void setSnapshotMs(long snapshotMs) {
        this.snapshotMs = Math.max(0L, snapshotMs);
    }

    /** Snapshot + search. Equals {@link #getComputeMs()} when snapshot was not recorded. */
    public long getDisplayMs() {
        return snapshotMs + computeMs;
    }

    public int getExtraStops() {
        return extraStops;
    }

    public String getStartMarketName() {
        if (legs == null) {
            return null;
        }
        for (RouteLeg leg : legs) {
            if (!leg.isPositioning()) {
                return leg.getFromName();
            }
            return leg.getToName();
        }
        return null;
    }

    public String getEndMarketName() {
        if (legs == null || legs.isEmpty()) {
            return null;
        }
        return legs.get(legs.size() - 1).getToName();
    }

    public int getStopCount() {
        return legs == null ? 0 : legs.size();
    }

    public RouteLeg getIncomingLeg(int stopIndex) {
        if (legs == null || stopIndex < 0 || stopIndex >= legs.size()) {
            return null;
        }
        return legs.get(stopIndex);
    }

    public RouteLeg getOutgoingLeg(int stopIndex) {
        if (legs == null) {
            return null;
        }
        int next = stopIndex + 1;
        if (next < 0 || next >= legs.size()) {
            return null;
        }
        return legs.get(next);
    }

    /** Goods to sell on arrival at this stop. Empty for a positioning (empty) hop. */
    public List<TradeAction> sellsAtStop(int stopIndex) {
        RouteLeg incoming = getIncomingLeg(stopIndex);
        if (incoming == null || incoming.isPositioning()) {
            return Collections.emptyList();
        }
        return incoming.getActions();
    }

    /** Goods to buy before leaving this stop. Empty at the last stop. */
    public List<TradeAction> buysAtStop(int stopIndex) {
        RouteLeg outgoing = getOutgoingLeg(stopIndex);
        if (outgoing == null || outgoing.isPositioning()) {
            return Collections.emptyList();
        }
        return outgoing.getActions();
    }

    public String getStopMarketId(int stopIndex) {
        RouteLeg incoming = getIncomingLeg(stopIndex);
        return incoming == null ? null : incoming.getToMarketId();
    }

    public String getStopMarketName(int stopIndex) {
        RouteLeg incoming = getIncomingLeg(stopIndex);
        return incoming == null ? null : incoming.getToName();
    }
}
