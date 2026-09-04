package org.tradeplanner.config;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-commodity trade toggles for the current campaign save.
 * Missing keys are enabled. Empty settings means every trade commodity is on.
 */
public final class CommodityTradeSettings {

    private Map<String, Boolean> byId = new HashMap<>();

    public CommodityTradeSettings() {
        this.byId = new HashMap<>();
    }

    public Map<String, Boolean> getById() {
        if (byId == null) {
            byId = new HashMap<>();
        }
        return byId;
    }

    public void clear() {
        getById().clear();
    }

    public void replaceWith(CommodityTradeSettings other) {
        clear();
        if (other == null) {
            return;
        }
        for (Map.Entry<String, Boolean> e : other.getById().entrySet()) {
            String id = e.getKey();
            Boolean on = e.getValue();
            if (id != null && on != null) {
                getById().put(id, on);
            }
        }
    }

    public static CommodityTradeSettings snapshot(CommodityTradeSettings source,
                                                  Collection<String> commodityIds) {
        CommodityTradeSettings out = new CommodityTradeSettings();
        CommodityTradeSettings src = source == null ? new CommodityTradeSettings() : source;
        if (commodityIds == null) {
            return out;
        }
        for (String id : commodityIds) {
            if (id == null || id.isEmpty()) {
                continue;
            }
            out.getById().put(id, Boolean.valueOf(src.allow(id)));
        }
        return out;
    }

    public boolean allow(String commodityId) {
        if (commodityId == null) {
            return true;
        }
        Boolean stored = getById().get(commodityId);
        return stored == null || stored.booleanValue();
    }

    public void setAllow(String commodityId, boolean allow) {
        if (commodityId == null) {
            return;
        }
        getById().put(commodityId, Boolean.valueOf(allow));
    }
}
