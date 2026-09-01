package org.tradeplanner.model;

/**
 * One commodity bought at the leg origin and sold at the destination.
 * Quantities and prices are estimates from an economy snapshot, not shop-shelf fills.
 */
public final class TradeAction {

    private String commodityId;
    private String name;
    private int quantity;
    private float cargoSpace;
    private float buyCost;
    private float sellRevenue;
    /** Null on old saves: executor falls back to {@code useBlack(market)}. */
    private Boolean buyOnBlack;
    private Boolean sellOnBlack;

    /** XStream / campaign save. */
    @SuppressWarnings("unused")
    private TradeAction() {
    }

    public TradeAction(String commodityId, String name, int quantity, float cargoSpace,
                       float buyCost, float sellRevenue) {
        this(commodityId, name, quantity, cargoSpace, buyCost, sellRevenue, null, null);
    }

    public TradeAction(String commodityId, String name, int quantity, float cargoSpace,
                       float buyCost, float sellRevenue, Boolean buyOnBlack, Boolean sellOnBlack) {
        this.commodityId = commodityId;
        this.name = name;
        this.quantity = quantity;
        this.cargoSpace = cargoSpace;
        this.buyCost = buyCost;
        this.sellRevenue = sellRevenue;
        this.buyOnBlack = buyOnBlack;
        this.sellOnBlack = sellOnBlack;
    }

    public String getCommodityId() {
        return commodityId;
    }

    public String getName() {
        return name;
    }

    public int getQuantity() {
        return quantity;
    }

    public float getCargoSpace() {
        return cargoSpace;
    }

    public float getCargoUsed() {
        return quantity * cargoSpace;
    }

    public float getBuyCost() {
        return buyCost;
    }

    public float getSellRevenue() {
        return sellRevenue;
    }

    public float getGrossProfit() {
        return sellRevenue - buyCost;
    }

    public Boolean getBuyOnBlack() {
        return buyOnBlack;
    }

    public Boolean getSellOnBlack() {
        return sellOnBlack;
    }

    public boolean buyOnBlack(boolean fallback) {
        return buyOnBlack != null ? buyOnBlack.booleanValue() : fallback;
    }

    public boolean sellOnBlack(boolean fallback) {
        return sellOnBlack != null ? sellOnBlack.booleanValue() : fallback;
    }
}
