package org.tradeplanner.model;

import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of a single-leg bounded knapsack: goods bought at A and sold at B.
 * Empty when no profitable load fits cargo and cash.
 */
public final class CargoLoad {

    public static final CargoLoad EMPTY = new CargoLoad(Collections.emptyList(), 0f, 0f, 0f);

    private final List<TradeAction> items;
    private final float cargoUsed;
    private final float buyCost;
    private final float sellRevenue;

    public CargoLoad(List<TradeAction> items, float cargoUsed, float buyCost, float sellRevenue) {
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.cargoUsed = cargoUsed;
        this.buyCost = buyCost;
        this.sellRevenue = sellRevenue;
    }

    public static CargoLoad of(List<TradeAction> items) {
        if (items == null || items.isEmpty()) {
            return EMPTY;
        }
        float cargo = 0f;
        float buy = 0f;
        float sell = 0f;
        for (TradeAction item : items) {
            cargo += item.getCargoUsed();
            buy += item.getBuyCost();
            sell += item.getSellRevenue();
        }
        return new CargoLoad(items, cargo, buy, sell);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public List<TradeAction> getItems() {
        return items;
    }

    public float getCargoUsed() {
        return cargoUsed;
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

    /** Fuel packed as a trade good (tank, not cargo). */
    public int getFuelQty() {
        int qty = 0;
        for (TradeAction item : items) {
            if (item != null && Commodities.FUEL.equals(item.getCommodityId())) {
                qty += item.getQuantity();
            }
        }
        return qty;
    }
}
