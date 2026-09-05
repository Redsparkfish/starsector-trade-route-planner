package org.tradeplanner.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.ButtonAPI.UICheckboxSize;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.tradeplanner.config.CommodityTradeSettings;
import org.tradeplanner.config.FactionTradeSettings;
import org.tradeplanner.config.PlannerConfig;
import org.tradeplanner.service.MarketDataCollector;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Intel settings view: campaign knobs that change during a run. Checkboxes edit a draft;
 * open/black toggles do not rebuild the panel (keeps scroll). 确认 writes the save
 * and returns to the planner.
 */
final class TradeSettingsPanel {

    private static final float ROW_H = 22f;
    private static final float BOX_W = 62f;
    private static final float COMMODITY_BOX_W = 22f;
    private static final float NAME_BOX_GAP = 8f;
    private static final float COL_GAP = 10f;
    private static final float TWO_COL_MIN_W = 460f;

    private TradeSettingsPanel() {
    }

    static void render(TradeRouteIntelPlugin intel, CustomPanelAPI panel, float width, float height) {
        float pad = 8f;
        float w = width - 20f;
        float y = pad;
        y += IntelActionButtons.row(panel, y, width,
                UiText.BTN_OK, TradeRouteIntelPlugin.BUTTON_SETTINGS_OK,
                UiText.BTN_CANCEL, TradeRouteIntelPlugin.BUTTON_SETTINGS_CANCEL,
                UiText.BTN_RESET, TradeRouteIntelPlugin.BUTTON_FACTION_RESET);
        float bodyH = Math.max(80f, height - y);
        TooltipMakerAPI info = panel.createUIElement(width, bodyH, true);
        Color h = Misc.getHighlightColor();
        Color neg = Misc.getNegativeHighlightColor();
        PlannerConfig cfg = PlannerConfig.load();
        intel.ensureFactionTrade(cfg);
        FactionTradeSettings draft = intel.ensureFactionDraft();
        CommodityTradeSettings commodities = intel.ensureCommodityDraft();

        info.addSectionHeading(UiText.SECTION_SETTINGS, Alignment.MID, 0f);
        info.addPara(UiText.SETTINGS_INTRO, pad);
        info.addPara(UiText.SETTINGS_LUNA_NOTE, 2f);

        renderAlphaBlock(intel, info, w, pad);
        renderFactionBlock(info, draft, cfg, w, pad, h, neg);
        renderCommodityBlock(info, commodities, w, pad, h, neg);

        panel.addUIElement(info).inTL(0, y);
    }

    private static void renderAlphaBlock(TradeRouteIntelPlugin intel, TooltipMakerAPI info,
                                         float w, float pad) {
        float draft = intel.ensurePosTimeWeightDraft();
        info.addSectionHeading(UiText.SECTION_ALPHA, Alignment.MID, pad);
        info.addPara(UiText.SETTINGS_ALPHA_HELP, 2f);
        Color base = Misc.getBasePlayerColor();
        Color bg = Misc.getDarkPlayerColor();
        Color bright = Misc.getBrightPlayerColor();
        for (float v : TradeRouteIntelPlugin.POS_WEIGHT_CHOICES) {
            ButtonAPI box = info.addAreaCheckbox(UiText.alphaChoice(v),
                    TradeRouteIntelPlugin.PREFIX_POS_WEIGHT + formatAlphaId(v),
                    base, bg, bright, w, 20f, 2f, true);
            if (box != null) {
                box.setChecked(Math.abs(draft - v) < 0.001f);
            }
        }
    }

    private static String formatAlphaId(float v) {
        if (v <= 0.001f) {
            return "0";
        }
        if (Math.abs(v - 1f) < 0.001f) {
            return "1";
        }
        if (Math.abs(v - 0.25f) < 0.001f) {
            return "0.25";
        }
        if (Math.abs(v - 0.5f) < 0.001f) {
            return "0.5";
        }
        if (Math.abs(v - 0.75f) < 0.001f) {
            return "0.75";
        }
        return Float.toString(v);
    }

    private static void renderFactionBlock(TooltipMakerAPI info, FactionTradeSettings draft,
                                           PlannerConfig cfg, float w, float pad, Color h, Color neg) {
        info.addSectionHeading(UiText.SECTION_FACTIONS, Alignment.MID, pad);
        info.addPara(UiText.SETTINGS_FACTION_HELP, 2f);
        List<String> ids = MarketDataCollector.economyFactionIds();
        if (ids.isEmpty()) {
            info.addPara(UiText.SETTINGS_NO_FACTIONS, 6f, h);
            return;
        }
        FactionAPI player = null;
        try {
            player = Global.getSector().getPlayerFaction();
        } catch (Exception ignored) {
        }
        int cols = w >= TWO_COL_MIN_W ? 2 : 1;
        float colW = cols == 1 ? w : (w - COL_GAP) / cols;
        int rows = (ids.size() + cols - 1) / cols;
        float maxNameW = Math.max(48f, colW - BOX_W * 2f - NAME_BOX_GAP);
        FactionGridPlugin plugin = new FactionGridPlugin();
        CustomPanelAPI grid = Global.getSettings().createCustom(w, rows * ROW_H, plugin);
        float[] colNameW = new float[cols];
        String[] labels = new String[ids.size()];
        LabelAPI probe = nameWidthProbe(grid, maxNameW);
        for (int i = 0; i < ids.size(); i++) {
            labels[i] = PlannerConfig.factionDisplayName(ids.get(i)) + factionRelHint(ids.get(i), player);
            int c = i % cols;
            colNameW[c] = Math.max(colNameW[c], measureNameWidth(probe, labels[i], maxNameW));
        }
        for (int i = 0; i < ids.size(); i++) {
            int r = i / cols;
            int c = i % cols;
            addFactionRow(grid, plugin, ids.get(i), labels[i], draft, cfg,
                    c * (colW + COL_GAP), r * ROW_H, colNameW[c], maxNameW, h, neg);
        }
        info.addCustom(grid, 4f);
    }

    private static LabelAPI nameWidthProbe(CustomPanelAPI grid, float maxNameW) {
        TooltipMakerAPI probe = grid.createUIElement(maxNameW, ROW_H, false);
        probe.setParaSmallInsignia();
        return probe.addPara(" ", 0f);
    }

    private static float measureNameWidth(LabelAPI probe, String label, float maxNameW) {
        if (probe == null || label == null) {
            return Math.min(maxNameW, 48f);
        }
        return Math.min(maxNameW, Math.max(48f, probe.computeTextWidth(label) + 4f));
    }

    private static void addFactionRow(CustomPanelAPI grid, FactionGridPlugin plugin, String id,
                                      String label, FactionTradeSettings draft, PlannerConfig cfg,
                                      float x, float y, float colNameW, float maxNameW,
                                      Color h, Color neg) {
        FactionTradeSettings.Pref pref = draft.effective(id, cfg);
        Color nameColor = pref.open || pref.black ? h : neg;

        TooltipMakerAPI nameEl = grid.createUIElement(maxNameW, ROW_H, false);
        nameEl.setParaSmallInsignia();
        nameEl.addPara(label, nameColor, 3f);
        nameEl.getPosition().setSize(colNameW, ROW_H);
        grid.addUIElement(nameEl).inTL(x, y);

        float boxX = x + colNameW + NAME_BOX_GAP;
        ButtonAPI openBox = addRowCheckbox(grid, boxX, y, BOX_W,
                UiText.OPEN_MARKET, TradeRouteIntelPlugin.PREFIX_FACTION_OPEN + id, pref.open);
        ButtonAPI blackBox = addRowCheckbox(grid, boxX + BOX_W, y, BOX_W,
                UiText.BLACK_MARKET, TradeRouteIntelPlugin.PREFIX_FACTION_BLACK + id, pref.black);
        plugin.track(openBox, id, true, pref.open);
        plugin.track(blackBox, id, false, pref.black);
    }

    private static void renderCommodityBlock(TooltipMakerAPI info, CommodityTradeSettings draft,
                                             float w, float pad, Color h, Color neg) {
        info.addSectionHeading(UiText.SECTION_COMMODITIES, Alignment.MID, pad);
        info.addPara(UiText.SETTINGS_COMMODITY_HELP, 2f);
        List<String> ids = MarketDataCollector.tradeCommodityIds();
        if (ids.isEmpty()) {
            info.addPara(UiText.SETTINGS_NO_COMMODITIES, 6f, h);
            return;
        }
        if (draft == null) {
            draft = new CommodityTradeSettings();
        }
        int cols = w >= TWO_COL_MIN_W ? 2 : 1;
        float colW = cols == 1 ? w : (w - COL_GAP) / cols;
        int rows = (ids.size() + cols - 1) / cols;
        float maxNameW = Math.max(48f, colW - COMMODITY_BOX_W - NAME_BOX_GAP);
        CommodityGridPlugin plugin = new CommodityGridPlugin();
        CustomPanelAPI grid = Global.getSettings().createCustom(w, rows * ROW_H, plugin);
        float[] colNameW = new float[cols];
        String[] labels = new String[ids.size()];
        LabelAPI probe = nameWidthProbe(grid, maxNameW);
        for (int i = 0; i < ids.size(); i++) {
            labels[i] = MarketDataCollector.commodityDisplayName(ids.get(i));
            int c = i % cols;
            colNameW[c] = Math.max(colNameW[c], measureNameWidth(probe, labels[i], maxNameW));
        }
        for (int i = 0; i < ids.size(); i++) {
            int r = i / cols;
            int c = i % cols;
            addCommodityRow(grid, plugin, ids.get(i), labels[i], draft,
                    c * (colW + COL_GAP), r * ROW_H, colNameW[c], maxNameW, h, neg);
        }
        info.addCustom(grid, 4f);
    }

    private static void addCommodityRow(CustomPanelAPI grid, CommodityGridPlugin plugin, String id,
                                        String label, CommodityTradeSettings draft,
                                        float x, float y, float colNameW, float maxNameW,
                                        Color h, Color neg) {
        boolean on = draft.allow(id);
        Color nameColor = on ? h : neg;
        TooltipMakerAPI nameEl = grid.createUIElement(maxNameW, ROW_H, false);
        nameEl.setParaSmallInsignia();
        nameEl.addPara(label, nameColor, 3f);
        nameEl.getPosition().setSize(colNameW, ROW_H);
        grid.addUIElement(nameEl).inTL(x, y);
        ButtonAPI box = addRowCheckbox(grid, x + colNameW + NAME_BOX_GAP, y, COMMODITY_BOX_W,
                "", TradeRouteIntelPlugin.PREFIX_COMMODITY + id, on);
        plugin.track(box, id, on);
    }

    private static ButtonAPI addRowCheckbox(CustomPanelAPI grid, float x, float y, float width,
                                            String text, String data, boolean checked) {
        TooltipMakerAPI t = grid.createUIElement(width, ROW_H, false);
        t.setButtonFontVictor10();
        ButtonAPI box = t.addCheckbox(16f, 16f, text, data, UICheckboxSize.SMALL, 2f);
        if (box != null) {
            box.setChecked(checked);
        }
        grid.addUIElement(t).inTL(x, y);
        grid.updateUIElementSizeAndMakeItProcessInput(t);
        return box;
    }

    private static String factionRelHint(String factionId, FactionAPI player) {
        if (factionId == null) {
            return "";
        }
        try {
            FactionAPI faction = Global.getSector().getFaction(factionId);
            if (faction == null) {
                return "";
            }
            if (player != null && faction.getId() != null && faction.getId().equals(player.getId())) {
                return UiText.REL_PLAYER;
            }
            if (player != null && faction.isHostileTo(player)) {
                return UiText.REL_HOSTILE;
            }
            if (faction.getRelToPlayer() != null && faction.getRelToPlayer().getLevel() != null) {
                String rel = faction.getRelToPlayer().getLevel().getDisplayName();
                if (rel != null && !rel.isEmpty()) {
                    return "，" + rel;
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    static boolean handleButton(Object buttonId, TradeRouteIntelPlugin intel) {
        if (TradeRouteIntelPlugin.BUTTON_SETTINGS_OK.equals(buttonId)) {
            intel.confirmSettings();
            return true;
        }
        if (TradeRouteIntelPlugin.BUTTON_SETTINGS_CANCEL.equals(buttonId)) {
            intel.cancelSettings();
            return true;
        }
        if (TradeRouteIntelPlugin.BUTTON_FACTION_RESET.equals(buttonId)) {
            intel.resetFactionDraft();
            return true;
        }
        if (!(buttonId instanceof String)) {
            return false;
        }
        String raw = (String) buttonId;
        if (raw.startsWith(TradeRouteIntelPlugin.PREFIX_POS_WEIGHT)) {
            try {
                float v = Float.parseFloat(raw.substring(TradeRouteIntelPlugin.PREFIX_POS_WEIGHT.length()));
                intel.setPosTimeWeightDraft(v);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * Nested grid writes draft from {@code isChecked}. Swallow intel echoes so the
     * value is not inverted a second time.
     */
    static boolean handleFactionToggle(Object buttonId, TradeRouteIntelPlugin intel) {
        if (!(buttonId instanceof String)) {
            return false;
        }
        String raw = (String) buttonId;
        return raw.startsWith(TradeRouteIntelPlugin.PREFIX_FACTION_OPEN)
                || raw.startsWith(TradeRouteIntelPlugin.PREFIX_FACTION_BLACK)
                || raw.startsWith(TradeRouteIntelPlugin.PREFIX_COMMODITY);
    }

    private static final class FactionGridPlugin extends BaseCustomUIPanelPlugin {
        private final List<TrackedBox> boxes = new ArrayList<>();

        void track(ButtonAPI button, String factionId, boolean openChannel, boolean initial) {
            if (button != null && factionId != null) {
                boxes.add(new TrackedBox(button, factionId, openChannel, initial));
            }
        }

        @Override
        public void buttonPressed(Object buttonId) {
            sync();
        }

        @Override
        public void advance(float amount) {
            sync();
        }

        private void sync() {
            TradeRouteIntelPlugin intel = TradeRouteIntelPlugin.getInstance();
            if (intel == null) {
                return;
            }
            FactionTradeSettings draft = intel.getFactionDraft();
            if (draft == null) {
                return;
            }
            PlannerConfig cfg = PlannerConfig.load();
            for (TrackedBox box : boxes) {
                boolean now = box.button.isChecked();
                if (now == box.last) {
                    continue;
                }
                box.last = now;
                if (box.openChannel) {
                    draft.setOpen(box.factionId, now, cfg);
                } else {
                    draft.setBlack(box.factionId, now, cfg);
                }
            }
        }

        private static final class TrackedBox {
            final ButtonAPI button;
            final String factionId;
            final boolean openChannel;
            boolean last;

            TrackedBox(ButtonAPI button, String factionId, boolean openChannel, boolean last) {
                this.button = button;
                this.factionId = factionId;
                this.openChannel = openChannel;
                this.last = last;
            }
        }
    }

    private static final class CommodityGridPlugin extends BaseCustomUIPanelPlugin {
        private final List<TrackedBox> boxes = new ArrayList<>();

        void track(ButtonAPI button, String commodityId, boolean initial) {
            if (button != null && commodityId != null) {
                boxes.add(new TrackedBox(button, commodityId, initial));
            }
        }

        @Override
        public void buttonPressed(Object buttonId) {
            sync();
        }

        @Override
        public void advance(float amount) {
            sync();
        }

        private void sync() {
            TradeRouteIntelPlugin intel = TradeRouteIntelPlugin.getInstance();
            if (intel == null) {
                return;
            }
            CommodityTradeSettings draft = intel.getCommodityDraft();
            if (draft == null) {
                return;
            }
            for (TrackedBox box : boxes) {
                boolean now = box.button.isChecked();
                if (now == box.last) {
                    continue;
                }
                box.last = now;
                draft.setAllow(box.commodityId, now);
            }
        }

        private static final class TrackedBox {
            final ButtonAPI button;
            final String commodityId;
            boolean last;

            TrackedBox(ButtonAPI button, String commodityId, boolean last) {
                this.button = button;
                this.commodityId = commodityId;
                this.last = last;
            }
        }
    }
}
