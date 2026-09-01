package org.tradeplanner.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.ButtonAPI.UICheckboxSize;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.tradeplanner.config.FactionTradeSettings;
import org.tradeplanner.config.PlannerConfig;
import org.tradeplanner.service.MarketDataCollector;

import java.awt.Color;
import java.util.List;

/**
 * Intel settings view: campaign knobs that change during a run. Checkboxes edit a draft;
 * only 确认 writes the save and returns to the planner.
 */
final class TradeSettingsPanel {

    private TradeSettingsPanel() {
    }

    static void render(TradeRouteIntelPlugin intel, CustomPanelAPI panel, float width, float height) {
        float pad = 10f;
        float w = width - 20f;
        TooltipMakerAPI info = panel.createUIElement(width, height, true);
        Color h = Misc.getHighlightColor();
        Color neg = Misc.getNegativeHighlightColor();
        PlannerConfig cfg = PlannerConfig.load();
        intel.ensureFactionTrade(cfg);
        FactionTradeSettings draft = intel.ensureFactionDraft();

        info.addSectionHeading(UiText.SECTION_SETTINGS, Alignment.MID, 0f);
        info.addPara(UiText.SETTINGS_INTRO, pad);
        info.addPara(UiText.SETTINGS_LUNA_NOTE, 3f);

        addActionButtons(info, w, pad);
        renderAlphaBlock(intel, info, w, pad);
        renderFactionBlock(info, draft, cfg, pad, h, neg);

        panel.addUIElement(info).inTL(0, 0);
    }

    private static void renderAlphaBlock(TradeRouteIntelPlugin intel, TooltipMakerAPI info,
                                         float w, float pad) {
        float draft = intel.ensurePosTimeWeightDraft();
        info.addSectionHeading(UiText.SECTION_ALPHA, Alignment.MID, pad);
        info.addPara(UiText.SETTINGS_ALPHA_HELP, 3f);
        Color base = Misc.getBasePlayerColor();
        Color bg = Misc.getDarkPlayerColor();
        Color bright = Misc.getBrightPlayerColor();
        for (float v : TradeRouteIntelPlugin.POS_WEIGHT_CHOICES) {
            ButtonAPI box = info.addAreaCheckbox(UiText.alphaChoice(v),
                    TradeRouteIntelPlugin.PREFIX_POS_WEIGHT + formatAlphaId(v),
                    base, bg, bright, w, 22f, 3f, true);
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

    private static void addActionButtons(TooltipMakerAPI info, float w, float pad) {
        info.addButton(UiText.BTN_OK, TradeRouteIntelPlugin.BUTTON_SETTINGS_OK, w, 24f, pad);
        info.addButton(UiText.BTN_CANCEL, TradeRouteIntelPlugin.BUTTON_SETTINGS_CANCEL, w, 24f, 3f);
        info.addButton(UiText.BTN_RESET, TradeRouteIntelPlugin.BUTTON_FACTION_RESET, 120f, 20f, 6f);
    }

    private static void renderFactionBlock(TooltipMakerAPI info, FactionTradeSettings draft,
                                           PlannerConfig cfg, float pad, Color h, Color neg) {
        info.addSectionHeading(UiText.SECTION_FACTIONS, Alignment.MID, pad);
        info.addPara(UiText.SETTINGS_FACTION_HELP, 3f);
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
        for (String id : ids) {
            FactionTradeSettings.Pref pref = draft.effective(id, cfg);
            String hint = factionRelHint(id, player);
            Color nameColor = pref.open || pref.black ? h : neg;
            if (!pref.open && !pref.black) {
                info.addPara(UiText.FACTION_NO_TRADE, 8f, nameColor,
                        PlannerConfig.factionDisplayName(id), hint);
            } else {
                info.addPara(UiText.FACTION_WITH_MODE, 8f, nameColor,
                        PlannerConfig.factionDisplayName(id), hint, UiText.factionMode(pref.open, pref.black));
            }
            ButtonAPI openBox = info.addCheckbox(16f, 16f, UiText.OPEN_MARKET,
                    TradeRouteIntelPlugin.PREFIX_FACTION_OPEN + id, UICheckboxSize.SMALL, 2f);
            if (openBox != null) {
                openBox.setChecked(pref.open);
            }
            ButtonAPI blackBox = info.addCheckbox(16f, 16f, UiText.BLACK_MARKET,
                    TradeRouteIntelPlugin.PREFIX_FACTION_BLACK + id, UICheckboxSize.SMALL, 2f);
            if (blackBox != null) {
                blackBox.setChecked(pref.black);
            }
        }
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
        FactionTradeSettings draft = intel.getFactionDraft();
        if (draft == null) {
            return false;
        }
        PlannerConfig cfg = PlannerConfig.load();
        if (raw.startsWith(TradeRouteIntelPlugin.PREFIX_FACTION_OPEN)) {
            String fid = raw.substring(TradeRouteIntelPlugin.PREFIX_FACTION_OPEN.length());
            draft.setOpen(fid, !draft.allowOpen(fid, cfg), cfg);
            return true;
        }
        if (raw.startsWith(TradeRouteIntelPlugin.PREFIX_FACTION_BLACK)) {
            String fid = raw.substring(TradeRouteIntelPlugin.PREFIX_FACTION_BLACK.length());
            draft.setBlack(fid, !draft.allowBlack(fid, cfg), cfg);
            return true;
        }
        return false;
    }
}
