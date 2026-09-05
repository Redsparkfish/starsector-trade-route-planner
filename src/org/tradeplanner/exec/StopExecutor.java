package org.tradeplanner.exec;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI.CoreUITradeMode;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.rulecmd.OpenCoreTab;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.tradeplanner.config.PlannerConfig;
import org.tradeplanner.config.TradeAccess;
import org.tradeplanner.data.LogisticsReserve;
import org.tradeplanner.model.RouteLeg;
import org.tradeplanner.model.RoutePlan;
import org.tradeplanner.model.TradeAction;
import org.tradeplanner.service.PriceQuoter;
import org.tradeplanner.ui.TradeRouteIntelPlugin;
import org.tradeplanner.ui.UiText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dock and fill the current stop's job sheet. Navigation is vanilla
 * {@code layInCourseForNextStep} only — this executor never plots or watches a course.
 */
public final class StopExecutor {

    private static final Logger log = Global.getLogger(StopExecutor.class);
    private static final float DIALOG_TIMEOUT_SEC = 8f;
    private static final float TRADE_TIMEOUT_SEC = 8f;
    private static final String OPTION_TRADE = "marketOpenCoreUI";
    private static final String OPTION_LEAVE = "marketLeave";

    private static StopExecutor instance;

    private enum Phase {
        IDLE,
        CLOSING_UI,
        OPENING_DIALOG,
        WAIT_MODE,
        OPENING_TRADE,
        EXECUTING,
        DISMISSING
    }

    private Phase phase = Phase.IDLE;
    private float phaseAge;
    private SectorEntityToken dest;
    private String destName;
    private int stopIndex;
    private boolean wantBlack;
    private CoreUITradeMode tradeMode;
    /** Set when the current run filled the sheet or confirmed there was nothing to do. */
    private boolean pendingSuccess;

    public static StopExecutor get() {
        if (instance == null) {
            instance = new StopExecutor();
        }
        return instance;
    }

    public boolean isActive() {
        return phase != Phase.IDLE;
    }

    public void start(TradeRouteIntelPlugin intel) {
        if (isActive()) {
            intel.setLastNavMessage(UiText.EXEC_ALREADY);
            return;
        }
        pendingSuccess = false;
        if (intel.getLastPlan() == null || intel.getLastPlan().isEmpty()) {
            finish(intel, UiText.EXEC_NO_ROUTE, false, false);
            return;
        }
        if (intel.isTripFinished()) {
            finish(intel, UiText.TRIP_FINISHED_PERIOD, false, false);
            return;
        }
        if (Global.getCurrentState() != GameState.CAMPAIGN) {
            finish(intel, UiText.EXEC_NOT_CAMPAIGN, false, false);
            return;
        }
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui != null && ui.isShowingMenu()) {
            finish(intel, UiText.EXEC_OTHER_DIALOG, false, false);
            return;
        }
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) {
            finish(intel, UiText.EXEC_NO_FLEET, false, false);
            return;
        }
        if (fleet.getBattle() != null) {
            finish(intel, UiText.EXEC_IN_BATTLE, false, false);
            return;
        }
        dest = intel.currentWaypointEntity();
        destName = intel.getLastPlan().getStopMarketName(intel.getNextWaypointIndex());
        if (dest == null) {
            finish(intel, UiText.unresolvedEntity(destName), false, false);
            return;
        }
        stopIndex = intel.getNextWaypointIndex();
        if (!TradeRouteIntelPlugin.isArrivedAt(fleet, dest)) {
            finish(intel, UiText.EXEC_NOT_ARRIVED, false, false);
            return;
        }
        if (dialogIsForeign(dest)) {
            finish(intel, UiText.EXEC_OTHER_DIALOG, false, false);
            return;
        }
        if (!hasWork(intel, stopIndex)) {
            intel.markArrived();
            String msg = UiText.EXEC_NO_WORK;
            if (!intel.isTripFinished()) {
                msg = UiText.noWorkNext(intel.getLastPlan().getStopMarketName(intel.getNextWaypointIndex()));
            }
            finish(intel, msg, true, true);
            return;
        }
        intel.setLastNavMessage(UiText.EXEC_RUNNING);
        addMessage(UiText.EXEC_RUNNING);
        if (dialogIsDest(dest)) {
            phase = Phase.WAIT_MODE;
            phaseAge = 0f;
            return;
        }
        if (ui != null && ui.getCurrentCoreTab() != null) {
            try {
                ui.showCoreUITab(null);
            } catch (Exception ignored) {
            }
            phase = Phase.CLOSING_UI;
            phaseAge = 0f;
            return;
        }
        enterOpeningDialog(intel);
    }

    public void abort(TradeRouteIntelPlugin intel, String reason) {
        if (!isActive()) {
            return;
        }
        dismissQuietly();
        finish(intel, reason, true, false);
    }

    /** Stop auto-trade without pausing or writing a campaign message. */
    public void silentReset() {
        if (!isActive()) {
            return;
        }
        dismissQuietly();
        reset();
    }

    public void advance(float amount) {
        TradeRouteIntelPlugin intel = TradeRouteIntelPlugin.getInstance();
        if (!isActive()) {
            tryAutoStart(intel);
            return;
        }
        if (intel == null) {
            reset();
            return;
        }
        phaseAge += Math.max(amount, 0f);
        if (interrupted(intel)) {
            return;
        }
        switch (phase) {
            case CLOSING_UI:
                tickClosingUi(intel);
                break;
            case OPENING_DIALOG:
                tickOpeningDialog(intel);
                break;
            case WAIT_MODE:
                tickWaitMode(intel);
                break;
            case OPENING_TRADE:
                tickOpeningTrade(intel);
                break;
            case EXECUTING:
                tickExecuting(intel);
                break;
            case DISMISSING:
                tickDismissing(intel);
                break;
            default:
                break;
        }
    }

    private void tickClosingUi(TradeRouteIntelPlugin intel) {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        CoreUITabId tab = null;
        try {
            tab = ui == null ? null : ui.getCurrentCoreTab();
        } catch (Exception ignored) {
        }
        if (tab != null) {
            if (phaseAge > DIALOG_TIMEOUT_SEC) {
                abort(intel, UiText.EXEC_CANT_CLOSE_UI);
            }
            return;
        }
        enterOpeningDialog(intel);
    }

    private void enterOpeningDialog(TradeRouteIntelPlugin intel) {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui == null) {
            abort(intel, UiText.EXEC_CANT_DOCK);
            return;
        }
        if (dialogIsDest(dest)) {
            phase = Phase.WAIT_MODE;
            phaseAge = 0f;
            return;
        }
        if (ui.isShowingDialog() || ui.getCurrentInteractionDialog() != null) {
            abort(intel, UiText.EXEC_OTHER_DOCK);
            return;
        }
        boolean shown;
        try {
            shown = ui.showInteractionDialog(dest);
        } catch (Exception e) {
            abort(intel, UiText.execDockFailed(e.getMessage()));
            return;
        }
        if (!shown) {
            abort(intel, UiText.EXEC_CANT_DOCK);
            return;
        }
        phase = Phase.OPENING_DIALOG;
        phaseAge = 0f;
    }

    private void tickOpeningDialog(TradeRouteIntelPlugin intel) {
        InteractionDialogAPI dialog = currentDialog();
        if (dialog == null) {
            if (phaseAge > DIALOG_TIMEOUT_SEC) {
                abort(intel, UiText.EXEC_DOCK_TIMEOUT);
            }
            return;
        }
        phase = Phase.WAIT_MODE;
        phaseAge = 0f;
    }

    private void tickWaitMode(TradeRouteIntelPlugin intel) {
        InteractionDialogAPI dialog = currentDialog();
        if (dialog == null) {
            abort(intel, UiText.EXEC_DIALOG_CLOSED);
            return;
        }
        if (phaseAge < 0.15f) {
            return;
        }
        MarketAPI market = dest == null ? null : dest.getMarket();
        if (market == null) {
            abort(intel, UiText.EXEC_NO_MARKET);
            return;
        }
        PlannerConfig cfg = intel.activeConfig();
        wantBlack = TradeAccess.useBlack(market, cfg);
        tradeMode = readTradeMode(dialog);
        String blocked = blockReason(market, tradeMode, wantBlack, cfg);
        if (blocked != null) {
            abort(intel, blocked);
            return;
        }
        if (!clickTrade(dialog, tradeMode)) {
            abort(intel, UiText.EXEC_CANT_TRADE_UI);
            return;
        }
        phase = Phase.OPENING_TRADE;
        phaseAge = 0f;
    }

    private void tickOpeningTrade(TradeRouteIntelPlugin intel) {
        InteractionDialogAPI dialog = currentDialog();
        if (dialog == null) {
            abort(intel, UiText.EXEC_DIALOG_CLOSED);
            return;
        }
        CoreUITabId tab = null;
        try {
            tab = Global.getSector().getCampaignUI().getCurrentCoreTab();
        } catch (Exception ignored) {
        }
        if (tab == CoreUITabId.CARGO || phaseAge > 0.45f) {
            phase = Phase.EXECUTING;
            phaseAge = 0f;
            return;
        }
        if (phaseAge > TRADE_TIMEOUT_SEC) {
            abort(intel, UiText.EXEC_TRADE_TIMEOUT);
        }
    }

    private void tickExecuting(TradeRouteIntelPlugin intel) {
        InteractionDialogAPI dialog = currentDialog();
        MarketAPI market = dest == null ? null : dest.getMarket();
        if (market == null) {
            abort(intel, UiText.EXEC_NO_MARKET);
            return;
        }
        String summary;
        try {
            summary = fillJobSheet(intel, market);
        } catch (Exception e) {
            log.warn("TradeRoutePlanner execute fill failed", e);
            abort(intel, UiText.execFillFailed(e.getMessage()));
            return;
        }
        dismissDialog(dialog);
        intel.markArrived();
        pendingSuccess = true;
        phase = Phase.DISMISSING;
        phaseAge = 0f;
        intel.setLastNavMessage(summary);
    }

    private void tickDismissing(TradeRouteIntelPlugin intel) {
        if (currentDialog() != null && phaseAge < 2f) {
            dismissQuietly();
            return;
        }
        String msg = intel.getLastNavMessage();
        finish(intel, msg == null ? UiText.EXEC_DONE : msg, true, pendingSuccess);
    }

    private boolean interrupted(TradeRouteIntelPlugin intel) {
        if (Global.getCurrentState() != GameState.CAMPAIGN) {
            abort(intel, UiText.EXEC_LEFT_CAMPAIGN);
            return true;
        }
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet != null && fleet.getBattle() != null) {
            abort(intel, UiText.EXEC_BATTLE_ABORT);
            return true;
        }
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui != null && ui.isShowingMenu()) {
            abort(intel, UiText.EXEC_MENU_ABORT);
            return true;
        }
        return false;
    }

    private static boolean hasWork(TradeRouteIntelPlugin intel, int index) {
        RoutePlan plan = intel.getLastPlan();
        if (plan == null) {
            return false;
        }
        if (!plan.sellsAtStop(index).isEmpty() || !plan.buysAtStop(index).isEmpty()) {
            return true;
        }
        RouteLeg out = plan.getOutgoingLeg(index);
        if (out != null && (out.getFuelPurchased() > 0.5f || out.getSupplyPurchased() > 0.5f)) {
            return true;
        }
        return LogisticsReserve.needsRestock(intel.activeConfig(), Global.getSector().getPlayerFleet());
    }

    private static String blockReason(MarketAPI market, CoreUITradeMode mode, boolean wantBlack,
                                      PlannerConfig cfg) {
        if (mode == CoreUITradeMode.NONE) {
            return UiText.EXEC_PORT_BLOCKED;
        }
        if (wantBlack) {
            if (!market.hasSubmarket(Submarkets.SUBMARKET_BLACK)) {
                return UiText.EXEC_NEED_BLACK;
            }
            return null;
        }
        if (mode == CoreUITradeMode.SNEAK) {
            if (cfg != null && cfg.allowBlackMarket(market.getFactionId())) {
                return UiText.EXEC_SNEAK_OPEN;
            }
            return UiText.EXEC_NO_BLACK_OR_OPEN;
        }
        if (!market.hasSubmarket(Submarkets.SUBMARKET_OPEN)) {
            return UiText.EXEC_NO_OPEN;
        }
        return null;
    }

    private CoreUITradeMode readTradeMode(InteractionDialogAPI dialog) {
        try {
            InteractionDialogPlugin plugin = dialog.getPlugin();
            if (plugin != null && plugin.getMemoryMap() != null) {
                MemoryAPI local = plugin.getMemoryMap().get(MemKeys.LOCAL);
                if (local != null) {
                    return Misc.getTradeMode(local);
                }
            }
        } catch (Exception ignored) {
        }
        if (dest != null && dest.getMemoryWithoutUpdate() != null) {
            return Misc.getTradeMode(dest.getMemoryWithoutUpdate());
        }
        return CoreUITradeMode.OPEN;
    }

    private boolean clickTrade(InteractionDialogAPI dialog, CoreUITradeMode mode) {
        try {
            dialog.getPlugin().optionSelected(null, OPTION_TRADE);
            return true;
        } catch (Exception e) {
            log.info("TradeRoutePlanner optionSelected trade failed, trying OpenCoreTab");
        }
        try {
            Map<String, MemoryAPI> map = dialog.getPlugin().getMemoryMap();
            String modeName = mode == null ? "OPEN" : mode.name();
            new OpenCoreTab().execute("OpenCoreTab", dialog, Misc.tokenize("CARGO " + modeName), map);
            return true;
        } catch (Exception e) {
            log.warn("TradeRoutePlanner OpenCoreTab failed", e);
            return false;
        }
    }

    private String fillJobSheet(TradeRouteIntelPlugin intel, MarketAPI market) {
        PlannerConfig cfg = intel.activeConfig();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        RoutePlan plan = intel.getLastPlan();
        List<String> lines = new ArrayList<>();
        boolean canBlack = TradeAccess.useBlack(market, cfg)
                && market.hasSubmarket(Submarkets.SUBMARKET_BLACK);
        boolean canOpen = TradeAccess.canOpen(market, cfg)
                && tradeMode != CoreUITradeMode.SNEAK;
        RouteLeg outgoing = plan.getOutgoingLeg(stopIndex);
        int fuelGoal = LogisticsReserve.fuelFloorLive(cfg, fleet);
        int supplyGoal = LogisticsReserve.supplyFloorLive(cfg, fleet);
        if (outgoing != null) {
            fuelGoal = Math.max(fuelGoal, held(fleet.getCargo(), Commodities.FUEL)
                    + Math.round(outgoing.getFuelPurchased()));
            supplyGoal = Math.max(supplyGoal, held(fleet.getCargo(), Commodities.SUPPLIES)
                    + Math.round(outgoing.getSupplyPurchased()));
        }
        if (canBlack) {
            fillChannel(market, plan, cfg, fleet, true, lines, fuelGoal, supplyGoal);
        }
        if (canOpen) {
            fillChannel(market, plan, cfg, fleet, false, lines, fuelGoal, supplyGoal);
        }
        if (!canBlack && !canOpen) {
            throw new IllegalStateException(UiText.EXEC_NO_SUBMARKET);
        }
        StringBuilder sb = new StringBuilder(UiText.EXEC_DONE_PREFIX);
        if (lines.isEmpty()) {
            sb.append(UiText.EXEC_NO_FILLS);
        } else {
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) {
                    sb.append("；");
                }
                sb.append(lines.get(i));
            }
        }
        log.info("TradeRoutePlanner execute: " + sb);
        return sb.toString();
    }

    private void fillChannel(MarketAPI market, RoutePlan plan, PlannerConfig cfg,
                             CampaignFleetAPI fleet, boolean black, List<String> lines,
                             int fuelGoal, int supplyGoal) {
        String subId = black ? Submarkets.SUBMARKET_BLACK : Submarkets.SUBMARKET_OPEN;
        SubmarketAPI sub = market.getSubmarket(subId);
        if (sub == null || sub.getPlugin() == null) {
            return;
        }
        sub.getPlugin().updateCargoPrePlayerInteraction();
        CargoAPI player = fleet.getCargo();
        CargoAPI shelf = sub.getCargo();
        CargoAPI sold = Global.getFactory().createCargo(true);
        CargoAPI bought = Global.getFactory().createCargo(true);
        String tag = UiText.channel(Boolean.valueOf(black));

        for (TradeAction action : plan.sellsAtStop(stopIndex)) {
            if (action.sellOnBlack(wantBlack) != black) {
                continue;
            }
            int did = sellOne(player, shelf, market, action, black, cfg, sold);
            lines.add(UiText.sellFill(action.getName(), tag, action.getQuantity(), did));
        }
        int fuelNeed = restockQty(player, Commodities.FUEL, fuelGoal);
        if (fuelNeed > 0) {
            TradeAction fuel = new TradeAction(Commodities.FUEL, UiText.COMMODITY_FUEL, fuelNeed, 0f, 0f, 0f);
            int did = buyOne(player, shelf, market, fuel, black, cfg, bought);
            if (did > 0) {
                lines.add(UiText.buyFuelFill(tag, did));
            }
        }
        for (TradeAction action : plan.buysAtStop(stopIndex)) {
            if (action.buyOnBlack(wantBlack) != black) {
                continue;
            }
            int did = buyOne(player, shelf, market, action, black, cfg, bought);
            lines.add(UiText.buyFill(action.getName(), tag, action.getQuantity(), did));
        }
        int supplyNeed = restockQty(player, Commodities.SUPPLIES, supplyGoal);
        if (supplyNeed > 0) {
            TradeAction supplies = new TradeAction(Commodities.SUPPLIES, UiText.COMMODITY_SUPPLIES, supplyNeed, 1f, 0f, 0f);
            int did = buyOne(player, shelf, market, supplies, black, cfg, bought);
            if (did > 0) {
                lines.add(UiText.buySupplyFill(did));
            }
        }

        reportTransaction(market, sub, sold, bought);
    }

    private static int sellOne(CargoAPI player, CargoAPI shelf, MarketAPI market, TradeAction action,
                               boolean black, PlannerConfig cfg, CargoAPI sold) {
        String id = action.getCommodityId();
        int planQty = Math.max(0, action.getQuantity());
        int qty = LogisticsReserve.maxSellQty(player, id, planQty, cfg, Global.getSector().getPlayerFleet());
        if (qty <= 0) {
            return 0;
        }
        float revenue = PriceQuoter.quoteSell(market, id, qty, black, cfg);
        takeFromPlayer(player, id, qty);
        shelf.addCommodity(id, qty);
        sold.addCommodity(id, qty);
        player.getCredits().add(revenue);
        return qty;
    }

    private static int buyOne(CargoAPI player, CargoAPI shelf, MarketAPI market, TradeAction action,
                              boolean black, PlannerConfig cfg, CargoAPI bought) {
        String id = action.getCommodityId();
        int planQty = Math.max(0, action.getQuantity());
        int shelfQty = Math.max(0, (int) shelf.getCommodityQuantity(id));
        int space = spaceFor(player, id);
        int cap = Math.min(planQty, Math.min(shelfQty, space));
        int qty = maxAffordable(market, id, cap, player.getCredits().get(), black, cfg);
        if (qty <= 0) {
            return 0;
        }
        float cost = PriceQuoter.quoteBuy(market, id, qty, black, cfg);
        if (cost > player.getCredits().get() + 0.01f) {
            return 0;
        }
        shelf.removeCommodity(id, qty);
        giveToPlayer(player, id, qty);
        bought.addCommodity(id, qty);
        player.getCredits().subtract(cost);
        return qty;
    }

    private static int maxAffordable(MarketAPI market, String id, int cap, float cash,
                                     boolean black, PlannerConfig cfg) {
        if (cap <= 0 || cash <= 0f) {
            return 0;
        }
        if (PriceQuoter.quoteBuy(market, id, cap, black, cfg) <= cash) {
            return cap;
        }
        int lo = 0;
        int hi = cap;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (PriceQuoter.quoteBuy(market, id, mid, black, cfg) <= cash) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    private static int restockQty(CargoAPI cargo, String id, int floor) {
        if (floor <= 0) {
            return 0;
        }
        return Math.max(0, floor - held(cargo, id));
    }

    private static int held(CargoAPI cargo, String id) {
        if (Commodities.FUEL.equals(id)) {
            return (int) cargo.getFuel();
        }
        return (int) cargo.getCommodityQuantity(id);
    }

    private static int spaceFor(CargoAPI cargo, String id) {
        if (Commodities.FUEL.equals(id)) {
            return (int) cargo.getFreeFuelSpace();
        }
        float unit = 1f;
        try {
            CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(id);
            if (spec != null && spec.getCargoSpace() > 0.0001f) {
                unit = spec.getCargoSpace();
            }
        } catch (Exception ignored) {
        }
        float left = cargo.getSpaceLeft();
        if (!Commodities.SUPPLIES.equals(id)) {
            PlannerConfig cfg = PlannerConfig.load();
            int floor = LogisticsReserve.supplyFloorLive(cfg, Global.getSector().getPlayerFleet());
            float shortfall = Math.max(0f, floor - cargo.getSupplies());
            left -= shortfall * LogisticsReserve.supplyCargoSpace();
        }
        if (left <= 0.01f || unit <= 0.0001f) {
            return 0;
        }
        return (int) (left / unit);
    }

    private static void takeFromPlayer(CargoAPI cargo, String id, int qty) {
        if (Commodities.FUEL.equals(id)) {
            cargo.removeFuel(qty);
        } else {
            cargo.removeCommodity(id, qty);
        }
    }

    private static void giveToPlayer(CargoAPI cargo, String id, int qty) {
        if (Commodities.FUEL.equals(id)) {
            cargo.addFuel(qty);
        } else {
            cargo.addCommodity(id, qty);
        }
    }

    /**
     * Shelf cargo is already moved. Shortage/surplus live on {@code CommodityOnMarketAPI}
     * and are updated by the submarket plugin ({@code addTradeMod*} /
     * {@code doShortageCountering}), not by the sector listener broadcast.
     */
    private void reportTransaction(MarketAPI market, SubmarketAPI sub, CargoAPI sold, CargoAPI bought) {
        if (market == null || sub == null || (cargoEmpty(sold) && cargoEmpty(bought))) {
            return;
        }
        CoreUITradeMode mode = tradeMode == null ? CoreUITradeMode.OPEN : tradeMode;
        PlayerMarketTransaction tx = new PlayerMarketTransaction(market, sub, mode);
        tx.setSold(sold == null ? Global.getFactory().createCargo(true) : sold);
        tx.setBought(bought == null ? Global.getFactory().createCargo(true) : bought);
        log.info("TradeRoutePlanner economy before: " + economyLine(market, sold, bought, sub));
        try {
            if (sub.getPlugin() != null) {
                sub.getPlugin().reportPlayerMarketTransaction(tx);
            }
        } catch (Exception e) {
            log.warn("TradeRoutePlanner submarket reportPlayerMarketTransaction failed", e);
        }
        try {
            Global.getSector().reportPlayerMarketTransaction(tx);
        } catch (Exception e) {
            log.warn("TradeRoutePlanner sector reportPlayerMarketTransaction failed", e);
        }
        log.info("TradeRoutePlanner economy after: " + economyLine(market, sold, bought, sub));
    }

    private static boolean cargoEmpty(CargoAPI cargo) {
        return cargo == null || cargo.isEmpty();
    }

    private static String economyLine(MarketAPI market, CargoAPI sold, CargoAPI bought, SubmarketAPI sub) {
        StringBuilder sb = new StringBuilder();
        sb.append("sub=");
        String subId = "-";
        try {
            if (sub != null && sub.getSpec() != null && sub.getSpec().getId() != null) {
                subId = sub.getSpec().getId();
            }
        } catch (Exception ignored) {
        }
        sb.append(subId);
        appendCargoMods(sb, market, sold, " sold");
        appendCargoMods(sb, market, bought, " bought");
        return sb.toString();
    }

    private static void appendCargoMods(StringBuilder sb, MarketAPI market, CargoAPI cargo, String label) {
        if (cargoEmpty(cargo) || market == null) {
            return;
        }
        sb.append(label);
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (stack == null || !stack.isCommodityStack() || stack.getCommodityId() == null) {
                continue;
            }
            String id = stack.getCommodityId();
            CommodityOnMarketAPI com = market.getCommodityData(id);
            sb.append(" ").append(id).append("x").append((int) stack.getSize());
            if (com != null) {
                sb.append(" def=").append(com.getDeficitQuantity());
                sb.append(" xs=").append(com.getExcessQuantity());
            }
        }
    }

    private static InteractionDialogAPI currentDialog() {
        try {
            return Global.getSector().getCampaignUI().getCurrentInteractionDialog();
        } catch (Exception e) {
            return null;
        }
    }

    private static void dismissDialog(InteractionDialogAPI dialog) {
        if (dialog == null) {
            return;
        }
        try {
            dialog.getPlugin().optionSelected(null, OPTION_LEAVE);
            return;
        } catch (Exception ignored) {
        }
        try {
            dialog.dismiss();
        } catch (Exception e) {
            log.info("TradeRoutePlanner dismiss failed");
        }
    }

    private static void dismissQuietly() {
        dismissDialog(currentDialog());
    }

    private void tryAutoStart(TradeRouteIntelPlugin intel) {
        if (intel == null) {
            return;
        }
        if (intel.getLastPlan() == null || intel.getLastPlan().isEmpty() || intel.isTripFinished()) {
            return;
        }
        PlannerConfig cfg = intel.activeConfig();
        if (cfg == null || !cfg.isAutoTradeOnArrival()) {
            return;
        }
        if (Global.getCurrentState() != GameState.CAMPAIGN) {
            return;
        }
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui != null && ui.isShowingMenu()) {
            return;
        }
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || fleet.getBattle() != null) {
            return;
        }
        if (intel.isOnArrivalCooldown()) {
            return;
        }
        SectorEntityToken next = intel.currentWaypointEntity();
        if (next == null || !TradeRouteIntelPlugin.isArrivedAt(fleet, next)) {
            return;
        }
        if (dialogIsForeign(next)) {
            return;
        }
        intel.rememberArrivalAttempt(intel.getLastPlan().getStopMarketId(intel.getNextWaypointIndex()));
        start(intel);
    }

    private static boolean dialogIsDest(SectorEntityToken dest) {
        InteractionDialogAPI dialog = currentDialog();
        return dialog != null && TradeRouteIntelPlugin.sameMarket(dialog.getInteractionTarget(), dest);
    }

    private static boolean dialogIsForeign(SectorEntityToken dest) {
        InteractionDialogAPI dialog = currentDialog();
        return dialog != null && !TradeRouteIntelPlugin.sameMarket(dialog.getInteractionTarget(), dest);
    }

    private void finish(TradeRouteIntelPlugin intel, String message, boolean pause, boolean success) {
        reset();
        if (intel != null && message != null) {
            intel.setLastNavMessage(message);
        }
        addMessage(message);
        boolean chainedNav = false;
        if (success && intel != null && !intel.isTripFinished()) {
            PlannerConfig cfg = intel.activeConfig();
            if (cfg != null && cfg.isAutoNavAfterTrade()) {
                intel.layInNextStop();
                chainedNav = true;
            }
        }
        if (pause && !chainedNav) {
            try {
                Global.getSector().setPaused(true);
            } catch (Exception ignored) {
            }
        }
    }

    private void reset() {
        phase = Phase.IDLE;
        phaseAge = 0f;
        dest = null;
        destName = null;
        stopIndex = 0;
        wantBlack = false;
        tradeMode = null;
        pendingSuccess = false;
    }

    private static void addMessage(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        try {
            Global.getSector().getCampaignUI().addMessage(text, Misc.getHighlightColor());
        } catch (Exception e) {
            log.info("TradeRoutePlanner execute: " + text);
        }
    }
}
