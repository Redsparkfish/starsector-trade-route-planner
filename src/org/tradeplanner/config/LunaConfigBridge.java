package org.tradeplanner.config;

import lunalib.lunaSettings.LunaSettings;

/**
 * Isolated LunaLib reads so {@link PlannerConfig} can load without lunalib on the classpath
 * at runtime. Only referenced after {@code isModEnabled("lunalib")}.
 */
final class LunaConfigBridge {

    private LunaConfigBridge() {
    }

    static void apply(PlannerConfig cfg) {
        Integer maxDays = LunaSettings.getInt(PlannerConfig.MOD_ID, "maxDays");
        if (maxDays != null) {
            cfg.setMaxDays(maxDays);
        }
        Boolean loop = LunaSettings.getBoolean(PlannerConfig.MOD_ID, "isLoop");
        if (loop != null) {
            cfg.setLoop(loop);
        }
        String mode = LunaSettings.getString(PlannerConfig.MOD_ID, "marketMode");
        if (mode != null && !mode.isBlank()) {
            cfg.setMarketMode(MarketMode.fromString(mode, cfg.getMarketMode()));
        }
        String factions = LunaSettings.getString(PlannerConfig.MOD_ID, "blackMarketFactions");
        if (factions != null) {
            cfg.setBlackMarketFactionIds(factions);
        }
        String openFactions = LunaSettings.getString(PlannerConfig.MOD_ID, "openMarketFactions");
        if (openFactions != null) {
            cfg.setOpenMarketFactionIds(openFactions);
        }
        Integer maxStops = LunaSettings.getInt(PlannerConfig.MOD_ID, "maxStops");
        if (maxStops != null) {
            cfg.setMaxStops(maxStops);
        }
        Double margin = LunaSettings.getDouble(PlannerConfig.MOD_ID, "qtySafetyMargin");
        if (margin != null) {
            cfg.setQtySafetyMargin(margin.floatValue());
        }
        Integer budget = LunaSettings.getInt(PlannerConfig.MOD_ID, "computeBudgetMs");
        if (budget != null) {
            cfg.setComputeBudgetMs(budget);
        }
        Boolean excludes = LunaSettings.getBoolean(PlannerConfig.MOD_ID, "assumeApiPriceExcludesTariff");
        if (excludes != null) {
            cfg.setAssumeApiPriceExcludesTariff(excludes);
        }
        Boolean autoTrade = LunaSettings.getBoolean(PlannerConfig.MOD_ID, "autoTradeOnArrival");
        if (autoTrade != null) {
            cfg.setAutoTradeOnArrival(autoTrade);
        }
        Boolean autoNav = LunaSettings.getBoolean(PlannerConfig.MOD_ID, "autoNavAfterTrade");
        if (autoNav != null) {
            cfg.setAutoNavAfterTrade(autoNav);
        }
        Integer supplyDays = LunaSettings.getInt(PlannerConfig.MOD_ID, "reserveSupplyDays");
        if (supplyDays != null) {
            cfg.setReserveSupplyDays(supplyDays);
        }
        Integer fuelDays = LunaSettings.getInt(PlannerConfig.MOD_ID, "reserveFuelDays");
        if (fuelDays != null) {
            cfg.setReserveFuelDays(fuelDays);
        }
        Integer startRange = LunaSettings.getInt(PlannerConfig.MOD_ID, "maxStartRangeLY");
        if (startRange != null) {
            cfg.setMaxStartRangeLy(startRange);
        }
        Double alpha = LunaSettings.getDouble(PlannerConfig.MOD_ID, "posTimeWeight");
        if (alpha != null) {
            cfg.setPosTimeWeight(alpha.floatValue());
        }
    }
}
