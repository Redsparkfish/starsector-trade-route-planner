package org.tradeplanner.data;

/**
 * Economy-layer commodity row for one market. Quantities are estimates, not shop-shelf stock.
 * Do not store a unit price — quotes are quantity-dependent via {@link org.tradeplanner.service.PriceQuoter}.
 */
public final class CommodityTradeInfo {

    private final String id;
    private final String name;
    private final float cargoSpace;
    private final float econUnit;
    private final boolean fuel;
    private final boolean illegalOnOpenMarket;
    private final int excessQty;
    private final int deficitQty;
    private final int available;
    private final int approxStockpile;
    private final int estimatedBuyOpen;
    private final int estimatedBuyBlack;
    private final int estimatedSellOpen;
    private final int estimatedSellBlack;

    public CommodityTradeInfo(String id, String name, float cargoSpace, float econUnit,
                              boolean fuel, boolean illegalOnOpenMarket,
                              int excessQty, int deficitQty, int available, int approxStockpile,
                              int estimatedBuyOpen, int estimatedBuyBlack,
                              int estimatedSellOpen, int estimatedSellBlack) {
        this.id = id;
        this.name = name;
        this.cargoSpace = cargoSpace;
        this.econUnit = econUnit;
        this.fuel = fuel;
        this.illegalOnOpenMarket = illegalOnOpenMarket;
        this.excessQty = excessQty;
        this.deficitQty = deficitQty;
        this.available = available;
        this.approxStockpile = approxStockpile;
        this.estimatedBuyOpen = Math.max(0, estimatedBuyOpen);
        this.estimatedBuyBlack = Math.max(0, estimatedBuyBlack);
        this.estimatedSellOpen = Math.max(0, estimatedSellOpen);
        this.estimatedSellBlack = Math.max(0, estimatedSellBlack);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public float getCargoSpace() {
        return cargoSpace;
    }

    public float getEconUnit() {
        return econUnit;
    }

    public boolean isFuel() {
        return fuel;
    }

    public boolean isIllegalOnOpenMarket() {
        return illegalOnOpenMarket;
    }

    public int getExcessQty() {
        return excessQty;
    }

    public int getDeficitQty() {
        return deficitQty;
    }

    public int getAvailable() {
        return available;
    }

    public int getApproxStockpile() {
        return approxStockpile;
    }

    /** Open + black buy caps. Fuel restock fallback when a channel is not split. */
    public int getEstimatedBuyMax() {
        return estimatedBuyOpen + estimatedBuyBlack;
    }

    public int getEstimatedBuyMax(boolean black) {
        return black ? estimatedBuyBlack : estimatedBuyOpen;
    }

    /** Diagnostic imbalance hint (deficit / dest E[L]); knapsack does not use this as a sell qty cap. */
    public int getEstimatedSellMax() {
        return estimatedSellOpen + estimatedSellBlack;
    }

    public int getEstimatedSellMax(boolean black) {
        return black ? estimatedSellBlack : estimatedSellOpen;
    }
}
