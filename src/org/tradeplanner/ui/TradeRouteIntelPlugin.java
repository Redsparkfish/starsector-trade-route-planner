package org.tradeplanner.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.tradeplanner.config.CommodityTradeSettings;
import org.tradeplanner.config.FactionTradeSettings;
import org.tradeplanner.config.MarketMode;
import org.tradeplanner.config.PlannerConfig;
import org.tradeplanner.data.FleetState;
import org.tradeplanner.data.MarketSnapshot;
import org.tradeplanner.engine.RouteOptimizationEngine;
import org.tradeplanner.exec.StopExecutor;
import org.tradeplanner.model.RouteLeg;
import org.tradeplanner.model.RoutePlan;
import org.tradeplanner.model.TradeAction;
import org.tradeplanner.service.DistanceCalculator;
import org.tradeplanner.service.DistanceCalculator.NearestMarket;
import org.tradeplanner.service.DistanceCalculator.TravelEstimate;
import org.tradeplanner.service.MarketDataCollector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Intel entry: estimated route cards, next-stop job sheet, single-target autonavigation.
 * Route plan is saved with the campaign; fleet/market snapshots stay transient.
 */
public class TradeRouteIntelPlugin extends BaseIntelPlugin {

    public static final String BUTTON_REFRESH = "trp_refresh";
    public static final String BUTTON_CALCULATE = "trp_calculate";
    public static final String BUTTON_NAVIGATE = "trp_navigate";
    public static final String BUTTON_ARRIVE = "trp_arrive";
    public static final String BUTTON_EXECUTE = "trp_execute";
    public static final String BUTTON_CLEAR = "trp_clear";
    public static final String BUTTON_TOGGLE_HUD = "trp_toggle_hud";
    public static final String BUTTON_SETTINGS = "trp_settings";
    public static final String BUTTON_SETTINGS_OK = "trp_settings_ok";
    public static final String BUTTON_SETTINGS_CANCEL = "trp_settings_cancel";
    public static final String BUTTON_FACTION_RESET = "trp_faction_reset";
    public static final String PREFIX_FACTION_OPEN = "trp_fo:";
    public static final String PREFIX_FACTION_BLACK = "trp_fb:";
    public static final String PREFIX_COMMODITY = "trp_co:";
    public static final String PREFIX_POS_WEIGHT = "trp_alpha:";
    public static final float[] POS_WEIGHT_CHOICES = {0f, 0.25f, 0.5f, 0.75f, 1f};

    private static final Logger log = Global.getLogger(TradeRouteIntelPlugin.class);

    private transient FleetState fleetState;
    private transient List<MarketSnapshot> markets;
    private transient String nearestMarketName;
    private transient long lastRefreshMs;
    /** Duration of last {@link #refreshSnapshot()}, not a wall-clock timestamp. */
    private transient long lastSnapshotDurationMs;
    private transient boolean calculating;
    /** Wall time when last {@link #calculateRoute()} finished; blocks HUD double-dispatch. */
    private transient long lastCalculateDoneMs;
    private transient String arrivalCooldownMarketId;
    private transient boolean showingSettings;
    private transient FactionTradeSettings factionDraft;
    private transient CommodityTradeSettings commodityDraft;
    private transient Float posTimeWeightDraft;
    /** Same-id echo after {@code updateUIForItem} rebuilds the large description mid-click. */
    private transient Object lastIntelButtonId;
    private transient long lastIntelButtonMs;
    private transient IntelUIAPI lastIntelUi;

    private RoutePlan lastPlan;
    private int nextWaypointIndex;
    private String lastNavMessage;
    private boolean hudCollapsed;
    /** Per-save; null or true shows the campaign-map job sheet. Null means old saves. */
    private Boolean hudVisible;
    /** Campaign clock at last successful 「计算新路线」. */
    private long tripStartTimestamp;
    private float tripStartCredits;
    private boolean tripSummaryReady;
    private float tripActualDays;
    private float tripActualNet;
    private float tripActualCpd;
    private FactionTradeSettings factionTrade;
    /** Per-save commodity on/off; missing keys are on. Null means old saves (all on). */
    private CommodityTradeSettings commodityTrade;
    /** Per-save α; null means use settings.json / Luna default. */
    private Float posTimeWeight;

    public TradeRouteIntelPlugin() {
        setImportant(false);
    }

    public static TradeRouteIntelPlugin getInstance() {
        try {
            if (Global.getSector() == null || Global.getSector().getIntelManager() == null) {
                return null;
            }
            return (TradeRouteIntelPlugin) Global.getSector().getIntelManager()
                    .getFirstIntel(TradeRouteIntelPlugin.class);
        } catch (Exception e) {
            return null;
        }
    }

    public RoutePlan getLastPlan() {
        return lastPlan;
    }

    public int getNextWaypointIndex() {
        return nextWaypointIndex;
    }

    public FleetState getFleetState() {
        return fleetState;
    }

    public String getLastNavMessage() {
        return lastNavMessage;
    }

    /** Status line for HUD/intel. Omits a trailing trip-totals clause when 行程总结 is already shown. */
    public String getPanelNavMessage() {
        if (lastNavMessage == null) {
            return null;
        }
        if (!hasTripSummary()) {
            return lastNavMessage;
        }
        return UiText.stripTripSummarySuffix(lastNavMessage);
    }

    public boolean isTripFinished() {
        return lastPlan != null && !lastPlan.isEmpty()
                && nextWaypointIndex >= lastPlan.getStopCount();
    }

    public boolean isHudCollapsed() {
        return hudCollapsed;
    }

    public boolean isHudVisible() {
        return hudVisible == null || hudVisible;
    }

    public boolean hasTripSummary() {
        return tripSummaryReady;
    }

    public float getTripActualDays() {
        return tripActualDays;
    }

    public float getTripActualNet() {
        return tripActualNet;
    }

    public float getTripActualCpd() {
        return tripActualCpd;
    }

    public String tripSummaryLine() {
        if (!tripSummaryReady) {
            return "";
        }
        return UiText.tripSummaryLine(tripActualDays,
                Misc.getDGSCredits(tripActualNet),
                Misc.getDGSCredits(tripActualCpd));
    }

    public void setHudCollapsed(boolean hudCollapsed) {
        this.hudCollapsed = hudCollapsed;
    }

    public void setHudVisible(boolean hudVisible) {
        this.hudVisible = hudVisible;
    }

    public void toggleHudVisible() {
        hudVisible = !isHudVisible();
    }

    public void setLastNavMessage(String lastNavMessage) {
        this.lastNavMessage = lastNavMessage;
    }

    public FactionTradeSettings getFactionTrade() {
        return factionTrade;
    }

    /** Saved campaign α, or null to fall back to json/Luna. */
    public Float getPosTimeWeight() {
        return posTimeWeight;
    }

    public float ensurePosTimeWeightDraft() {
        if (posTimeWeightDraft == null) {
            PlannerConfig cfg = PlannerConfig.load();
            posTimeWeightDraft = effectivePosTimeWeight(cfg);
        }
        return posTimeWeightDraft;
    }

    public float effectivePosTimeWeight(PlannerConfig cfg) {
        if (posTimeWeight != null) {
            return PlannerConfig.clampFloat(posTimeWeight,
                    PlannerConfig.MIN_POS_TIME_WEIGHT, PlannerConfig.MAX_POS_TIME_WEIGHT);
        }
        return cfg == null ? PlannerConfig.DEFAULT_POS_TIME_WEIGHT : cfg.getPosTimeWeight();
    }

    /** Json/Luna defaults plus this save's faction toggles and α. */
    public PlannerConfig activeConfig() {
        PlannerConfig cfg = PlannerConfig.load();
        ensureFactionTrade(cfg);
        ensureCommodityTrade();
        cfg.applyCampaign(factionTrade, commodityTrade, posTimeWeight);
        return cfg;
    }

    public void setPosTimeWeightDraft(float value) {
        posTimeWeightDraft = PlannerConfig.clampFloat(value,
                PlannerConfig.MIN_POS_TIME_WEIGHT, PlannerConfig.MAX_POS_TIME_WEIGHT);
    }

    public boolean isShowingSettings() {
        return showingSettings;
    }

    /** Uncommitted checkbox state while the settings page is open. Not saved. */
    public FactionTradeSettings getFactionDraft() {
        return factionDraft;
    }

    public FactionTradeSettings ensureFactionDraft() {
        if (factionDraft == null) {
            PlannerConfig cfg = PlannerConfig.load();
            ensureFactionTrade(cfg);
            factionDraft = FactionTradeSettings.snapshot(
                    factionTrade, MarketDataCollector.economyFactionIds(), cfg);
        }
        return factionDraft;
    }

    public CommodityTradeSettings getCommodityDraft() {
        return commodityDraft;
    }

    public CommodityTradeSettings ensureCommodityDraft() {
        if (commodityDraft == null) {
            ensureCommodityTrade();
            commodityDraft = CommodityTradeSettings.snapshot(
                    commodityTrade, MarketDataCollector.tradeCommodityIds());
        }
        return commodityDraft;
    }

    public void ensureCommodityTrade() {
        if (commodityTrade == null) {
            commodityTrade = new CommodityTradeSettings();
        }
    }

    /**
     * Seed once per campaign. Old ALLOW_BLACK_MARKET saves become both-on for current economy factions.
     */
    public void ensureFactionTrade(PlannerConfig defaults) {
        if (factionTrade != null) {
            return;
        }
        factionTrade = new FactionTradeSettings();
        if (defaults != null && defaults.getMarketMode() == MarketMode.ALLOW_BLACK_MARKET) {
            factionTrade.seedBothOn(MarketDataCollector.economyFactionIds());
        }
    }

    public void openSettings() {
        PlannerConfig cfg = PlannerConfig.load();
        ensureFactionTrade(cfg);
        ensureCommodityTrade();
        factionDraft = FactionTradeSettings.snapshot(
                factionTrade, MarketDataCollector.economyFactionIds(), cfg);
        commodityDraft = CommodityTradeSettings.snapshot(
                commodityTrade, MarketDataCollector.tradeCommodityIds());
        posTimeWeightDraft = effectivePosTimeWeight(cfg);
        showingSettings = true;
    }

    public void confirmSettings() {
        PlannerConfig cfg = PlannerConfig.load();
        ensureFactionTrade(cfg);
        ensureCommodityTrade();
        if (factionDraft != null) {
            factionTrade.replaceWith(factionDraft);
        }
        if (commodityDraft != null) {
            commodityTrade.replaceWith(commodityDraft);
        }
        if (posTimeWeightDraft != null) {
            posTimeWeight = PlannerConfig.clampFloat(posTimeWeightDraft,
                    PlannerConfig.MIN_POS_TIME_WEIGHT, PlannerConfig.MAX_POS_TIME_WEIGHT);
        }
        factionDraft = null;
        commodityDraft = null;
        posTimeWeightDraft = null;
        showingSettings = false;
        lastNavMessage = UiText.SETTINGS_SAVED;
    }

    public void cancelSettings() {
        factionDraft = null;
        commodityDraft = null;
        posTimeWeightDraft = null;
        showingSettings = false;
    }

    /** Draft only; still needs 确认 to write the campaign save. */
    public void resetFactionDraft() {
        if (factionDraft == null) {
            factionDraft = new FactionTradeSettings();
        } else {
            factionDraft.clear();
        }
        if (commodityDraft == null) {
            commodityDraft = new CommodityTradeSettings();
        } else {
            commodityDraft.clear();
        }
        posTimeWeightDraft = PlannerConfig.DEFAULT_POS_TIME_WEIGHT;
    }

    public boolean isStopExecutorActive() {
        return StopExecutor.get().isActive();
    }

    public void executeNextStop() {
        arrivalCooldownMarketId = null;
        StopExecutor.get().start(this);
    }

    /** Remember this market so auto-trade will not retry until the fleet leaves. */
    public void rememberArrivalAttempt(String marketId) {
        if (marketId != null && !marketId.isEmpty()) {
            arrivalCooldownMarketId = marketId;
        }
    }

    public boolean isOnArrivalCooldown() {
        return isOnArrivalCooldown(Global.getSector().getPlayerFleet());
    }

    public void clearPlan() {
        StopExecutor.get().silentReset();
        lastPlan = null;
        nextWaypointIndex = 0;
        lastNavMessage = null;
        arrivalCooldownMarketId = null;
        tripStartTimestamp = 0L;
        tripStartCredits = 0f;
        tripSummaryReady = false;
        tripActualDays = 0f;
        tripActualNet = 0f;
        tripActualCpd = 0f;
        setImportant(false);
    }

    @Override
    public boolean hasSmallDescription() {
        return true;
    }

    @Override
    public boolean hasLargeDescription() {
        return true;
    }

    @Override
    protected String getName() {
        return UiText.TITLE;
    }

    @Override
    public String getIcon() {
        try {
            return Global.getSettings().getSpriteName(PlannerConfig.MOD_ID, "intel");
        } catch (Exception e) {
            return "graphics/icons/intel/price_update.png";
        }
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_TRADE);
        return tags;
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        TradeRouteCustomPanel.appendSmallSummary(info, this);
    }

    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        if (showingSettings) {
            TradeSettingsPanel.render(this, panel, width, height);
            return;
        }
        if (markets == null) {
            refreshSnapshot();
        }
        TradeRouteCustomPanel.render(this, panel, width, height);
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (ui != null) {
            lastIntelUi = ui;
        }
        if (!applyIntelButton(buttonId, ui)) {
            super.buttonPressConfirmed(buttonId, ui);
        }
    }

    /** Nested two-column strips inside the large description. */
    public void notifyStripPress(Object buttonId) {
        applyIntelButton(buttonId, lastIntelUi);
    }

    private boolean applyIntelButton(Object buttonId, IntelUIAPI ui) {
        if (TradeSettingsPanel.handleFactionToggle(buttonId, this)) {
            return true;
        }
        if (isEchoIntelClick(buttonId)) {
            return true;
        }
        if (BUTTON_REFRESH.equals(buttonId)) {
            refreshSnapshot();
            lastNavMessage = null;
            refreshIntelItem(ui);
            return true;
        }
        if (BUTTON_CALCULATE.equals(buttonId)) {
            if (isStopExecutorActive()) {
                StopExecutor.get().abort(this, UiText.EXEC_RECALC_ABORT);
            }
            long t0 = System.currentTimeMillis();
            calculateRoute();
            long uiStarted = System.currentTimeMillis();
            refreshIntelItem(ui);
            long uiMs = System.currentTimeMillis() - uiStarted;
            long totalMs = System.currentTimeMillis() - t0;
            log.info("TradeRoutePlanner calculateUI: uiMs=" + uiMs + " totalMs=" + totalMs);
            return true;
        }
        if (BUTTON_NAVIGATE.equals(buttonId)) {
            layInNextStop();
            refreshIntelItem(ui);
            return true;
        }
        if (BUTTON_ARRIVE.equals(buttonId)) {
            markArrived();
            refreshIntelItem(ui);
            return true;
        }
        if (BUTTON_EXECUTE.equals(buttonId)) {
            executeNextStop();
            refreshIntelItem(ui);
            return true;
        }
        if (BUTTON_CLEAR.equals(buttonId)) {
            clearPlan();
            refreshIntelItem(ui);
            return true;
        }
        if (BUTTON_TOGGLE_HUD.equals(buttonId)) {
            toggleHudVisible();
            lastNavMessage = UiText.hudToggled(isHudVisible());
            try {
                Global.getSector().getCampaignUI().addMessage(
                        lastNavMessage, Misc.getHighlightColor());
            } catch (Exception ignored) {
            }
            refreshIntelItem(ui);
            return true;
        }
        if (BUTTON_SETTINGS.equals(buttonId)) {
            openSettings();
            refreshIntelItem(ui);
            return true;
        }
        if (TradeSettingsPanel.handleButton(buttonId, this)) {
            refreshIntelItem(ui);
            return true;
        }
        return false;
    }

    private void refreshIntelItem(IntelUIAPI ui) {
        if (ui == null) {
            return;
        }
        ui.updateUIForItem(this);
    }

    /**
     * {@code updateUIForItem} rebuilds the large description while the mouse is still down,
     * so the same click hits the replacement button. A two-state control such as HUD toggle
     * would flip twice and look like a no-op.
     */
    private boolean isEchoIntelClick(Object buttonId) {
        if (buttonId == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (buttonId.equals(lastIntelButtonId) && now - lastIntelButtonMs < 350L) {
            return true;
        }
        lastIntelButtonId = buttonId;
        lastIntelButtonMs = now;
        return false;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        SectorEntityToken next = currentWaypointEntity();
        if (next != null) {
            return next;
        }
        if (nearestMarketName != null && markets != null) {
            for (MarketSnapshot snap : markets) {
                if (nearestMarketName.equals(snap.getName())) {
                    SectorEntityToken e = snap.getPrimaryEntity();
                    if (e != null) {
                        return e;
                    }
                }
            }
        }
        return Global.getSector().getPlayerFleet();
    }

    @Override
    public List<IntelInfoPlugin.ArrowData> getArrowData(SectorMapAPI map) {
        if (lastPlan == null || lastPlan.isEmpty() || isTripFinished()) {
            return null;
        }
        List<IntelInfoPlugin.ArrowData> arrows = new ArrayList<>();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        SectorEntityToken next = currentWaypointEntity();
        if (fleet != null && next != null) {
            IntelInfoPlugin.ArrowData main = new IntelInfoPlugin.ArrowData(fleet, next);
            main.color = Misc.getHighlightColor();
            arrows.add(main);
        }
        List<RouteLeg> legs = lastPlan.getLegs();
        for (int i = nextWaypointIndex; i < legs.size(); i++) {
            RouteLeg leg = legs.get(i);
            SectorEntityToken from = RouteLeg.FLEET_ORIGIN_ID.equals(leg.getFromMarketId())
                    ? fleet
                    : resolveMarketEntity(leg.getFromMarketId());
            SectorEntityToken to = resolveMarketEntity(leg.getToMarketId());
            if (from == null || to == null || from == to) {
                continue;
            }
            if (i == nextWaypointIndex && fleet != null && from == fleet && to == next) {
                continue;
            }
            IntelInfoPlugin.ArrowData hop = new IntelInfoPlugin.ArrowData(from, to);
            hop.color = Misc.getHighlightColor();
            hop.alphaMult = 0.22f;
            arrows.add(hop);
        }
        return arrows.isEmpty() ? null : arrows;
    }

    public void layInNextStop() {
        if (lastPlan == null || lastPlan.isEmpty()) {
            lastNavMessage = UiText.NAV_NO_ROUTE;
            return;
        }
        if (isTripFinished()) {
            lastNavMessage = UiText.TRIP_FINISHED_PERIOD;
            return;
        }
        SectorEntityToken entity = currentWaypointEntity();
        String name = lastPlan.getStopMarketName(nextWaypointIndex);
        if (entity == null) {
            lastNavMessage = UiText.unresolvedEntity(name);
            return;
        }
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet != null && isArrivedAt(fleet, entity)) {
            lastNavMessage = UiText.alreadyAt(entity.getName());
            return;
        }
        try {
            Global.getSector().getCampaignUI().layInCourseForNextStep(entity);
            Global.getSector().getCampaignUI().addMessage(
                    UiText.courseSetMessage(entity.getName()), Misc.getHighlightColor());
            lastNavMessage = UiText.courseSetIntel(entity.getName());
        } catch (Exception e) {
            lastNavMessage = UiText.navFailed(e.getMessage());
            log.warn("TradeRoutePlanner layInCourse failed", e);
        }
    }

    public void markArrived() {
        applyArrivalAdvance(false);
    }

    public void openIntelDetails() {
        try {
            Global.getSector().getCampaignUI().showCoreUITab(CoreUITabId.INTEL, this);
        } catch (Exception e) {
            lastNavMessage = UiText.openIntelFailed(e.getMessage());
            log.warn("TradeRoutePlanner open intel failed", e);
        }
    }

    private void applyArrivalAdvance(boolean fromAuto) {
        if (lastPlan == null || lastPlan.isEmpty() || isTripFinished()) {
            lastNavMessage = UiText.NAV_NO_WAYPOINT;
            return;
        }
        String arrived = lastPlan.getStopMarketName(nextWaypointIndex);
        String arrivedId = lastPlan.getStopMarketId(nextWaypointIndex);
        nextWaypointIndex++;
        arrivalCooldownMarketId = arrivedId;
        String msg;
        if (isTripFinished()) {
            captureTripSummary();
            msg = UiText.arrivedFinished(arrived);
        } else {
            String next = lastPlan.getStopMarketName(nextWaypointIndex);
            msg = UiText.arrivedNext(arrived, next);
        }
        lastNavMessage = msg;
        if (fromAuto) {
            try {
                Global.getSector().getCampaignUI().addMessage(msg, Misc.getHighlightColor());
            } catch (Exception e) {
                log.info("TradeRoutePlanner arrival: " + msg);
            }
        }
        log.info("TradeRoutePlanner arrival: auto=" + fromAuto
                + " arrived=" + arrived
                + " nextIndex=" + nextWaypointIndex
                + " finished=" + isTripFinished());
    }

    private void beginTripClock() {
        tripSummaryReady = false;
        tripActualDays = 0f;
        tripActualNet = 0f;
        tripActualCpd = 0f;
        tripStartTimestamp = 0L;
        tripStartCredits = 0f;
        if (lastPlan == null || lastPlan.isEmpty()) {
            return;
        }
        try {
            tripStartTimestamp = Global.getSector().getClock().getTimestamp();
            tripStartCredits = playerCredits();
        } catch (Exception e) {
            tripStartTimestamp = 0L;
        }
    }

    private void captureTripSummary() {
        if (tripSummaryReady || tripStartTimestamp == 0L) {
            return;
        }
        try {
            CampaignClockAPI clock = Global.getSector().getClock();
            tripActualDays = Math.max(0f, clock.getElapsedDaysSince(tripStartTimestamp));
            tripActualNet = playerCredits() - tripStartCredits;
            tripActualCpd = tripActualDays > 0.01f ? tripActualNet / tripActualDays : 0f;
            tripSummaryReady = true;
            log.info("TradeRoutePlanner trip summary: days=" + String.format("%.2f", tripActualDays)
                    + " net=" + (int) tripActualNet
                    + " cpd=" + (int) tripActualCpd
                    + " loop=" + (lastPlan != null && lastPlan.isLoop()));
        } catch (Exception e) {
            log.warn("TradeRoutePlanner trip summary failed", e);
        }
    }

    private static float playerCredits() {
        try {
            return Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        } catch (Exception e) {
            return 0f;
        }
    }

    private boolean isOnArrivalCooldown(CampaignFleetAPI fleet) {
        if (arrivalCooldownMarketId == null) {
            return false;
        }
        SectorEntityToken stayed = resolveMarketEntity(arrivalCooldownMarketId);
        if (stayed == null || !isArrivedAt(fleet, stayed)) {
            arrivalCooldownMarketId = null;
            return false;
        }
        return true;
    }

    public static boolean isArrivedAt(CampaignFleetAPI fleet, SectorEntityToken dest) {
        if (fleet == null || dest == null) {
            return false;
        }
        // Do not use fleet.getInteractionTarget() — vanilla sets that to the
        // autopilot destination while still flying, including just after
        // jumping into the target's system.
        try {
            InteractionDialogAPI dialog = Global.getSector().getCampaignUI()
                    .getCurrentInteractionDialog();
            if (dialog != null && sameMarket(dialog.getInteractionTarget(), dest)) {
                return true;
            }
        } catch (Exception ignored) {
        }
        if (fleet.getContainingLocation() == null || dest.getContainingLocation() == null) {
            return false;
        }
        if (fleet.getContainingLocation() != dest.getContainingLocation()) {
            return false;
        }
        if (fleet.isInHyperspace() && (dest.getMarket() == null || !dest.getMarket().isInHyperspace())) {
            return false;
        }
        float dist = Misc.getDistance(fleet, dest);
        return dist <= fleet.getRadius() + dest.getRadius();
    }

    public static boolean sameMarket(SectorEntityToken a, SectorEntityToken b) {
        if (a == null || b == null) {
            return false;
        }
        if (a == b) {
            return true;
        }
        if (a.getMarket() != null && b.getMarket() != null) {
            String idA = a.getMarket().getId();
            String idB = b.getMarket().getId();
            return idA != null && idA.equals(idB);
        }
        return false;
    }

    public SectorEntityToken currentWaypointEntity() {
        if (lastPlan == null || lastPlan.isEmpty() || isTripFinished()) {
            return null;
        }
        return resolveMarketEntity(lastPlan.getStopMarketId(nextWaypointIndex));
    }

    static SectorEntityToken resolveMarketEntity(String marketId) {
        if (marketId == null || RouteLeg.FLEET_ORIGIN_ID.equals(marketId)) {
            return null;
        }
        MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
        return market == null ? null : market.getPrimaryEntity();
    }

    private void refreshSnapshot() {
        long t0 = System.currentTimeMillis();
        PlannerConfig cfg = activeConfig();
        fleetState = FleetState.fromPlayer();
        markets = MarketDataCollector.collect(cfg, fleetState);
        NearestMarket nearest = DistanceCalculator.nearestMarket(fleetState, markets);
        if (nearest != null && nearest.market != null) {
            nearestMarketName = nearest.market.getName();
        } else {
            nearestMarketName = null;
        }
        lastRefreshMs = System.currentTimeMillis();
        lastSnapshotDurationMs = lastRefreshMs - t0;
        logSummary(cfg);
    }

    public void calculateRoute() {
        if (calculating) {
            log.info("TradeRoutePlanner calculate: skipped reentrant");
            return;
        }
        long now = System.currentTimeMillis();
        if (lastCalculateDoneMs > 0L && now - lastCalculateDoneMs < 750L) {
            log.info("TradeRoutePlanner calculate: skipped duplicate dt="
                    + (now - lastCalculateDoneMs));
            return;
        }
        calculating = true;
        hudVisible = true;
        long t0 = now;
        try {
            calculateRouteBody(t0);
        } finally {
            calculating = false;
            lastCalculateDoneMs = System.currentTimeMillis();
        }
    }

    private void calculateRouteBody(long t0) {
        refreshSnapshot();
        long snapMs = lastSnapshotDurationMs;
        PlannerConfig cfg = activeConfig();
        lastPlan = RouteOptimizationEngine.optimize(fleetState, markets, cfg);
        long searchMs = lastPlan == null ? 0L : lastPlan.getComputeMs();
        if (lastPlan != null) {
            lastPlan.setSnapshotMs(snapMs);
        }
        long postStarted = System.currentTimeMillis();
        nextWaypointIndex = 0;
        lastNavMessage = UiText.computeDone(lastPlan);
        arrivalCooldownMarketId = null;
        beginTripClock();
        setImportant(lastPlan != null && !lastPlan.isEmpty());
        logRoute(lastPlan);
        logFirstLeg(lastPlan);
        long postMs = System.currentTimeMillis() - postStarted;
        long wallMs = System.currentTimeMillis() - t0;
        int n = markets == null ? 0 : markets.size();
        log.info("TradeRoutePlanner calculate: snapMs=" + snapMs
                + " searchMs=" + searchMs
                + " postMs=" + postMs
                + " wallMs=" + wallMs
                + " markets=" + n);
    }

    private void logFirstLeg(RoutePlan plan) {
        if (plan == null || plan.isEmpty() || fleetState == null) {
            return;
        }
        SectorEntityToken dest = resolveMarketEntity(plan.getStopMarketId(0));
        String destName = dest == null ? plan.getStopMarketName(0) : dest.getName();
        String destLoc = "?";
        if (dest != null && dest.getContainingLocation() != null) {
            destLoc = dest.getContainingLocation().getName();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("TradeRoutePlanner firstLeg: fleetInHyper=").append(fleetState.isInHyperspace());
        sb.append(" fleetLoc=").append(fleetState.getLocationName());
        sb.append(" dest=").append(destName);
        sb.append(" destLoc=").append(destLoc);
        if (dest != null) {
            TravelEstimate est = DistanceCalculator.estimateTo(fleetState, dest);
            sb.append(" sameLoc=").append(est.isSameLocation());
            sb.append(" hyperLY=").append(String.format("%.3f", est.getHyperspaceLY()));
            sb.append(" hyperDays=").append(String.format("%.3f", est.getHyperspaceDays()));
            sb.append(" inSystemDays=").append(String.format("%.3f", est.getInSystemDays()));
            sb.append(" totalDays=").append(String.format("%.3f", est.getTotalDays()));
            sb.append(" hyperFuel=").append(String.format("%.1f", est.getHyperspaceFuel()));
        } else {
            RouteLeg first = plan.getIncomingLeg(0);
            if (first != null) {
                sb.append(" sameLoc=?");
                sb.append(" hyperLY=").append(String.format("%.3f", first.getHyperspaceLY()));
                sb.append(" hyperDays=").append(String.format("%.3f", first.getHyperspaceDays()));
                sb.append(" inSystemDays=").append(String.format("%.3f", first.getInSystemDays()));
            }
        }
        log.info(sb.toString());
    }

    private void logRoute(RoutePlan plan) {
        if (plan == null) {
            log.info("TradeRoutePlanner route: plan=null");
            return;
        }
        log.info("TradeRoutePlanner route: stops=" + plan.getExtraStops()
                + " legs=" + plan.getLegs().size()
                + " cpd=" + (int) plan.getCreditsPerDay()
                + " net=" + (int) plan.getNetProfit()
                + " posDays=" + String.format("%.2f", plan.getPositioningDays())
                + " loopDays=" + String.format("%.2f", plan.getLoopDays())
                + " days=" + String.format("%.2f", plan.getTotalDays())
                + " start=" + (plan.getStartMarketName() == null ? "-" : plan.getStartMarketName())
                + " truncated=" + plan.isTruncated()
                + " loop=" + plan.isLoop()
                + " empty=" + (plan.getEmptyReason() == null ? "-" : plan.getEmptyReason())
                + " ms=" + plan.getComputeMs());
        for (RouteLeg leg : plan.getLegs()) {
            StringBuilder sb = new StringBuilder();
            sb.append("  leg ").append(leg.getFromName()).append(" -> ").append(leg.getToName());
            sb.append(" days=").append(String.format("%.2f", leg.getTotalDays()));
            sb.append(" gross=").append((int) leg.getGrossProfit());
            for (TradeAction action : leg.getActions()) {
                sb.append(" [").append(action.getName()).append(" x").append(action.getQuantity()).append("]");
            }
            log.info(sb.toString());
        }
    }

    private void logSummary(PlannerConfig cfg) {
        int n = markets == null ? 0 : markets.size();
        log.info("TradeRoutePlanner snapshot: markets=" + n
                + " credits=" + (fleetState == null ? 0 : (int) fleetState.getCredits())
                + " cargoLeft=" + (fleetState == null ? 0 : (int) fleetState.getCargoLeft())
                + " fuelPerLY=" + (fleetState == null ? 0 : fleetState.getFuelPerLightYear())
                + " margin=" + cfg.getQtySafetyMargin()
                + " ms=" + lastSnapshotDurationMs);
    }
}
