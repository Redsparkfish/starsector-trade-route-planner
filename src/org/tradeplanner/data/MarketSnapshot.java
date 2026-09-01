package org.tradeplanner.data;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Filtered economy market at snapshot time. Does not hold live {@link MarketAPI} so it is
 * safe to keep transiently in the intel plugin without bloating saves.
 */
public final class MarketSnapshot {

    private final String marketId;
    private final String name;
    private final String factionId;
    private final String factionName;
    private final int size;
    private final String systemName;
    private final boolean inHyperspace;
    private final boolean hasOpenMarket;
    private final boolean hasBlackMarket;
    private final float openTariff;
    private final float blackTariff;
    private final boolean hostileToPlayer;
    private final boolean openUsableWithCurrentTransponder;
    private final boolean blackUsable;
    private final List<CommodityTradeInfo> commodities;

    public MarketSnapshot(String marketId, String name, String factionId, String factionName,
                          int size, String systemName, boolean inHyperspace,
                          boolean hasOpenMarket, boolean hasBlackMarket,
                          float openTariff, float blackTariff,
                          boolean hostileToPlayer,
                          boolean openUsableWithCurrentTransponder, boolean blackUsable,
                          List<CommodityTradeInfo> commodities) {
        this.marketId = marketId;
        this.name = name;
        this.factionId = factionId;
        this.factionName = factionName;
        this.size = size;
        this.systemName = systemName;
        this.inHyperspace = inHyperspace;
        this.hasOpenMarket = hasOpenMarket;
        this.hasBlackMarket = hasBlackMarket;
        this.openTariff = openTariff;
        this.blackTariff = blackTariff;
        this.hostileToPlayer = hostileToPlayer;
        this.openUsableWithCurrentTransponder = openUsableWithCurrentTransponder;
        this.blackUsable = blackUsable;
        this.commodities = Collections.unmodifiableList(new ArrayList<>(commodities));
    }

    public MarketAPI resolveMarket() {
        return Global.getSector().getEconomy().getMarket(marketId);
    }

    public SectorEntityToken getPrimaryEntity() {
        MarketAPI market = resolveMarket();
        return market == null ? null : market.getPrimaryEntity();
    }

    public String getMarketId() {
        return marketId;
    }

    public String getName() {
        return name;
    }

    public String getFactionId() {
        return factionId;
    }

    public String getFactionName() {
        return factionName;
    }

    public int getSize() {
        return size;
    }

    public String getSystemName() {
        return systemName;
    }

    public boolean isInHyperspace() {
        return inHyperspace;
    }

    public boolean hasOpenMarket() {
        return hasOpenMarket;
    }

    public boolean hasBlackMarket() {
        return hasBlackMarket;
    }

    public float getOpenTariff() {
        return openTariff;
    }

    public float getBlackTariff() {
        return blackTariff;
    }

    public boolean isHostileToPlayer() {
        return hostileToPlayer;
    }

    public boolean isOpenUsableWithCurrentTransponder() {
        return openUsableWithCurrentTransponder;
    }

    public boolean isBlackUsable() {
        return blackUsable;
    }

    public List<CommodityTradeInfo> getCommodities() {
        return commodities;
    }

    public CommodityTradeInfo getCommodity(String commodityId) {
        if (commodityId == null) {
            return null;
        }
        for (CommodityTradeInfo row : commodities) {
            if (commodityId.equals(row.getId())) {
                return row;
            }
        }
        return null;
    }
}
