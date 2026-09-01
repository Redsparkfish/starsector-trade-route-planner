package org.tradeplanner.config;

import com.fs.starfarer.api.Global;
import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;
import org.apache.log4j.Level;

/**
 * Reloads planner settings when the player saves LunaLib (F3) options.
 * Isolated so {@link org.tradeplanner.TradePlannerModPlugin} does not hard-link LunaLib.
 */
public final class LunaConfigListener implements LunaSettingsListener {

    public static void register() {
        try {
            LunaSettings.addSettingsListener(new LunaConfigListener());
        } catch (Exception e) {
            Global.getLogger(LunaConfigListener.class).log(
                    Level.WARN, "TradeRoutePlanner: failed to register LunaSettings listener", e);
        }
    }

    @Override
    public void settingsChanged(String modId) {
        if (!PlannerConfig.MOD_ID.equals(modId)) {
            return;
        }
        // Next intel refresh calls PlannerConfig.load() which re-reads LunaSettings.
    }
}
