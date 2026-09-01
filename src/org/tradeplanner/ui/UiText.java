package org.tradeplanner.ui;

import org.tradeplanner.config.FactionTradeSettings;
import org.tradeplanner.config.PlannerConfig;
import org.tradeplanner.model.RoutePlan;
import org.tradeplanner.model.TradeAction;

import java.util.Collection;

/**
 * Player-facing copy. Layout stays in the panels; this class is the one place to change wording.
 */
public final class UiText {

    public static final String TITLE = "跑商规划器";
    public static final String HUD_NOT_CALCULATED = "跑商规划器，尚未计算";
    public static final String NOT_CALCULATED_HINT = "尚未计算。点击\"计算新路线\"。";
    public static final String NOT_CALCULATED_SHORT = "尚未计算。";
    public static final String OPEN_DETAIL_TO_CALCULATE = "打开详情并点击\"计算新路线\"。";
    public static final String NO_PLAN_YET = "尚未计算";

    public static final String BTN_CALCULATE = "计算新路线";
    public static final String BTN_NAV = "导航到下一站";
    public static final String BTN_EXECUTE = "本站自动买卖";
    public static final String BTN_ARRIVE = "标记抵达";
    public static final String BTN_CLEAR = "取消规划";
    public static final String BTN_CLEAR_SHORT = "取消";
    public static final String BTN_REFRESH = "刷新舰队";
    public static final String BTN_SETTINGS = "规划设置";
    public static final String BTN_DETAIL = "打开详情";
    public static final String BTN_COLLAPSE = "折叠";
    public static final String BTN_EXPAND = "展开";
    public static final String BTN_OK = "确认";
    public static final String BTN_CANCEL = "取消";
    public static final String BTN_RESET = "恢复默认";
    public static final String BTN_CALC_SHORT = "计算";
    public static final String BTN_NAV_SHORT = "导航";
    public static final String BTN_EXECUTE_SHORT = "买卖";
    public static final String BTN_DETAIL_SHORT = "详情";

    public static final String SECTION_SUMMARY = "路线概要";
    public static final String SECTION_JOB = "下一站作业单";
    public static final String SECTION_ITINERARY = "全程行程";
    public static final String SECTION_FLEET = "舰队";
    public static final String SECTION_SETTINGS = "规划设置";
    public static final String SECTION_ALPHA = "定位权重 alpha";
    public static final String SECTION_FACTIONS = "势力交易（本存档）";
    public static final String JOB_QTY_NOTE = "数量为估算，到站后按实际库存成交。";

    public static final String YES = "是";
    public static final String NO = "否";
    public static final String ON = "开";
    public static final String OFF = "关";
    public static final String LOOP = "闭环";
    public static final String OPEN_LOOP = "开环";
    public static final String OPEN_MARKET = "开市";
    public static final String BLACK_MARKET = "黑市";
    public static final String OPEN_AND_BLACK = "开市+黑市";
    public static final String OPEN_ONLY = "仅开市";
    public static final String BLACK_ONLY = "仅黑市";
    public static final String NO_TRADE = "不交易";
    public static final String NONE_FACTIONS = "无";
    public static final String UNKNOWN = "未知";
    public static final String TRIP_FINISHED = "行程结束";
    public static final String TRIP_FINISHED_RECALC = "行程结束。需要新路线请再点计算新路线。";
    public static final String TRIP_FINISHED_PERIOD = "行程结束。";
    public static final String NO_NEXT_STOP = "没有下一站。";
    public static final String NO_STOP_TRADE = "本站无计划买卖。";
    public static final String NO_FLEET_SNAPSHOT = "尚无舰队状态。";
    public static final String HYPERSPACE = "超空间";
    public static final String HERE_MARK = "，当前";
    public static final String SEARCH_TRUNCATED = "（搜索时间已到）";
    public static final String TRUNCATED_SUFFIX = "（搜索时间已到）";
    public static final String IN_DOCK_RANGE = "已在停靠范围";
    public static final String REL_PLAYER = "，玩家";
    public static final String REL_HOSTILE = "，敌对";
    public static final String BUY_PREFIX = "+买";
    public static final String SELL_PREFIX = "-卖";
    public static final String COMMODITY_FUEL = "燃料";
    public static final String COMMODITY_SUPPLIES = "补给";

    public static final String SETTINGS_INTRO =
            "改完点（确认）保存并返回。本页改当前存档的开市、黑市和定位权重。";
    public static final String SETTINGS_F2_NOTE =
            "路线时间、闭环、停靠上限、燃料和补给保留请到 F2（需 LunaLib）修改。";
    public static final String SETTINGS_ALPHA_HELP =
            "前往第一站也要花时间。0 更看重路线本身的收益；0.5 为默认，兼顾前往第一站的距离和之后的收益；"
                    + "1 完整计入前往第一站的时间。普通情况下保持默认即可。";
    public static final String SETTINGS_FACTION_HELP =
            "开市（合法市场）与黑市可分别开关。两边都关则不与该势力交易。"
                    + "两边都开则先黑市；如果还有剩余货舱，并且通过开市继续交易仍然有利可图，也可能继续使用开市。";
    public static final String SETTINGS_NO_FACTIONS = "当前没有可列出的经济势力。";
    public static final String SETTINGS_SAVED = "已保存规划设置。请重新计算路线。";
    public static final String ALPHA_0 = "0，更看重路线本身的收益";
    public static final String ALPHA_025 = "0.25，略微考虑前往第一站";
    public static final String ALPHA_DEFAULT = "0.5，默认，兼顾前往第一站的距离";
    public static final String ALPHA_075 = "0.75，较多计入前往第一站";
    public static final String ALPHA_1 = "1，完整计入前往第一站的时间";

    public static final String ALREADY_HERE_BUY = "已在此地。请先按下方清单买入，再点\"标记抵达\"。";
    public static final String POSITIONING_EMPTY = "空载前往，到站后再买。";
    public static final String EMPTY_ARRIVE_RESTOCK = "空载抵达。请按下方清单补补给、燃料。";
    public static final String EMPTY_RETURN = "空载返回，本站无需买卖。";
    public static final String CARD_ALREADY_HERE = "    已在此地，先买入再出发。";
    public static final String CARD_POSITIONING = "    空载航行，到站后再买。";
    public static final String CARD_EMPTY_RETURN = "    空载返回，无需买卖。";
    public static final String LOOP_SUMMARY_TITLE = "闭环总结";
    public static final String TRIP_SUMMARY_TITLE = "行程总结";

    public static final String EXEC_ALREADY = "已在本站自动买卖中。";
    public static final String EXEC_NO_ROUTE = "没有可执行的路线，请先计算。";
    public static final String EXEC_NOT_CAMPAIGN = "当前不在大地图，无法自动买卖。";
    public static final String EXEC_OTHER_DIALOG = "已有其他对话或菜单，无法开始自动买卖。";
    public static final String EXEC_NO_FLEET = "没有玩家舰队。";
    public static final String EXEC_IN_BATTLE = "战斗中，无法自动买卖。";
    public static final String EXEC_NOT_ARRIVED = "尚未抵达目标地点，请抵达后再点击\"本站自动买卖\"。";
    public static final String EXEC_NO_WORK = "本站无需买卖。";
    public static final String EXEC_RUNNING = "本站自动买卖进行中。";
    public static final String EXEC_CANT_CLOSE_UI = "无法关闭情报、舰队界面。";
    public static final String EXEC_CANT_DOCK = "无法打开停靠对话。";
    public static final String EXEC_OTHER_DOCK = "已有其他对话，无法自动停靠。";
    public static final String EXEC_DIALOG_CLOSED = "停靠对话已关闭。";
    public static final String EXEC_NO_MARKET = "找不到下一站市场。";
    public static final String EXEC_CANT_TRADE_UI = "无法打开交易界面。";
    public static final String EXEC_DOCK_TIMEOUT = "打开停靠对话超时。";
    public static final String EXEC_TRADE_TIMEOUT = "打开交易界面超时。";
    public static final String EXEC_DONE = "本站买卖完成。";
    public static final String EXEC_DONE_PREFIX = "本站买卖完成（计划对照实际）：";
    public static final String EXEC_NO_FILLS = "无成交。";
    public static final String EXEC_LEFT_CAMPAIGN = "已离开大地图，自动买卖已中止。";
    public static final String EXEC_BATTLE_ABORT = "战斗开始，已中止自动买卖。";
    public static final String EXEC_MENU_ABORT = "已打开菜单，已中止自动买卖。";
    public static final String EXEC_RECALC_ABORT = "已重新计算，自动买卖已中止。";
    public static final String EXEC_NO_SUBMARKET = "没有可交易的市场";
    public static final String EXEC_PORT_BLOCKED = "港务局拒绝交易（巡逻队追踪、敌对或冷却中）。";
    public static final String EXEC_NEED_BLACK = "需要黑市，但该市场没有黑市。";
    public static final String EXEC_SNEAK_OPEN = "当前是暗中停靠，开市不可用；本站规划为开市。";
    public static final String EXEC_NO_BLACK_OR_OPEN = "该势力未启用黑市，且当前开市不可用。";
    public static final String EXEC_NO_OPEN = "该市场没有开市。";

    public static final String NAV_NO_ROUTE = "没有可导航的路线，请先计算。";
    public static final String NAV_NO_WAYPOINT = "没有下一站。";
    public static final String CALC_FAILED = "计算失败。";
    public static final String NO_FEASIBLE = "无可行路线";

    public static final String REASON_NO_MARKETS = "无可交易市场";
    public static final String REASON_NO_CASH = "现金为 0";
    public static final String REASON_NO_CARGO = "货舱已满";
    public static final String REASON_NO_FUEL = "燃料不够飞到第一站";
    public static final String REASON_NO_START_IN_RANGE = "第一站范围内没有可用市场";
    public static final String REASON_NO_PROFIT = "没有利润为正的航段";
    public static final String REASON_TIMEOUT = "计算超时且无可行路线";

    public static final String CONFIG_LINE =
            "路线时间上限 %s 天，闭环 %s，额外停靠 %s，第一站范围 %s 光年，定位权重 %s，到站自动切换 %s";
    public static final String RESERVE_LINE =
            "补给保留 %s 天，燃料保留 %s 天（0 为不保留）";
    public static final String SETTINGS_SUMMARY = "%s，路线时间上限 %s 天，%s";
    public static final String FACTION_POLICY_LINE = "势力交易 %s。改完请重新计算。";
    public static final String PROFIT_COMPARE_NOTE = "未计入航行消耗的燃料和补给，适合比较路线。";
    public static final String SEARCH_TIME_UP = "搜索时间已到，这是当前找到的较好路线。";
    public static final String EMPTY_PLAN = "无可行路线：%s。";
    public static final String EMPTY_PLAN_SHORT = "无可行路线：%s";
    public static final String EMPTY_PLAN_HUD = "无可行路线";
    public static final String EXTRA_STOPS = "%s，额外停靠 %s";
    public static final String NEXT_STOP_NAME = "当前下一站：%s";
    public static final String PLANNED_TRAVEL = "预计航行 %s 天（超空间 %s + 星系内 %s）";
    public static final String BUY_AT_ORIGIN = "本航段在 %s 买入，到本站卖出。请先在出发地买到货再航行。";
    public static final String STOP_CARD = "第 %s 站，%s%s%s";
    public static final String CARD_ETA = "    预计 %s 天";
    public static final String MID_FUEL = "    途中补燃料 %s（花费 %s）";
    public static final String MID_SUPPLY = "    途中补补给 %s（花费 %s）";
    public static final String SELL_LINE = "    -卖 %s x %s 收入 %s";
    public static final String BUY_LINE = "    +买 %s x %s 成本 %s";
    public static final String PLAN_TOTALS = "预计总利润 %s，预计 %s 天，利润/天 %s";
    public static final String POSITIONING_NOTE =
            "其中前往第一站预计 %s 天，已计入预计天数和利润/天。";
    public static final String THIS_COMPUTE = "搜索用时约 %s";
    public static final String COMPUTE_DONE = "计算完成。";
    public static final String TRIP_SUMMARY = "%s：%s 天，实际总利润 %s，利润/天 %s";
    public static final String PREVIEW_LINE = "  %s %s x %s";
    public static final String PREVIEW_MORE = "  ...其余 %s 项见详情。";
    public static final String PREVIEW_SUPPLY = "  +买 补给（保留） x %s";
    public static final String PREVIEW_FUEL = "  +买 燃料（航行，保留） x %s";
    public static final String FLEET_LINE =
            "现金 %s，货舱剩余 %s，补给 %s（保留 %s），燃料 %s（上限 %s，保留 %s），%s";
    public static final String HUD_CPD = "利润/天 %s";
    public static final String NEXT_STOP_ARRIVED = "下一站 %s，%s";
    public static final String NEXT_STOP_ETA = "下一站 %s，%s，预计 %s 天";
    public static final String FACTION_NO_TRADE = "%s%s  不交易";
    public static final String FACTION_WITH_MODE = "%s%s，%s";
    public static final String POLICY_ALL_BOTH = "全部：开市+黑市";
    public static final String POLICY_CUSTOM = "已自定义（见规划设置）";
    public static final String POLICY_REST_OPEN = "其余：仅开市";

    private UiText() {
    }

    public static String yesNo(boolean value) {
        return value ? YES : NO;
    }

    public static String onOff(boolean value) {
        return value ? ON : OFF;
    }

    public static String loopKind(boolean loop) {
        return loop ? LOOP : OPEN_LOOP;
    }

    public static String channel(Boolean black) {
        if (black == null) {
            return "";
        }
        return black.booleanValue() ? BLACK_MARKET : OPEN_MARKET;
    }

    public static String channelName(TradeAction action, boolean buy) {
        if (action == null) {
            return "";
        }
        String label = buy ? channel(action.getBuyOnBlack()) : channel(action.getSellOnBlack());
        if (label == null || label.isEmpty()) {
            return action.getName();
        }
        return action.getName() + "（" + label + "）";
    }

    public static String emptyReason(String raw) {
        if (raw == null || raw.isEmpty()) {
            return UNKNOWN;
        }
        if (RoutePlan.REASON_NO_MARKETS.equals(raw)) {
            return REASON_NO_MARKETS;
        }
        if (RoutePlan.REASON_NO_CASH.equals(raw)) {
            return REASON_NO_CASH;
        }
        if (RoutePlan.REASON_NO_CARGO.equals(raw)) {
            return REASON_NO_CARGO;
        }
        if (RoutePlan.REASON_NO_FUEL.equals(raw)) {
            return REASON_NO_FUEL;
        }
        if (RoutePlan.REASON_NO_START_IN_RANGE.equals(raw)) {
            return REASON_NO_START_IN_RANGE;
        }
        if (RoutePlan.REASON_NO_PROFIT.equals(raw)) {
            return REASON_NO_PROFIT;
        }
        if (RoutePlan.REASON_TIMEOUT.equals(raw)) {
            return REASON_TIMEOUT;
        }
        return raw;
    }

    public static String emptyPlanLine(RoutePlan plan) {
        return String.format(EMPTY_PLAN, emptyReason(plan == null ? null : plan.getEmptyReason()));
    }

    public static String emptyPlanLineHud(RoutePlan plan) {
        if (plan == null) {
            return EMPTY_PLAN_HUD;
        }
        return String.format(EMPTY_PLAN_SHORT, emptyReason(plan.getEmptyReason()));
    }

    public static String configLine(PlannerConfig cfg) {
        return String.format(CONFIG_LINE,
                String.valueOf(cfg.getMaxDays()),
                onOff(cfg.isLoop()),
                String.valueOf(cfg.getMaxStops()),
                String.valueOf(cfg.getMaxStartRangeLy()),
                String.format("%.2f", cfg.getPosTimeWeight()),
                onOff(cfg.isAutoAdvanceOnArrival()));
    }

    public static String reserveLine(PlannerConfig cfg) {
        return String.format(RESERVE_LINE,
                String.valueOf(cfg.getReserveSupplyDays()),
                String.valueOf(cfg.getReserveFuelDays()));
    }

    public static String factionPolicyLine(String policy) {
        return String.format(FACTION_POLICY_LINE, policy);
    }

    public static String settingsSummary(PlannerConfig cfg, String policy) {
        if (cfg == null) {
            return policy == null ? "" : policy;
        }
        return String.format(SETTINGS_SUMMARY,
                loopKind(cfg.isLoop()),
                String.valueOf(cfg.getMaxDays()),
                policy == null ? "" : policy);
    }

    public static String factionPolicy(FactionTradeSettings settings, PlannerConfig cfg,
                                       Collection<String> factionIds) {
        if (settings == null) {
            return defaultFactionPolicy(cfg);
        }
        FactionTradeSettings.PolicySummary summary = settings.classify(cfg, factionIds);
        if (summary.kind == FactionTradeSettings.PolicyKind.DEFAULT) {
            return defaultFactionPolicy(cfg);
        }
        if (summary.kind == FactionTradeSettings.PolicyKind.ALL_OPEN_BLACK) {
            return POLICY_ALL_BOTH;
        }
        if (summary.kind == FactionTradeSettings.PolicyKind.CUSTOM_SKIP) {
            return "已自定义（" + summary.skippedCount + " 个势力不交易）";
        }
        return POLICY_CUSTOM;
    }

    public static String defaultFactionPolicy(PlannerConfig cfg) {
        if (cfg == null) {
            return POLICY_REST_OPEN;
        }
        String black = cfg.getBlackMarketFactionsDisplay();
        if (black == null || black.isEmpty()) {
            return OPEN_ONLY;
        }
        return black + "：仅黑市，其余：仅开市";
    }

    public static String factionMode(boolean open, boolean black) {
        if (!open && !black) {
            return NO_TRADE;
        }
        if (open && black) {
            return OPEN_AND_BLACK;
        }
        return black ? BLACK_ONLY : OPEN_ONLY;
    }

    public static String extraStopsLine(RoutePlan plan) {
        return String.format(EXTRA_STOPS,
                loopKind(plan.isLoop()),
                String.valueOf(plan.getExtraStops()));
    }

    public static String currentNextStop(String name) {
        return String.format(NEXT_STOP_NAME, name);
    }

    public static String plannedTravel(String total, String hyper, String inSystem) {
        return String.format(PLANNED_TRAVEL, total, hyper, inSystem);
    }

    public static String buyAtOrigin(String fromName) {
        return String.format(BUY_AT_ORIGIN, fromName);
    }

    public static String stopCard(int number, String name, String system, boolean here) {
        String sys = system == null || system.isEmpty() ? "" : "（" + system + "）";
        String mark = here ? HERE_MARK : "";
        return String.format(STOP_CARD, String.valueOf(number), name, sys, mark);
    }

    public static String cardEta(String days) {
        return String.format(CARD_ETA, days);
    }

    public static String midFuel(String qty, String cost) {
        return String.format(MID_FUEL, qty, cost);
    }

    public static String midSupply(String qty, String cost) {
        return String.format(MID_SUPPLY, qty, cost);
    }

    public static String sellLine(String name, String qty, String revenue) {
        return String.format(SELL_LINE, name, qty, revenue);
    }

    public static String buyLine(String name, String qty, String cost) {
        return String.format(BUY_LINE, name, qty, cost);
    }

    public static String planTotals(String net, String days, String cpd) {
        return String.format(PLAN_TOTALS, net, days, cpd);
    }

    public static String positioningNote(String days) {
        return String.format(POSITIONING_NOTE, days);
    }

    public static String thisCompute(String detail) {
        return String.format(THIS_COMPUTE, detail);
    }

    public static String formatSeconds(long ms) {
        if (ms < 50L) {
            return "不足 0.1 秒";
        }
        float sec = ms / 1000f;
        if (sec < 9.95f) {
            return String.format("%.1f 秒", sec);
        }
        return Math.round(sec) + " 秒";
    }

    public static String searchDuration(long ms, boolean truncated) {
        String time = formatSeconds(ms);
        return truncated ? time + TRUNCATED_SUFFIX : time;
    }

    public static String computeMs(long ms, boolean truncated) {
        return searchDuration(ms, truncated);
    }

    public static String computeMsDetail(long displayMs, long snapshotMs, long searchMs, boolean truncated) {
        return searchDuration(displayMs, truncated);
    }

    public static String tripSummary(boolean loop, String days, String net, String cpd) {
        String title = loop ? LOOP_SUMMARY_TITLE : TRIP_SUMMARY_TITLE;
        return String.format(TRIP_SUMMARY, title, days, net, cpd);
    }

    public static String tripSummaryLine(float days, String net, String cpd) {
        return String.format("实际 %.1f 天，实际总利润 %s，利润/天 %s。", days, net, cpd);
    }

    public static String previewLine(boolean buy, String name, String qty) {
        String prefix = buy ? BUY_PREFIX : SELL_PREFIX;
        return String.format(PREVIEW_LINE, prefix, name, qty);
    }

    public static String previewMore(int rest) {
        return String.format(PREVIEW_MORE, String.valueOf(rest));
    }

    public static String previewSupply(String qty) {
        return String.format(PREVIEW_SUPPLY, qty);
    }

    public static String previewFuel(String qty) {
        return String.format(PREVIEW_FUEL, qty);
    }

    public static String fleetLine(String credits, String cargo, String supplies, String supplyFloor,
                                   String fuel, String fuelMax, String fuelFloor, String loc) {
        return String.format(FLEET_LINE, credits, cargo, supplies, supplyFloor, fuel, fuelMax, fuelFloor, loc);
    }

    public static String hudCpd(String cpd, String compute) {
        return String.format(HUD_CPD, cpd);
    }

    public static String nextStopArrived(String name, String dist) {
        return String.format(NEXT_STOP_ARRIVED, name, dist);
    }

    public static String nextStopEta(String name, String dist, String days) {
        return String.format(NEXT_STOP_ETA, name, dist, days);
    }

    public static String ly(float distLY) {
        return String.format("%.1f 光年", distLY);
    }

    public static String alphaChoice(float v) {
        if (v <= 0.001f) {
            return ALPHA_0;
        }
        if (Math.abs(v - 0.25f) < 0.001f) {
            return ALPHA_025;
        }
        if (Math.abs(v - 1f) < 0.001f) {
            return ALPHA_1;
        }
        if (Math.abs(v - 0.5f) < 0.001f) {
            return ALPHA_DEFAULT;
        }
        if (Math.abs(v - 0.75f) < 0.001f) {
            return ALPHA_075;
        }
        return String.format("%.2f", v);
    }

    public static String factionNoTrade(String name, String hint) {
        return String.format(FACTION_NO_TRADE, name, hint);
    }

    public static String factionWithMode(String name, String hint, String mode) {
        return String.format(FACTION_WITH_MODE, name, hint, mode);
    }

    public static String computeDone(RoutePlan plan) {
        if (plan == null) {
            return CALC_FAILED;
        }
        if (plan.isEmpty()) {
            return plan.getEmptyReason() == null ? NO_FEASIBLE : emptyReason(plan.getEmptyReason());
        }
        if (plan.isTruncated()) {
            return SEARCH_TIME_UP;
        }
        return COMPUTE_DONE;
    }

    public static String unresolvedEntity(String name) {
        return "找不到下一站：" + (name == null ? "?" : name);
    }

    public static String alreadyAt(String name) {
        return "已在 " + name + "。请按作业单完成买卖后点\"标记抵达\"，再导航到下一站。";
    }

    public static String courseSetMessage(String name) {
        return "已导航到：" + name;
    }

    public static String courseSetIntel(String name) {
        return "已导航到：" + name;
    }

    public static String navFailed(String err) {
        return "导航失败：" + err;
    }

    public static String openIntelFailed(String err) {
        return "无法打开情报：" + err;
    }

    public static String arrivedBuyThenMark(String arrived) {
        return "已抵达 " + arrived + "。请按作业单买入后点\"标记抵达\"。";
    }

    public static String arrivedFinished(String arrived) {
        return "已抵达 " + arrived + "。" + TRIP_FINISHED_PERIOD;
    }

    public static String arrivedNext(String arrived, String next) {
        return "已抵达 " + arrived + "。下一站：" + next + "（点\"导航到下一站\"继续）";
    }

    public static String execDockFailed(String err) {
        return "打开停靠对话失败：" + err;
    }

    public static String execFillFailed(String err) {
        return "自动买卖失败：" + err;
    }

    public static String noWorkNext(String next) {
        return EXEC_NO_WORK + "下一站：" + next;
    }

    public static String sellFill(String name, String tag, int planned, int did) {
        return "-卖 " + name + "（" + tag + "）计划 " + planned + "，实际 " + did;
    }

    public static String buyFill(String name, String tag, int planned, int did) {
        return "+买 " + name + "（" + tag + "）计划 " + planned + "，实际 " + did;
    }

    public static String buyFuelFill(String tag, int did) {
        return "+买 燃料（" + tag + "） " + did;
    }

    public static String buySupplyFill(int did) {
        return "+买 补给（保留） " + did;
    }
}
