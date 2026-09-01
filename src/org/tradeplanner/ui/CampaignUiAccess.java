package org.tradeplanner.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * Resolves the campaign screen panel without compiling against obfuscated internals.
 * Must not call {@code java.lang.reflect.Method} directly — the script classloader
 * throws {@code SecurityException}. Uses {@link ScriptSafeReflect} (MagicLib's
 * MethodHandles approach) instead of depending on MagicLib.
 */
final class CampaignUiAccess {

    private static final Logger log = Global.getLogger(CampaignUiAccess.class);
    private static boolean loggedFailure;

    private CampaignUiAccess() {
    }

    static UIPanelAPI getScreenPanel() {
        Object panel = fromAppDriver();
        if (panel instanceof UIPanelAPI) {
            return (UIPanelAPI) panel;
        }
        panel = fromCampaignUi();
        if (panel instanceof UIPanelAPI) {
            return (UIPanelAPI) panel;
        }
        return null;
    }

    /**
     * True if {@code child} is still in {@code parent}'s child list.
     * Campaign UI rebuilds in-system widgets without replacing the screen panel object.
     */
    static boolean isStillAttached(UIPanelAPI parent, UIComponentAPI child) {
        if (parent == null || child == null) {
            return false;
        }
        List<?> children = childrenOf(parent);
        if (children != null) {
            return children.contains(child);
        }
        Object reported = ScriptSafeReflect.invoke(child, "getParent");
        if (reported != null) {
            return reported == parent;
        }
        return true;
    }

    static boolean isTopChild(UIPanelAPI parent, UIComponentAPI child) {
        List<?> children = childrenOf(parent);
        if (children == null || children.isEmpty() || child == null) {
            return false;
        }
        return children.get(children.size() - 1) == child;
    }

    private static List<?> childrenOf(UIPanelAPI parent) {
        Object list = ScriptSafeReflect.invoke(parent, "getChildrenCopy");
        if (list instanceof List) {
            return (List<?>) list;
        }
        return null;
    }

    private static Object fromAppDriver() {
        if (!ScriptSafeReflect.isReady()) {
            logOnce("MethodHandles", new IllegalStateException(
                    "script-safe reflection failed to initialize"));
            return null;
        }
        try {
            Class<?> driverClass = Class.forName("com.fs.state.AppDriver", false,
                    Global.class.getClassLoader());
            Object driver = ScriptSafeReflect.invokeStatic(driverClass, "getInstance");
            if (driver == null) {
                return null;
            }
            Object state = ScriptSafeReflect.invoke(driver, "getCurrentState");
            if (state == null) {
                state = ScriptSafeReflect.field(driver, "currentState");
            }
            if (state == null) {
                return null;
            }
            Object screen = ScriptSafeReflect.invoke(state, "getScreenPanel");
            if (screen != null) {
                return screen;
            }
            return ScriptSafeReflect.field(state, "screenPanel");
        } catch (Exception e) {
            logOnce("AppDriver screenPanel", e);
            return null;
        }
    }

    private static Object fromCampaignUi() {
        try {
            Object ui = Global.getSector().getCampaignUI();
            if (ui == null) {
                return null;
            }
            Object screen = ScriptSafeReflect.invoke(ui, "getScreenPanel");
            if (screen != null) {
                return screen;
            }
            return ScriptSafeReflect.field(ui, "screenPanel");
        } catch (Exception e) {
            logOnce("CampaignUI screenPanel", e);
            return null;
        }
    }

    private static void logOnce(String where, Exception e) {
        if (loggedFailure) {
            return;
        }
        loggedFailure = true;
        log.warn("TradeRoutePlanner HUD: cannot resolve " + where, e);
    }
}
