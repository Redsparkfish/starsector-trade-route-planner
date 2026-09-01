package org.tradeplanner.ui;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import org.tradeplanner.config.PlannerConfig;
import org.tradeplanner.model.RoutePlan;

import java.util.List;

/**
 * Injects the compact job sheet onto the campaign travel map and tears it down
 * for dialogs, core UI tabs, and when the HUD setting is off.
 */
public final class CampaignHudScript implements EveryFrameScript {

    private CustomPanelAPI panel;
    private UIPanelAPI attachedTo;
    private String lastSignature;
    private boolean loggedAttach;

    public static void register() {
        try {
            if (findExisting() == null) {
                Global.getSector().addTransientScript(new CampaignHudScript());
            }
        } catch (Exception ignored) {
            // Title screen / no sector yet.
        }
    }

    private static CampaignHudScript findExisting() {
        try {
            List<EveryFrameScript> scripts = Global.getSector().getTransientScripts();
            if (scripts == null) {
                return null;
            }
            for (EveryFrameScript script : scripts) {
                if (script instanceof CampaignHudScript) {
                    return (CampaignHudScript) script;
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
            tickHud();
        } catch (Exception e) {
            Global.getLogger(CampaignHudScript.class).warn(
                    "TradeRoutePlanner HUD tick failed", e);
            detach();
        }
    }

    private void tickHud() {
        if (!shouldShow()) {
            detach();
            return;
        }
        TradeRouteIntelPlugin intel = TradeRouteIntelPlugin.getInstance();
        if (intel == null) {
            detach();
            return;
        }
        UIPanelAPI screen = CampaignUiAccess.getScreenPanel();
        if (screen == null) {
            detach();
            return;
        }
        String signature = signature(intel);
        boolean needRebuild = panel == null
                || attachedTo != screen
                || !signature.equals(lastSignature)
                || !CampaignUiAccess.isStillAttached(screen, panel);
        if (needRebuild) {
            detach();
            attach(screen, intel, signature);
        } else if (panel != null) {
            CampaignHudPanel.applyPosition(panel.getPosition(), panel.getPosition().getHeight(), screen);
        }
    }

    private void attach(UIPanelAPI screen, TradeRouteIntelPlugin intel, String signature) {
        try {
            panel = CampaignHudPanel.create(intel);
            PositionAPI pos = screen.addComponent(panel);
            float height = panel.getPosition().getHeight();
            CampaignHudPanel.applyPosition(pos, height, screen);
            try {
                screen.bringComponentToTop(panel);
            } catch (Exception ignored) {
                // Optional in older UI implementations.
            }
            CampaignHudPanel.applyPosition(pos, height, screen);
            attachedTo = screen;
            lastSignature = signature;
            if (!loggedAttach) {
                loggedAttach = true;
                Global.getLogger(CampaignHudScript.class).info(
                        "TradeRoutePlanner HUD attached "
                                + (int) pos.getWidth() + "x" + (int) pos.getHeight()
                                + " at " + (int) pos.getX() + "," + (int) pos.getY()
                                + " parent=" + screen.getClass().getName());
            }
        } catch (Exception e) {
            Global.getLogger(CampaignHudScript.class).warn(
                    "TradeRoutePlanner HUD: failed to attach panel", e);
            panel = null;
            attachedTo = null;
            lastSignature = null;
        }
    }

    private void detach() {
        if (panel != null && attachedTo != null) {
            try {
                attachedTo.removeComponent(panel);
            } catch (Exception ignored) {
                // Screen already replaced (dialog, tab, load).
            }
        }
        panel = null;
        attachedTo = null;
        lastSignature = null;
    }

    private static boolean shouldShow() {
        try {
            if (Global.getCurrentState() != GameState.CAMPAIGN) {
                return false;
            }
            PlannerConfig cfg = PlannerConfig.load();
            if (cfg == null || !cfg.isHudEnabled()) {
                return false;
            }
            CampaignUIAPI ui = Global.getSector().getCampaignUI();
            if (ui == null || ui.isHideUI() || ui.isShowingDialog() || ui.isShowingMenu()) {
                return false;
            }
            if (ui.getCurrentInteractionDialog() != null) {
                return false;
            }
            CoreUITabId tab = ui.getCurrentCoreTab();
            // MAP is the campaign travel / sector map layer (including in-system).
            // Hide only for Intel / Fleet / Refit / Cargo and other covering tabs.
            if (tab != null && tab != CoreUITabId.MAP) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String signature(TradeRouteIntelPlugin intel) {
        RoutePlan plan = intel.getLastPlan();
        String planPart = "none";
        if (plan != null) {
            if (plan.isEmpty()) {
                planPart = "empty:" + plan.getEmptyReason();
            } else {
                planPart = System.identityHashCode(plan)
                        + ":" + intel.getNextWaypointIndex()
                        + ":" + (int) plan.getNetProfit()
                        + ":" + (int) plan.getCreditsPerDay();
            }
        }
        return planPart
                + "|" + intel.isHudCollapsed()
                + "|" + intel.isTripFinished()
                + "|" + intel.hasTripSummary()
                + "|" + (int) intel.getTripActualNet()
                + "|" + intel.isStopExecutorActive()
                + "|" + String.valueOf(intel.getLastNavMessage())
                + "|" + NextStopReadout.of(intel).signature;
    }
}
