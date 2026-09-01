package org.tradeplanner.config;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Runtime planner settings. LunaLib (F2) overrides {@code data/config/settings.json}
 * when the lunalib mod is enabled. Search knobs are consumed by {@code RouteOptimizationEngine}.
 */
public final class PlannerConfig {

    public static final String MOD_ID = "traderouteplanner";
    public static final String LUNA_ID = "lunalib";
    public static final String SETTINGS_FILE = "data/config/settings.json";
    public static final String SETTINGS_OBJECT = "TradeRoutePlanner";

    public static final int DEFAULT_MAX_DAYS = 30;
    public static final int MIN_DAYS = 5;
    public static final int MAX_DAYS = 60;
    public static final boolean DEFAULT_LOOP = true;
    public static final String DEFAULT_BLACK_MARKET_FACTIONS = "pirates,luddic_path";
    public static final String DEFAULT_OPEN_MARKET_FACTIONS = "";
    public static final int DEFAULT_MAX_STOPS = 4;
    public static final float DEFAULT_QTY_SAFETY_MARGIN = 0.9f;
    public static final int DEFAULT_COMPUTE_BUDGET_MS = 2000;
    public static final int MIN_COMPUTE_BUDGET_MS = 50;
    public static final int MAX_COMPUTE_BUDGET_MS = 10000;
    public static final boolean DEFAULT_API_PRICE_EXCLUDES_TARIFF = true;
    public static final boolean DEFAULT_AUTO_ADVANCE_ON_ARRIVAL = true;
    public static final boolean DEFAULT_HUD_ENABLED = true;
    public static final int DEFAULT_RESERVE_SUPPLY_DAYS = 100;
    public static final int DEFAULT_RESERVE_FUEL_DAYS = 100;
    public static final int MIN_RESERVE_DAYS = 0;
    public static final int MAX_RESERVE_DAYS = 999;
    public static final int DEFAULT_MAX_START_RANGE_LY = 10;
    public static final int MIN_START_RANGE_LY = 1;
    public static final int MAX_START_RANGE_LY = 200;
    public static final float DEFAULT_POS_TIME_WEIGHT = 0.5f;
    public static final float MIN_POS_TIME_WEIGHT = 0f;
    public static final float MAX_POS_TIME_WEIGHT = 1f;

    private static final Logger log = Global.getLogger(PlannerConfig.class);

    private int maxDays = DEFAULT_MAX_DAYS;
    private boolean loop = DEFAULT_LOOP;
    /** Only used to seed old saves that still have marketMode in json/Luna. */
    private MarketMode marketMode = MarketMode.LEGAL_ONLY;
    private Set<String> blackMarketFactionIds = parseFactionList(DEFAULT_BLACK_MARKET_FACTIONS);
    private Set<String> openMarketFactionIds = parseFactionList(DEFAULT_OPEN_MARKET_FACTIONS);
    private FactionTradeSettings campaignTrade;
    private int maxStops = DEFAULT_MAX_STOPS;
    private float qtySafetyMargin = DEFAULT_QTY_SAFETY_MARGIN;
    private int computeBudgetMs = DEFAULT_COMPUTE_BUDGET_MS;
    private boolean assumeApiPriceExcludesTariff = DEFAULT_API_PRICE_EXCLUDES_TARIFF;
    private boolean autoAdvanceOnArrival = DEFAULT_AUTO_ADVANCE_ON_ARRIVAL;
    private boolean hudEnabled = DEFAULT_HUD_ENABLED;
    private int reserveSupplyDays = DEFAULT_RESERVE_SUPPLY_DAYS;
    private int reserveFuelDays = DEFAULT_RESERVE_FUEL_DAYS;
    private int maxStartRangeLy = DEFAULT_MAX_START_RANGE_LY;
    private float posTimeWeight = DEFAULT_POS_TIME_WEIGHT;

    public static PlannerConfig load() {
        PlannerConfig cfg = new PlannerConfig();
        cfg.loadFromJsonFile();
        if (isLunaEnabled()) {
            LunaConfigBridge.apply(cfg);
        }
        cfg.clamp();
        return cfg;
    }

    /** Overlay per-save faction toggles and α. Does not read the intel plugin. */
    public void applyCampaign(FactionTradeSettings trade, Float posWeight) {
        this.campaignTrade = trade;
        if (posWeight != null) {
            setPosTimeWeight(posWeight);
        }
    }

    public static boolean isLunaEnabled() {
        try {
            return Global.getSettings().getModManager().isModEnabled(LUNA_ID);
        } catch (Exception e) {
            return false;
        }
    }

    private void loadFromJsonFile() {
        try {
            JSONObject root = Global.getSettings().loadJSON(SETTINGS_FILE, MOD_ID);
            JSONObject obj = root.optJSONObject(SETTINGS_OBJECT);
            if (obj == null) {
                obj = root;
            }
            maxDays = obj.optInt("maxDays", DEFAULT_MAX_DAYS);
            loop = obj.optBoolean("isLoop", DEFAULT_LOOP);
            marketMode = MarketMode.fromString(obj.optString("marketMode", MarketMode.LEGAL_ONLY.name()), MarketMode.LEGAL_ONLY);
            blackMarketFactionIds = readFactionList(obj, "blackMarketFactions", DEFAULT_BLACK_MARKET_FACTIONS);
            openMarketFactionIds = readFactionList(obj, "openMarketFactions", DEFAULT_OPEN_MARKET_FACTIONS);
            maxStops = obj.optInt("maxStops", DEFAULT_MAX_STOPS);
            qtySafetyMargin = (float) obj.optDouble("qtySafetyMargin", DEFAULT_QTY_SAFETY_MARGIN);
            computeBudgetMs = obj.optInt("computeBudgetMs", DEFAULT_COMPUTE_BUDGET_MS);
            assumeApiPriceExcludesTariff = obj.optBoolean("assumeApiPriceExcludesTariff", DEFAULT_API_PRICE_EXCLUDES_TARIFF);
            autoAdvanceOnArrival = obj.optBoolean("autoAdvanceOnArrival", DEFAULT_AUTO_ADVANCE_ON_ARRIVAL);
            hudEnabled = obj.optBoolean("hudEnabled", DEFAULT_HUD_ENABLED);
            reserveSupplyDays = obj.optInt("reserveSupplyDays", DEFAULT_RESERVE_SUPPLY_DAYS);
            reserveFuelDays = obj.optInt("reserveFuelDays", DEFAULT_RESERVE_FUEL_DAYS);
            maxStartRangeLy = obj.optInt("maxStartRangeLY", DEFAULT_MAX_START_RANGE_LY);
            posTimeWeight = (float) obj.optDouble("posTimeWeight", DEFAULT_POS_TIME_WEIGHT);
        } catch (Exception e) {
            log.warn("Failed to load " + SETTINGS_FILE + ", using built-in defaults", e);
        }
    }

    void clamp() {
        maxDays = clampInt(maxDays, MIN_DAYS, MAX_DAYS);
        maxStops = clampInt(maxStops, 2, 8);
        qtySafetyMargin = clampFloat(qtySafetyMargin, 0.1f, 1f);
        computeBudgetMs = clampInt(computeBudgetMs, MIN_COMPUTE_BUDGET_MS, MAX_COMPUTE_BUDGET_MS);
        if (marketMode == null) {
            marketMode = MarketMode.LEGAL_ONLY;
        }
        if (blackMarketFactionIds == null) {
            blackMarketFactionIds = parseFactionList(DEFAULT_BLACK_MARKET_FACTIONS);
        }
        if (openMarketFactionIds == null) {
            openMarketFactionIds = parseFactionList(DEFAULT_OPEN_MARKET_FACTIONS);
        }
        reserveSupplyDays = clampInt(reserveSupplyDays, MIN_RESERVE_DAYS, MAX_RESERVE_DAYS);
        reserveFuelDays = clampInt(reserveFuelDays, MIN_RESERVE_DAYS, MAX_RESERVE_DAYS);
        maxStartRangeLy = clampInt(maxStartRangeLy, MIN_START_RANGE_LY, MAX_START_RANGE_LY);
        posTimeWeight = clampFloat(posTimeWeight, MIN_POS_TIME_WEIGHT, MAX_POS_TIME_WEIGHT);
    }

    void setMaxDays(int maxDays) {
        this.maxDays = maxDays;
    }

    void setLoop(boolean loop) {
        this.loop = loop;
    }

    void setMarketMode(MarketMode marketMode) {
        this.marketMode = marketMode;
    }

    void setBlackMarketFactionIds(String raw) {
        this.blackMarketFactionIds = parseFactionList(raw);
    }

    void setOpenMarketFactionIds(String raw) {
        this.openMarketFactionIds = parseFactionList(raw);
    }

    void setMaxStops(int maxStops) {
        this.maxStops = maxStops;
    }

    void setQtySafetyMargin(float qtySafetyMargin) {
        this.qtySafetyMargin = qtySafetyMargin;
    }

    void setComputeBudgetMs(int computeBudgetMs) {
        this.computeBudgetMs = computeBudgetMs;
    }

    void setAssumeApiPriceExcludesTariff(boolean assumeApiPriceExcludesTariff) {
        this.assumeApiPriceExcludesTariff = assumeApiPriceExcludesTariff;
    }

    void setAutoAdvanceOnArrival(boolean autoAdvanceOnArrival) {
        this.autoAdvanceOnArrival = autoAdvanceOnArrival;
    }

    void setHudEnabled(boolean hudEnabled) {
        this.hudEnabled = hudEnabled;
    }

    void setReserveSupplyDays(int reserveSupplyDays) {
        this.reserveSupplyDays = reserveSupplyDays;
    }

    void setReserveFuelDays(int reserveFuelDays) {
        this.reserveFuelDays = reserveFuelDays;
    }

    void setMaxStartRangeLy(int maxStartRangeLy) {
        this.maxStartRangeLy = maxStartRangeLy;
    }

    void setPosTimeWeight(float posTimeWeight) {
        this.posTimeWeight = clampFloat(posTimeWeight, MIN_POS_TIME_WEIGHT, MAX_POS_TIME_WEIGHT);
    }

    public int getMaxDays() {
        return maxDays;
    }

    public boolean isLoop() {
        return loop;
    }

    /** Old global mode, only for seeding campaign faction toggles. */
    public MarketMode getMarketMode() {
        return marketMode;
    }

    public Set<String> getBlackMarketFactionIds() {
        return Collections.unmodifiableSet(blackMarketFactionIds);
    }

    public Set<String> getOpenMarketFactionIds() {
        return Collections.unmodifiableSet(openMarketFactionIds);
    }

    public boolean isDefaultBlackMarketFaction(String factionId) {
        return factionId != null && blackMarketFactionIds.contains(factionId);
    }

    public boolean isDefaultOpenMarketFaction(String factionId) {
        return factionId != null && openMarketFactionIds.contains(factionId);
    }

    public boolean allowOpenMarket(String factionId) {
        if (campaignTrade != null) {
            return campaignTrade.allowOpen(factionId, this);
        }
        return FactionTradeSettings.defaultPref(factionId, this).open;
    }

    public boolean allowBlackMarket(String factionId) {
        if (campaignTrade != null) {
            return campaignTrade.allowBlack(factionId, this);
        }
        return FactionTradeSettings.defaultPref(factionId, this).black;
    }

    public FactionTradeSettings getCampaignTrade() {
        return campaignTrade;
    }

    /** Empty when the default black-market list is empty. */
    public String getBlackMarketFactionsDisplay() {
        if (blackMarketFactionIds == null || blackMarketFactionIds.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String id : blackMarketFactionIds) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(factionDisplayName(id));
        }
        return sb.toString();
    }

    public static String factionDisplayName(String factionId) {
        if (factionId == null) {
            return "?";
        }
        try {
            com.fs.starfarer.api.campaign.FactionAPI faction = Global.getSector().getFaction(factionId);
            if (faction != null && faction.getDisplayName() != null && !faction.getDisplayName().isEmpty()) {
                return faction.getDisplayName();
            }
        } catch (Exception ignored) {
        }
        if ("pirates".equals(factionId)) {
            return "海盗";
        }
        if ("luddic_path".equals(factionId)) {
            return "卢德左径";
        }
        return factionId;
    }

    public int getMaxStops() {
        return maxStops;
    }

    public float getQtySafetyMargin() {
        return qtySafetyMargin;
    }

    public int getComputeBudgetMs() {
        return computeBudgetMs;
    }

    public boolean assumeApiPriceExcludesTariff() {
        return assumeApiPriceExcludesTariff;
    }

    public boolean isAutoAdvanceOnArrival() {
        return autoAdvanceOnArrival;
    }

    public boolean isHudEnabled() {
        return hudEnabled;
    }

    /** Days of supply consumption kept out of trade. 0 disables. */
    public int getReserveSupplyDays() {
        return reserveSupplyDays;
    }

    /**
     * Days of hyperspace fuel (at current burn) kept out of trade. 0 disables.
     * Converted with {@code Misc.getFuelPerDay}; not a vanilla "daily fuel" cost.
     */
    public int getReserveFuelDays() {
        return reserveFuelDays;
    }

    /** Hyperspace LY from fleet to first market A. Same-system starts are always in range. */
    public int getMaxStartRangeLy() {
        return maxStartRangeLy;
    }

    /**
     * α in search rank {@code P / (T_loop + α T_pos)}. 0 ignores positioning; 1 treats it
     * like loop time. UI credits/day is still pure {@code P / T_loop}.
     */
    public float getPosTimeWeight() {
        return posTimeWeight;
    }

    public static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static Set<String> parseFactionList(String raw) {
        if (raw == null || raw.isBlank()) {
            return new LinkedHashSet<>();
        }
        List<String> tokens = new ArrayList<>();
        for (String part : raw.split("[,;]")) {
            tokens.add(part);
        }
        return parseFactionList(tokens);
    }

    public static Set<String> parseFactionList(Collection<String> raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw == null) {
            return out;
        }
        for (String part : raw) {
            if (part == null) {
                continue;
            }
            String id = part.trim();
            if (!id.isEmpty()) {
                out.add(id);
            }
        }
        return out;
    }

    private Set<String> readFactionList(JSONObject obj, String key, String fallback) {
        if (obj == null || !obj.has(key)) {
            return parseFactionList(fallback);
        }
        JSONArray arr = obj.optJSONArray(key);
        if (arr != null) {
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                ids.add(arr.optString(i, ""));
            }
            return parseFactionList(ids);
        }
        return parseFactionList(obj.optString(key, fallback));
    }
}
