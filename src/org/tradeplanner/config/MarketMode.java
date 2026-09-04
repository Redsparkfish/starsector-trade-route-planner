package org.tradeplanner.config;

/**
 * Open-market vs black-market planning. Planner never toggles the transponder.
 *
 * <p>{@link #LEGAL_ONLY}: open market everywhere except factions on the black-market
 * allowlist (default pirates, luddic_path, independent).
 * {@link #ALLOW_BLACK_MARKET}: use black market at every market that has one.
 */
public enum MarketMode {
    LEGAL_ONLY,
    ALLOW_BLACK_MARKET;

    public static MarketMode fromString(String raw, MarketMode fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return MarketMode.valueOf(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
