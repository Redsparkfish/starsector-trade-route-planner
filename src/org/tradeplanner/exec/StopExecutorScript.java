package org.tradeplanner.exec;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

import java.util.List;

/**
 * Advances {@link StopExecutor} independently of the campaign HUD, so auto-trade
 * still ticks when the job-sheet overlay is disabled.
 */
public final class StopExecutorScript implements EveryFrameScript {

    public static void register() {
        try {
            if (findExisting() == null) {
                Global.getSector().addTransientScript(new StopExecutorScript());
            }
        } catch (Exception ignored) {
            // Title screen / no sector yet.
        }
    }

    private static StopExecutorScript findExisting() {
        try {
            List<EveryFrameScript> scripts = Global.getSector().getTransientScripts();
            if (scripts == null) {
                return null;
            }
            for (EveryFrameScript script : scripts) {
                if (script instanceof StopExecutorScript) {
                    return (StopExecutorScript) script;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        try {
            StopExecutor.get().advance(amount);
        } catch (Exception e) {
            Global.getLogger(StopExecutorScript.class).warn(
                    "TradeRoutePlanner StopExecutor tick failed", e);
        }
    }
}
