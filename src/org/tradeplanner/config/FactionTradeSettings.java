package org.tradeplanner.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-faction open/black toggles for the current campaign save.
 * Missing keys use {@link PlannerConfig} default lists (black-only vs open-only).
 */
public final class FactionTradeSettings {

    /** XStream / campaign save. */
    public static final class Pref {
        public boolean open;
        public boolean black;

        public Pref() {
            this.open = true;
            this.black = false;
        }

        public Pref(boolean open, boolean black) {
            this.open = open;
            this.black = black;
        }
    }

    private Map<String, Pref> byId = new HashMap<>();

    public FactionTradeSettings() {
        this.byId = new HashMap<>();
    }

    public Map<String, Pref> getById() {
        if (byId == null) {
            byId = new HashMap<>();
        }
        return byId;
    }

    public void clear() {
        getById().clear();
    }

    public boolean isEmpty() {
        return getById().isEmpty();
    }

    /** Copy stored prefs only (empty source stays empty = defaults). */
    public void replaceWith(FactionTradeSettings other) {
        clear();
        if (other == null) {
            return;
        }
        for (Map.Entry<String, Pref> e : other.getById().entrySet()) {
            String id = e.getKey();
            Pref p = e.getValue();
            if (id != null && p != null) {
                getById().put(id, new Pref(p.open, p.black));
            }
        }
    }

    /**
     * Draft snapshot: every listed faction gets its current effective pref so checkboxes
     * match what calculation would use, including default-list fallbacks.
     */
    public static FactionTradeSettings snapshot(FactionTradeSettings source,
                                                Collection<String> factionIds, PlannerConfig cfg) {
        FactionTradeSettings out = new FactionTradeSettings();
        FactionTradeSettings src = source == null ? new FactionTradeSettings() : source;
        if (factionIds == null) {
            return out;
        }
        for (String id : factionIds) {
            if (id == null || id.isEmpty()) {
                continue;
            }
            Pref e = src.effective(id, cfg);
            out.getById().put(id, new Pref(e.open, e.black));
        }
        return out;
    }

    public static Pref defaultPref(String factionId, PlannerConfig cfg) {
        boolean black = cfg != null && cfg.isDefaultBlackMarketFaction(factionId);
        boolean openListed = cfg != null && cfg.isDefaultOpenMarketFaction(factionId);
        boolean open = openListed || !black;
        return new Pref(open, black);
    }

    public Pref effective(String factionId, PlannerConfig cfg) {
        if (factionId == null) {
            return new Pref(true, false);
        }
        Pref stored = getById().get(factionId);
        if (stored != null) {
            return new Pref(stored.open, stored.black);
        }
        return defaultPref(factionId, cfg);
    }

    public boolean allowOpen(String factionId, PlannerConfig cfg) {
        return effective(factionId, cfg).open;
    }

    public boolean allowBlack(String factionId, PlannerConfig cfg) {
        return effective(factionId, cfg).black;
    }

    public void setOpen(String factionId, boolean open, PlannerConfig cfg) {
        if (factionId == null) {
            return;
        }
        Pref p = effective(factionId, cfg);
        p.open = open;
        getById().put(factionId, p);
    }

    public void setBlack(String factionId, boolean black, PlannerConfig cfg) {
        if (factionId == null) {
            return;
        }
        Pref p = effective(factionId, cfg);
        p.black = black;
        getById().put(factionId, p);
    }

    public void seedBothOn(Collection<String> factionIds) {
        if (factionIds == null) {
            return;
        }
        for (String id : factionIds) {
            if (id != null && !id.isEmpty()) {
                getById().put(id, new Pref(true, true));
            }
        }
    }

    public enum PolicyKind {
        DEFAULT,
        ALL_OPEN_BLACK,
        CUSTOM_SKIP,
        CUSTOM
    }

    public static final class PolicySummary {
        public final PolicyKind kind;
        public final int skippedCount;

        public PolicySummary(PolicyKind kind, int skippedCount) {
            this.kind = kind;
            this.skippedCount = skippedCount;
        }
    }

    public PolicySummary classify(PlannerConfig cfg, Collection<String> factionIds) {
        if (isEmpty()) {
            return new PolicySummary(PolicyKind.DEFAULT, 0);
        }
        List<String> ids = factionIds == null ? Collections.<String>emptyList() : new ArrayList<>(factionIds);
        boolean matchesDefault = true;
        int none = 0;
        int both = 0;
        for (String id : ids) {
            Pref e = effective(id, cfg);
            Pref d = defaultPref(id, cfg);
            if (e.open != d.open || e.black != d.black) {
                matchesDefault = false;
            }
            if (!e.open && !e.black) {
                none++;
            }
            if (e.open && e.black) {
                both++;
            }
        }
        if (matchesDefault) {
            return new PolicySummary(PolicyKind.DEFAULT, none);
        }
        if (!ids.isEmpty() && both == ids.size()) {
            return new PolicySummary(PolicyKind.ALL_OPEN_BLACK, none);
        }
        if (none > 0) {
            return new PolicySummary(PolicyKind.CUSTOM_SKIP, none);
        }
        return new PolicySummary(PolicyKind.CUSTOM, none);
    }
}
