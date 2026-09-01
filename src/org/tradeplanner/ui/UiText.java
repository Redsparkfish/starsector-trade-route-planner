package org.tradeplanner.ui;

import com.fs.starfarer.api.util.Misc;
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
    public static final String INTEL_BLURB =
            "咨询式规划：数量与利润均为估计（经济快照 + 关税 + 滑价），成交后会变。"
                    + "天数均为预计，不是到站钟点。不自动交易、不连锁导航、不读取远程货架。";
    public static final String INTEL_SMALL_BLURB =
            "咨询式规划。数量与利润均为估计；不自动交易、不连锁导航。";
    public static final String HUD_NOT_CALCULATED = "跑商规划器，尚未计算";
    public static final String NOT_CALCULATED_HINT =
            "尚未计算。点击\"计算新路线\"搜索净利/天最高的估计路线。";
    public static final String NOT_CALCULATED_SHORT = "尚未计算。数量与利润均为估计。";
    public static final String OPEN_DETAIL_TO_CALCULATE = "打开详情并点击\"计算新路线\"。";
    public static final String NO_PLAN_YET = "尚未计算";

    public static final String BTN_CALCULATE = "计算新路线";
    public static final String BTN_NAV = "设导航（下一站）";
    public static final String BTN_EXECUTE = "本站自动买卖";
    public static final String BTN_ARRIVE = "标记抵达";
    public static final String BTN_CLEAR = "取消规划";
    public static final String BTN_CLEAR_SHORT = "取消";
    public static final String BTN_REFRESH = "刷新快照";
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

    public static final String SECTION_SUMMARY = "汇总（估计）";
    public static final String SECTION_JOB = "下一站作业单";
    public static final String SECTION_ITINERARY = "全程行程（估计）";
    public static final String SECTION_FLEET = "舰队";
    public static final String SECTION_SETTINGS = "规划设置";
    public static final String SECTION_ALPHA = "定位权重 alpha";
    public static final String SECTION_FACTIONS = "势力交易（本存档）";

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
    public static final String TRIP_FINISHED_RECALC = "行程结束。需要新路线请重新计算。";
    public static final String TRIP_FINISHED_PERIOD = "行程结束。";
    public static final String NO_NEXT_STOP = "没有下一站。";
    public static final String NO_STOP_TRADE = "本站无计划买卖。";
    public static final String NO_FLEET_SNAPSHOT = "尚无舰队快照。";
    public static final String HYPERSPACE = "超空间";
    public static final String HERE_MARK = "，当前";
    public static final String SEARCH_TRUNCATED = "，搜索已截断";
    public static final String TRUNCATED_SUFFIX = "（截断）";
    public static final String IN_DOCK_RANGE = "已在停靠范围";
    public static final String INFINITE = "无限";
    public static final String REL_PLAYER = "，玩家";
    public static final String REL_HOSTILE = "，敌对";
    public static final String BUY_PREFIX = "+买";
    public static final String SELL_PREFIX = "-卖";
    public static final String COMMODITY_FUEL = "燃料";
    public static final String COMMODITY_SUPPLIES = "补给";

    public static final String SETTINGS_INTRO =
            "勾选只改本页草稿，不会立刻刷新或写入存档。点\"确认\"后生效并返回规划页；未确认的勾选不影响计算。";
    public static final String SETTINGS_F2_NOTE =
            "时间预算、闭环、起点光年等仍在 F2 或 settings.json。定位权重 alpha 在本页，确认后写入当前存档。";
    public static final String SETTINGS_ALPHA_HELP =
            "搜索按 回路利润 / (回路天数 + alpha x 定位天数) 排序。0 忽略飞到第一站的时间，1 把定位和跑圈同等看待。情报\"净利/天\"按全程天数（定位+回路）计算。";
    public static final String SETTINGS_FACTION_HELP =
            "开市与黑市可分别开关。两边都关则不与该势力交易。两边都开则先黑市，仍有利可图再开市补仓。不根据敌对自动改。";
    public static final String SETTINGS_NO_FACTIONS = "当前没有可列出的经济势力。";
    public static final String SETTINGS_SAVED = "已保存规划设置。请重新计算路线。";
    public static final String ALPHA_0 = "0，忽略定位";
    public static final String ALPHA_1 = "1，定位与回路同等";
    public static final String ALPHA_DEFAULT = "0.5，默认（约两圈）";

    public static final String SNAPSHOT_NOTE = "存档路线是计算时快照，读档后仍保留；成交后数字会变。";
    public static final String ALREADY_HERE_BUY =
            "已在此地。请先按下方清单买入（估计），完成后点\"标记抵达\"，再设下一站导航。途中不要卖这些货。";
    public static final String POSITIONING_EMPTY = "空载前往，到站后再买（估计）。途中无需出货。";
    public static final String EMPTY_ARRIVE_RESTOCK = "本航段空载抵达。请按下方清单补补给、燃料。";
    public static final String EMPTY_RETURN = "空载返回，本站无需买卖。到站后行程结束或再点计算。";
    public static final String CARD_ALREADY_HERE = "    已在此地，先买入再出发。";
    public static final String CARD_POSITIONING = "    空载航行，到站后再买。";
    public static final String CARD_EMPTY_RETURN = "    空载返回，无需买卖。";
    public static final String LOOP_SUMMARY_TITLE = "闭环总结（实际现金）";
    public static final String TRIP_SUMMARY_TITLE = "行程总结（实际现金）";

    public static final String EXEC_ALREADY = "已在本站自动买卖中。";
    public static final String EXEC_NO_ROUTE = "没有可执行的路线，请先计算。";
    public static final String EXEC_NOT_CAMPAIGN = "不在战役层，无法自动买卖。";
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
    public static final String EXEC_NO_MARKET = "无法解析下一站市场。";
    public static final String EXEC_CANT_TRADE_UI = "无法打开交易界面。";
    public static final String EXEC_DOCK_TIMEOUT = "打开停靠对话超时。";
    public static final String EXEC_TRADE_TIMEOUT = "打开交易界面超时。";
    public static final String EXEC_DONE = "本站买卖完成。";
    public static final String EXEC_DONE_PREFIX = "本站买卖完成（估计对照实际）：";
    public static final String EXEC_NO_FILLS = "无成交。";
    public static final String EXEC_LEFT_CAMPAIGN = "离开战役层，已中止自动买卖。";
    public static final String EXEC_BATTLE_ABORT = "战斗开始，已中止自动买卖。";
    public static final String EXEC_MENU_ABORT = "已打开菜单，已中止自动买卖。";
    public static final String EXEC_RECALC_ABORT = "已重新计算，自动买卖已中止。";
    public static final String EXEC_NO_SUBMARKET = "没有可用子市场";
    public static final String EXEC_PORT_BLOCKED = "港务局拒绝交易（巡逻队追踪、敌对或冷却中）。";
    public static final String EXEC_NEED_BLACK = "需要黑市，但该市场没有黑市。";
    public static final String EXEC_SNEAK_OPEN = "当前是暗中停靠，开市不可用；本站规划为开市。";
    public static final String EXEC_NO_BLACK_OR_OPEN = "该势力未启用黑市，且当前开市不可用。";
    public static final String EXEC_NO_OPEN = "该市场没有开市。";

    public static final String NAV_NO_ROUTE = "没有可导航的路线，请先计算。";
    public static final String NAV_NO_WAYPOINT = "没有可推进的航点。";
    public static final String CALC_FAILED = "计算失败。";
    public static final String NO_FEASIBLE = "无可行路线";

    public static final String REASON_NO_MARKETS = "无可交易市场";
    public static final String REASON_NO_CASH = "现金为 0";
    public static final String REASON_NO_CARGO = "货舱已满";
    public static final String REASON_NO_FUEL = "油不够第一跳";
    public static final String REASON_NO_START_IN_RANGE = "光年范围内无可用起点";
    public static final String REASON_NO_PROFIT = "没有利润为正的航段";
    public static final String REASON_TIMEOUT = "计算超时且无可行路线";

    public static final String CONFIG_LINE =
            "时间预算 %s 天，闭环 %s，停数上限 %s，起点 %s LY，alpha %s，到站推进 %s";
    public static final String RESERVE_LINE =
            "补给保留 %s 天，燃料保留 %s 天（超空间日耗折算。0 为不保留）";
    public static final String FACTION_POLICY_LINE =
            "势力交易 %s（本存档，点\"规划设置\"修改；改完请重新计算）";
    public static final String EMPTY_PLAN = "无可行路线：%s。";
    public static final String EMPTY_PLAN_SHORT = "无可行路线：%s";
    public static final String EMPTY_PLAN_HUD = "无可行路线，计算 %s";
    public static final String EXTRA_STOPS = "毛利 %s，%s，额外停靠 %s%s";
    public static final String NEXT_STOP_NAME = "当前下一站：%s";
    public static final String PLANNED_TRAVEL = "规划时预计航行 %s 天（超空间 %s + 星系内 %s）";
    public static final String BUY_AT_ORIGIN = "本航段在 %s 买入，到本站卖出（估计）。请先在出发地买到货再航行。";
    public static final String STOP_CARD = "第 %s 站，%s%s%s";
    public static final String CARD_ETA = "    预计 %s 天";
    public static final String MID_FUEL = "    途中补油 %s（花费 %s）";
    public static final String MID_SUPPLY = "    途中补补给 %s（花费 %s）";
    public static final String SELL_LINE = "    -卖 %s x %s（估计）收入 %s";
    public static final String BUY_LINE = "    +买 %s x %s（估计）成本 %s";
    public static final String PLAN_TOTALS = "预计净利 %s，预计 %s 天，净利/天 %s";
    public static final String POSITIONING_NOTE =
            "其中定位（舰队->第一站）预计 %s 天，已计入净利/天。";
    public static final String THIS_COMPUTE = "本次计算 %s";
    public static final String TRIP_SUMMARY = "%s：%s 天，净利润 %s，净利/天 %s";
    public static final String PREVIEW_LINE = "  %s %s x %s（估计）";
    public static final String PREVIEW_MORE = "  ...其余 %s 项见详情。";
    public static final String PREVIEW_SUPPLY = "  +买 补给（保留） x %s（估计）";
    public static final String PREVIEW_FUEL = "  +买 燃料（航行，保留） x %s（估计）";
    public static final String FLEET_LINE =
            "现金 %s，货舱剩余 %s，补给 %s（保留 %s），燃料 %s（上限 %s，保留 %s），%s";
    public static final String HUD_CPD = "净利/天 %s，计算 %s";
    public static final String NEXT_STOP_ARRIVED = "下一站 %s，%s";
    public static final String NEXT_STOP_ETA = "下一站 %s，%s，预计 %s 天";
    public static final String ALPHA_DRAFT = "当前草稿 alpha = %s（约合预期跑 %s 圈后再重算）";
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
        return "无可行路线：" + emptyReason(plan == null ? null : plan.getEmptyReason());
    }

    public static String emptyPlanLineHud(RoutePlan plan) {
        return "无可行路线，计算 " + TradeRouteCustomPanel.formatComputeTime(plan);
    }

    public static String configLine(PlannerConfig cfg) {
        return String.format("时间预算 %s 天，闭环 %s，停数上限 %s，起点 %s LY，alpha %s，到站推进 %s",
                String.valueOf(cfg.getMaxDays()),
                yesNo(cfg.isLoop()),
                String.valueOf(cfg.getMaxStops()),
                String.valueOf(cfg.getMaxStartRangeLy()),
                String.format("%.2f", cfg.getPosTimeWeight()),
                onOff(cfg.isAutoAdvanceOnArrival()));
    }

    public static String reserveLine(PlannerConfig cfg) {
        return String.format("补给保留 %s 天，燃料保留 %s 天（超空间日耗折算。0 为不保留）",
                String.valueOf(cfg.getReserveSupplyDays()),
                String.valueOf(cfg.getReserveFuelDays()));
    }

    public static String factionPolicyLine(String policy) {
        return "势力交易 " + policy + "（本存档，点\"规划设置\"修改；改完请重新计算）";
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
        String truncated = plan.isTruncated() ? SEARCH_TRUNCATED : "";
        return String.format("毛利 %s，%s，额外停靠 %s%s",
                Misc.getDGSCredits(plan.getGrossProfit()),
                loopKind(plan.isLoop()),
                String.valueOf(plan.getExtraStops()),
                truncated);
    }

    public static String currentNextStop(String name) {
        return "当前下一站：" + name;
    }

    public static String plannedTravel(String total, String hyper, String inSystem) {
        return String.format("规划时预计航行 %s 天（超空间 %s + 星系内 %s）", total, hyper, inSystem);
    }

    public static String buyAtOrigin(String fromName) {
        return "本航段在 " + fromName + " 买入，到本站卖出（估计）。请先在出发地买到货再航行。";
    }

    public static String stopCard(int number, String name, String system, boolean here) {
        String sys = system == null || system.isEmpty() ? "" : "（" + system + "）";
        String mark = here ? HERE_MARK : "";
        return "第 " + number + " 站，" + name + sys + mark;
    }

    public static String cardEta(String days) {
        return "    预计 " + days + " 天";
    }

    public static String midFuel(String qty, String cost) {
        return "    途中补油 " + qty + "（花费 " + cost + "）";
    }

    public static String midSupply(String qty, String cost) {
        return "    途中补补给 " + qty + "（花费 " + cost + "）";
    }

    public static String sellLine(String name, String qty, String revenue) {
        return "    -卖 " + name + " x " + qty + "（估计）收入 " + revenue;
    }

    public static String buyLine(String name, String qty, String cost) {
        return "    +买 " + name + " x " + qty + "（估计）成本 " + cost;
    }

    public static String planTotals(String net, String days, String cpd) {
        return "预计净利 " + net + "，预计 " + days + " 天，净利/天 " + cpd;
    }

    public static String positioningNote(String days) {
        return "其中定位（舰队->第一站）预计 " + days + " 天，已计入净利/天。";
    }

    public static String thisCompute(String detail) {
        return "本次计算 " + detail;
    }

    public static String computeMs(long ms, boolean truncated) {
        String text = ms + " ms";
        return truncated ? text + TRUNCATED_SUFFIX : text;
    }

    public static String computeMsDetail(long displayMs, long snapshotMs, long searchMs, boolean truncated) {
        String core = displayMs + " ms（快照 " + snapshotMs + " + 搜索 " + searchMs + "）";
        return truncated ? core + TRUNCATED_SUFFIX : core;
    }

    public static String tripSummary(boolean loop, String days, String net, String cpd) {
        String title = loop ? LOOP_SUMMARY_TITLE : TRIP_SUMMARY_TITLE;
        return title + "：" + days + " 天，净利润 " + net + "，净利/天 " + cpd;
    }

    public static String tripSummaryLine(float days, String net, String cpd) {
        return String.format("实际 %.1f 天，净利润 %s，净利/天 %s。", days, net, cpd);
    }

    public static String previewLine(boolean buy, String name, String qty) {
        String prefix = buy ? "+买" : "-卖";
        return "  " + prefix + " " + name + " x " + qty + "（估计）";
    }

    public static String previewMore(int rest) {
        return "  ...其余 " + rest + " 项见详情。";
    }

    public static String previewSupply(String qty) {
        return "  +买 补给（保留） x " + qty + "（估计）";
    }

    public static String previewFuel(String qty) {
        return "  +买 燃料（航行，保留） x " + qty + "（估计）";
    }

    public static String fleetLine(String credits, String cargo, String supplies, String supplyFloor,
                                   String fuel, String fuelMax, String fuelFloor, String loc) {
        return "现金 " + credits + "，货舱剩余 " + cargo + "，补给 " + supplies
                + "（保留 " + supplyFloor + "），燃料 " + fuel + "（上限 " + fuelMax
                + "，保留 " + fuelFloor + "），" + loc;
    }

    public static String hudCpd(String cpd, String compute) {
        return "净利/天 " + cpd + "，计算 " + compute;
    }

    public static String nextStopArrived(String name, String dist) {
        return "下一站 " + name + "，" + dist;
    }

    public static String nextStopEta(String name, String dist, String days) {
        return "下一站 " + name + "，" + dist + "，预计 " + days + " 天";
    }

    public static String ly(float distLY) {
        return String.format("%.1f 光年", distLY);
    }

    public static String alphaDraft(float draft, String loops) {
        return String.format("当前草稿 alpha = %s（约合预期跑 %s 圈后再重算）",
                String.format("%.2f", draft), loops);
    }

    public static String alphaChoice(float v) {
        if (v <= 0.001f) {
            return ALPHA_0;
        }
        if (Math.abs(v - 1f) < 0.001f) {
            return ALPHA_1;
        }
        if (Math.abs(v - 0.5f) < 0.001f) {
            return ALPHA_DEFAULT;
        }
        return String.format("%.2f", v);
    }

    public static String factionNoTrade(String name, String hint) {
        return name + hint + "  " + NO_TRADE;
    }

    public static String factionWithMode(String name, String hint, String mode) {
        return name + hint + "，" + mode;
    }

    public static String computeDone(RoutePlan plan) {
        if (plan == null) {
            return CALC_FAILED;
        }
        String ms = plan.getDisplayMs() + " ms";
        if (plan.isEmpty()) {
            String reason = plan.getEmptyReason() == null ? NO_FEASIBLE : emptyReason(plan.getEmptyReason());
            return reason + "，耗时 " + ms;
        }
        if (plan.isTruncated()) {
            return "计算完成，耗时 " + ms + "（搜索已截断）";
        }
        return "计算完成，耗时 " + ms;
    }

    public static String unresolvedEntity(String name) {
        return "无法解析下一站实体：" + (name == null ? "?" : name);
    }

    public static String alreadyAt(String name) {
        return "已在 " + name + "。请按作业单完成买卖后点\"标记抵达\"，再设下一站导航。";
    }

    public static String courseSetMessage(String name) {
        return "已设航线：" + name;
    }

    public static String courseSetIntel(String name) {
        return "已设航线：" + name + "（仅下一站）";
    }

    public static String navFailed(String err) {
        return "设导航失败：" + err;
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
        return "已抵达 " + arrived + "。下一站：" + next + "（点\"设导航\"前往下一站）";
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
