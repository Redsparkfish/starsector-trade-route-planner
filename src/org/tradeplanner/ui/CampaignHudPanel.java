package org.tradeplanner.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.opengl.GL11;
import org.tradeplanner.exec.StopExecutor;
import org.tradeplanner.model.RoutePlan;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Compact campaign-map job sheet. Buttons poll {@link ButtonAPI#isChecked()} because
 * campaign HUD has no {@code IntelUIAPI.buttonPressConfirmed}. Do not also override
 * {@code buttonPressed} — a long calculate would expire the 250ms debounce and run twice.
 */
final class CampaignHudPanel {

    static final float WIDTH = 312f;
    static final float HEIGHT_COLLAPSED = 140f;
    static final float HEIGHT_EXPANDED = 280f;
    private static final float BUILD_HEIGHT = 720f;
    private static final float BOTTOM_PAD = 6f;
    private static final float MIN_HEIGHT = 72f;
    /** Gap from the right edge so the sheet sits immediately left of the minimap. */
    static final float PAD_RIGHT = 248f;
    static final float PAD_BOTTOM = 12f;
    static final float TITLE_H = 22f;

    static final String BTN_CALC = "hud_calc";
    static final String BTN_NAV = "hud_nav";
    static final String BTN_EXECUTE = "hud_execute";
    static final String BTN_DETAIL = "hud_detail";
    static final String BTN_TOGGLE = "hud_toggle";
    static final String BTN_CLEAR = "hud_clear";

    private CampaignHudPanel() {
    }

    static CustomPanelAPI create(TradeRouteIntelPlugin intel) {
        boolean collapsed = intel.isHudCollapsed();
        HudPlugin plugin = new HudPlugin(intel);
        CustomPanelAPI panel = Global.getSettings().createCustom(WIDTH, BUILD_HEIGHT, plugin);
        TooltipMakerAPI info = panel.createUIElement(WIDTH, BUILD_HEIGHT, false);
        info.setParaFontVictor14();
        info.setButtonFontVictor10();
        if (collapsed) {
            buildCollapsed(info, plugin, intel, WIDTH);
        } else {
            buildExpanded(info, plugin, intel, WIDTH);
        }
        panel.addUIElement(info).inTL(0f, 0f);
        panel.updateUIElementSizeAndMakeItProcessInput(info);
        float measured = info.getHeightSoFar();
        float height;
        if (measured > 20f && measured < BUILD_HEIGHT - 20f) {
            height = measured + BOTTOM_PAD;
        } else {
            height = collapsed ? HEIGHT_COLLAPSED : HEIGHT_EXPANDED;
        }
        if (height < MIN_HEIGHT) {
            height = MIN_HEIGHT;
        }
        panel.getPosition().setSize(WIDTH, height);
        return panel;
    }

    static void applyPosition(PositionAPI pos, float height, UIPanelAPI screen) {
        pos.setSize(WIDTH, height);
        // Parent-relative: bottom-right of this panel is PAD_RIGHT from the screen's
        // right edge, i.e. immediately left of the vanilla minimap. Do not use saved
        // coordinates — old campaigns may still contain unused hudX/hudY in XML.
        try {
            pos.inBR(PAD_RIGHT, PAD_BOTTOM);
        } catch (Exception ignored) {
            float sw = screenWidth();
            float sh = screenHeight();
            float left = Math.max(0f, sw - PAD_RIGHT - WIDTH);
            float bottom = Math.max(0f, Math.min(sh - height, PAD_BOTTOM));
            pos.setLocation(left, bottom);
        }
    }

    private static float screenWidth() {
        try {
            return Global.getSettings().getScreenWidth();
        } catch (Exception e) {
            return 1920f;
        }
    }

    private static float screenHeight() {
        try {
            return Global.getSettings().getScreenHeight();
        } catch (Exception e) {
            return 1080f;
        }
    }

    private static void buildCollapsed(TooltipMakerAPI info, HudPlugin plugin,
                                       TradeRouteIntelPlugin intel, float width) {
        Color h = Misc.getHighlightColor();
        RoutePlan plan = intel.getLastPlan();
        float inner = width - 8f;
        info.addSectionHeading(UiText.TITLE, Alignment.MID, 2f);
        if (plan != null && !plan.isEmpty()) {
            if (intel.isTripFinished()) {
                info.addPara(UiText.TRIP_FINISHED, 4f, h);
                TradeRouteCustomPanel.appendTripSummary(info, intel, 2f);
            } else {
                info.addPara(UiText.HUD_CPD, 3f, h,
                        Misc.getDGSCredits(plan.getCreditsPerDay()),
                        TradeRouteCustomPanel.formatComputeTime(plan));
                NextStopReadout.of(intel).append(info, 2f);
            }
        } else if (plan != null && plan.isEmpty()) {
            info.addPara(UiText.EMPTY_PLAN_HUD, 4f, h,
                    TradeRouteCustomPanel.formatComputeTime(plan));
        } else {
            info.addPara(UiText.HUD_NOT_CALCULATED, 4f, h);
        }
        addButtonRow(info, plugin, intel, inner, true);
    }

    private static void buildExpanded(TooltipMakerAPI info, HudPlugin plugin,
                                      TradeRouteIntelPlugin intel, float width) {
        Color h = Misc.getHighlightColor();
        Color pos = Misc.getPositiveHighlightColor();
        Color neg = Misc.getNegativeHighlightColor();
        RoutePlan plan = intel.getLastPlan();
        float inner = width - 8f;
        float btnH = 22f;

        info.addSectionHeading(UiText.TITLE, Alignment.MID, 2f);
        plugin.track(info.addButton(UiText.BTN_COLLAPSE, BTN_TOGGLE, 56f, 18f, 3f), BTN_TOGGLE);

        if (plan != null && !plan.isEmpty()) {
            TradeRouteCustomPanel.appendPlanTotals(info, plan, 4f);
            if (intel.isTripFinished()) {
                info.addPara(UiText.TRIP_FINISHED_RECALC, 3f, h);
                TradeRouteCustomPanel.appendTripSummary(info, intel, 3f);
            } else {
                int index = intel.getNextWaypointIndex();
                NextStopReadout.of(intel).append(info, 3f);
                TradeRouteCustomPanel.appendTradePreview(info, plan.sellsAtStop(index), false, neg, 2);
                TradeRouteCustomPanel.appendTradePreview(info, plan.buysAtStop(index), true, pos, 2);
                TradeRouteCustomPanel.appendOperationalBuys(info, plan.getOutgoingLeg(index), pos, 2);
            }
        } else if (plan != null && plan.isEmpty()) {
            info.addPara(UiText.EMPTY_PLAN_SHORT, 4f, neg, UiText.emptyReason(plan.getEmptyReason()));
            TradeRouteCustomPanel.appendComputeTime(info, plan, 3f);
        } else {
            info.addPara(UiText.NOT_CALCULATED_SHORT, 4f, h);
        }

        if (intel.getLastNavMessage() != null) {
            info.addPara("%s", 3f, h, intel.getLastNavMessage());
        }

        plugin.track(info.addButton(UiText.BTN_CALCULATE, BTN_CALC, inner, btnH, 6f), BTN_CALC);
        boolean hasRoute = plan != null && !plan.isEmpty();
        if (hasRoute && !intel.isTripFinished()) {
            plugin.track(info.addButton(UiText.BTN_NAV, BTN_NAV, inner, btnH, 3f), BTN_NAV);
            plugin.track(info.addButton(UiText.BTN_EXECUTE, BTN_EXECUTE, inner, btnH, 3f), BTN_EXECUTE);
        }
        if (plan != null) {
            plugin.track(info.addButton(UiText.BTN_CLEAR_SHORT, BTN_CLEAR, inner, btnH, 3f), BTN_CLEAR);
        }
        plugin.track(info.addButton(UiText.BTN_DETAIL, BTN_DETAIL, inner, btnH, 3f), BTN_DETAIL);
    }

    private static void addButtonRow(TooltipMakerAPI info, HudPlugin plugin,
                                     TradeRouteIntelPlugin intel, float inner, boolean collapsed) {
        RoutePlan plan = intel.getLastPlan();
        boolean canTravel = plan != null && !plan.isEmpty() && !intel.isTripFinished();
        CustomPanelAPI strip = Global.getSettings().createCustom(inner, 22f, null);
        float gap = 4f;
        int n = canTravel ? 3 : 2;
        float bw = (inner - gap * (n - 1)) / n;
        float x = 0f;
        x += addStripButton(strip, plugin, UiText.BTN_CALC_SHORT, BTN_CALC, x, bw);
        if (canTravel) {
            x += gap;
            x += addStripButton(strip, plugin, UiText.BTN_NAV_SHORT, BTN_NAV, x, bw);
            x += gap;
            addStripButton(strip, plugin, UiText.BTN_EXECUTE_SHORT, BTN_EXECUTE, x, bw);
            CustomPanelAPI strip2 = Global.getSettings().createCustom(inner, 22f, null);
            float bw2 = (inner - gap) / 2f;
            addStripButton(strip2, plugin, UiText.BTN_EXPAND, BTN_TOGGLE, 0f, bw2);
            addStripButton(strip2, plugin, UiText.BTN_DETAIL_SHORT, BTN_DETAIL, bw2 + gap, bw2);
            info.addCustom(strip, 3f);
            info.addCustom(strip2, 3f);
        } else {
            x += gap;
            addStripButton(strip, plugin, collapsed ? UiText.BTN_EXPAND : UiText.BTN_COLLAPSE, BTN_TOGGLE, x, bw);
            info.addCustom(strip, 3f);
            plugin.track(info.addButton(UiText.BTN_DETAIL, BTN_DETAIL, inner, 20f, 3f), BTN_DETAIL);
        }
        if (plan != null) {
            plugin.track(info.addButton(UiText.BTN_CLEAR_SHORT, BTN_CLEAR, inner, 20f, 3f), BTN_CLEAR);
        }
    }

    private static float addStripButton(CustomPanelAPI strip, HudPlugin plugin,
                                        String label, String id, float x, float width) {
        TooltipMakerAPI t = strip.createUIElement(width, 22f, false);
        t.setButtonFontVictor10();
        ButtonAPI button = t.addButton(label, id, width, 20f, 0f);
        plugin.track(button, id);
        strip.addUIElement(t).inTL(x, 0f);
        strip.updateUIElementSizeAndMakeItProcessInput(t);
        return width;
    }

    static final class HudPlugin extends BaseCustomUIPanelPlugin {
        private final TradeRouteIntelPlugin intel;
        private final List<TrackedButton> buttons = new ArrayList<>();
        private PositionAPI pos;
        private String lastHandledId;
        private long lastHandledMs;

        HudPlugin(TradeRouteIntelPlugin intel) {
            this.intel = intel;
        }

        void track(ButtonAPI button, String id) {
            if (button != null) {
                buttons.add(new TrackedButton(button, id));
            }
        }

        @Override
        public void positionChanged(PositionAPI position) {
            this.pos = position;
        }

        @Override
        public void renderBelow(float alphaMult) {
            if (pos == null) {
                return;
            }
            float x = pos.getX();
            float y = pos.getY();
            float w = pos.getWidth();
            float h = pos.getHeight();
            GL11.glPushMatrix();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(0f, 0f, 0f, 0.72f * alphaMult);
            GL11.glRectf(x, y, x + w, y + h);
            Color title = Misc.getDarkPlayerColor();
            GL11.glColor4f(title.getRed() / 255f, title.getGreen() / 255f,
                    title.getBlue() / 255f, 0.85f * alphaMult);
            GL11.glRectf(x, y + h - TITLE_H, x + w, y + h);
            Color border = Misc.getBasePlayerColor();
            GL11.glColor4f(border.getRed() / 255f, border.getGreen() / 255f,
                    border.getBlue() / 255f, 0.95f * alphaMult);
            GL11.glLineWidth(1f);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex2f(x, y);
            GL11.glVertex2f(x, y + h);
            GL11.glVertex2f(x + w, y + h);
            GL11.glVertex2f(x + w, y);
            GL11.glEnd();
            GL11.glPopMatrix();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }

        @Override
        public void advance(float amount) {
            for (TrackedButton tracked : buttons) {
                if (tracked.button.isChecked()) {
                    tracked.button.setChecked(false);
                    handle(tracked.id);
                    return;
                }
            }
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
            if (pos == null || events == null) {
                return;
            }
            for (InputEventAPI event : events) {
                if (event.isConsumed()) {
                    continue;
                }
                float ex;
                float ey;
                try {
                    ex = event.getX();
                    ey = event.getY();
                } catch (Exception e) {
                    continue;
                }
                if (event.isMouseMoveEvent()) {
                    continue;
                }
                if (!(event.isMouseDownEvent() || event.isMouseUpEvent())) {
                    continue;
                }
                if (hitsButtonAt(ex, ey)) {
                    continue;
                }
                if (pos.containsEvent(event)) {
                    event.consume();
                }
            }
        }

        private boolean hitsButtonAt(float ex, float ey) {
            for (TrackedButton tracked : buttons) {
                try {
                    PositionAPI bp = tracked.button.getPosition();
                    if (bp == null) {
                        continue;
                    }
                    float x = bp.getX();
                    float y = bp.getY();
                    if (ex >= x && ex <= x + bp.getWidth() && ey >= y && ey <= y + bp.getHeight()) {
                        return true;
                    }
                } catch (Exception ignored) {
                }
            }
            return false;
        }

        private void handle(String id) {
            long now = System.currentTimeMillis();
            if (id.equals(lastHandledId) && now - lastHandledMs < 250L) {
                return;
            }
            lastHandledId = id;
            lastHandledMs = now;
            if (BTN_TOGGLE.equals(id)) {
                intel.setHudCollapsed(!intel.isHudCollapsed());
                return;
            }
            if (BTN_CALC.equals(id)) {
                if (intel.isStopExecutorActive()) {
                    StopExecutor.get().abort(intel, UiText.EXEC_RECALC_ABORT);
                }
                intel.calculateRoute();
                return;
            }
            if (BTN_NAV.equals(id)) {
                intel.layInNextStop();
                return;
            }
            if (BTN_EXECUTE.equals(id)) {
                intel.executeNextStop();
                return;
            }
            if (BTN_DETAIL.equals(id)) {
                intel.openIntelDetails();
                return;
            }
            if (BTN_CLEAR.equals(id)) {
                intel.clearPlan();
            }
        }
    }

    private static final class TrackedButton {
        final ButtonAPI button;
        final String id;

        TrackedButton(ButtonAPI button, String id) {
            this.button = button;
            this.id = id;
        }
    }
}
