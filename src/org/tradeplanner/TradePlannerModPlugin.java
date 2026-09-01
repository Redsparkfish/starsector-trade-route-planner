package org.tradeplanner;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import org.apache.log4j.Level;
import org.tradeplanner.config.PlannerConfig;
import org.tradeplanner.exec.StopExecutorScript;
import org.tradeplanner.ui.CampaignHudScript;
import org.tradeplanner.ui.TradeRouteIntelPlugin;

/**
 * Mod lifecycle. Registers the planner intel entry once per campaign.
 */
public class TradePlannerModPlugin extends BaseModPlugin {

    @Override
    public void onApplicationLoad() {
        if (!PlannerConfig.isLunaEnabled()) {
            Global.getLogger(TradePlannerModPlugin.class).log(
                    Level.WARN,
                    "TradeRoutePlanner: LunaLib not enabled. Using data/config/settings.json. "
                            + "Install LunaLib for in-game F2 sliders."
            );
        } else {
            try {
                Class.forName("org.tradeplanner.config.LunaConfigListener")
                        .getMethod("register")
                        .invoke(null);
            } catch (Exception e) {
                Global.getLogger(TradePlannerModPlugin.class).log(
                        Level.WARN, "TradeRoutePlanner: failed to register LunaSettings listener", e);
            }
        }
        Global.getLogger(TradePlannerModPlugin.class).info("TradeRoutePlanner loaded (" + PlannerConfig.MOD_ID + ")");
    }

    @Override
    public void onGameLoad(boolean newGame) {
        IntelManagerAPI intel = Global.getSector().getIntelManager();
        if (!intel.hasIntelOfClass(TradeRouteIntelPlugin.class)) {
            intel.addIntel(new TradeRouteIntelPlugin(), false);
        }
        StopExecutorScript.register();
        CampaignHudScript.register();
    }

    @Override
    public void onDevModeF8Reload() {
        onApplicationLoad();
        StopExecutorScript.register();
        CampaignHudScript.register();
    }
}
