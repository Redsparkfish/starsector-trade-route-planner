package org.tradeplanner.ui;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

/**
 * Action buttons as direct children of the intel large-description panel.
 * Nested {@code createCustom} strips miss {@code buttonPressConfirmed} and freeze
 * the intel UI if they rebuild mid-click.
 */
final class IntelActionButtons {

    static final float HEIGHT = 24f;
    static final float GAP = 4f;
    static final float SIDE = 10f;

    private IntelActionButtons() {
    }

    static float innerWidth(float panelWidth) {
        return Math.max(80f, panelWidth - SIDE * 2f);
    }

    /**
     * One row of equal-width native intel buttons. {@code labelsAndIds} is
     * {@code label, id, label, id, ...}. Returns the vertical space used.
     */
    static float row(CustomPanelAPI panel, float y, float panelWidth, String... labelsAndIds) {
        if (panel == null || labelsAndIds == null || labelsAndIds.length < 2) {
            return 0f;
        }
        int n = labelsAndIds.length / 2;
        float inner = innerWidth(panelWidth);
        float bw = n <= 1 ? inner : (inner - GAP * (n - 1)) / n;
        float x = SIDE;
        for (int i = 0; i < n; i++) {
            add(panel, x, y, bw, labelsAndIds[i * 2], labelsAndIds[i * 2 + 1]);
            x += bw + GAP;
        }
        return HEIGHT + GAP;
    }

    static void add(CustomPanelAPI panel, float x, float y, float width, String label, String id) {
        if (panel == null || label == null || id == null) {
            return;
        }
        TooltipMakerAPI t = panel.createUIElement(width, HEIGHT, false);
        t.addButton(label, id, width, HEIGHT - 2f, 0f);
        panel.addUIElement(t).inTL(x, y);
        panel.updateUIElementSizeAndMakeItProcessInput(t);
    }
}
