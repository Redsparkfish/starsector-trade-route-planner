package org.tradeplanner.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One hop: buy at {@code from} (or travel empty from the fleet), fly to {@code to}, sell there.
 * Travel days are 预计 only. Quantities are economy-layer estimates.
 */
public final class RouteLeg {

    /** Synthetic origin id for the positioning hop from the player fleet. */
    public static final String FLEET_ORIGIN_ID = "*fleet*";

    private String fromMarketId;
    private String fromName;
    private String toMarketId;
    private String toName;
    private List<TradeAction> actions;
    private float hyperspaceLY;
    private float hyperspaceDays;
    private float inSystemDays;
    private float totalDays;
    private float hyperspaceFuel;
    private float supplyUnits;
    private float fuelCostCredits;
    private float supplyCostCredits;
    private float fuelPurchased;
    private float fuelPurchaseCost;
    private float supplyPurchased;
    private float supplyPurchaseCost;
    private float cashAfter;
    private float cargoUsedOnHop;
    private float fuelAfter;

    /** XStream / campaign save. */
    @SuppressWarnings("unused")
    private RouteLeg() {
        this.actions = new ArrayList<>();
    }

    public RouteLeg(String fromMarketId, String fromName, String toMarketId, String toName,
                    List<TradeAction> actions,
                    float hyperspaceLY, float hyperspaceDays, float inSystemDays, float totalDays,
                    float hyperspaceFuel, float supplyUnits,
                    float fuelCostCredits, float supplyCostCredits,
                    float fuelPurchased, float fuelPurchaseCost,
                    float supplyPurchased, float supplyPurchaseCost,
                    float cashAfter, float cargoUsedOnHop, float fuelAfter) {
        this.fromMarketId = fromMarketId;
        this.fromName = fromName;
        this.toMarketId = toMarketId;
        this.toName = toName;
        this.actions = new ArrayList<>(actions == null ? Collections.emptyList() : actions);
        this.hyperspaceLY = hyperspaceLY;
        this.hyperspaceDays = hyperspaceDays;
        this.inSystemDays = inSystemDays;
        this.totalDays = totalDays;
        this.hyperspaceFuel = hyperspaceFuel;
        this.supplyUnits = supplyUnits;
        this.fuelCostCredits = fuelCostCredits;
        this.supplyCostCredits = supplyCostCredits;
        this.fuelPurchased = fuelPurchased;
        this.fuelPurchaseCost = fuelPurchaseCost;
        this.supplyPurchased = supplyPurchased;
        this.supplyPurchaseCost = supplyPurchaseCost;
        this.cashAfter = cashAfter;
        this.cargoUsedOnHop = cargoUsedOnHop;
        this.fuelAfter = fuelAfter;
    }

    /** Fleet → first market. Empty cargo, buy after arrival. Not a loop return. */
    public boolean isPositioning() {
        return FLEET_ORIGIN_ID.equals(fromMarketId);
    }

    public boolean isEmptyCargo() {
        return actions == null || actions.isEmpty();
    }

    public boolean isAlreadyThere() {
        return isPositioning() && totalDays < 0.05f && hyperspaceFuel < 0.1f;
    }

    public String getFromMarketId() {
        return fromMarketId;
    }

    public String getFromName() {
        return fromName;
    }

    public String getToMarketId() {
        return toMarketId;
    }

    public String getToName() {
        return toName;
    }

    public List<TradeAction> getActions() {
        if (actions == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(actions);
    }

    public float getHyperspaceLY() {
        return hyperspaceLY;
    }

    public float getHyperspaceDays() {
        return hyperspaceDays;
    }

    public float getInSystemDays() {
        return inSystemDays;
    }

    /** Estimated days — not a clock. */
    public float getTotalDays() {
        return totalDays;
    }

    public float getHyperspaceFuel() {
        return hyperspaceFuel;
    }

    public float getSupplyUnits() {
        return supplyUnits;
    }

    public float getFuelCostCredits() {
        return fuelCostCredits;
    }

    public float getSupplyCostCredits() {
        return supplyCostCredits;
    }

    public float getOpsCost() {
        return fuelCostCredits + supplyCostCredits;
    }

    public float getFuelPurchased() {
        return fuelPurchased;
    }

    public float getFuelPurchaseCost() {
        return fuelPurchaseCost;
    }

    public float getSupplyPurchased() {
        return supplyPurchased;
    }

    public float getSupplyPurchaseCost() {
        return supplyPurchaseCost;
    }

    public float getGrossProfit() {
        float g = 0f;
        for (TradeAction a : getActions()) {
            g += a.getGrossProfit();
        }
        return g;
    }

    public float getBuyCost() {
        float b = 0f;
        for (TradeAction a : getActions()) {
            b += a.getBuyCost();
        }
        return b;
    }

    public float getSellRevenue() {
        float s = 0f;
        for (TradeAction a : getActions()) {
            s += a.getSellRevenue();
        }
        return s;
    }

    public float getCashAfter() {
        return cashAfter;
    }

    public float getCargoUsedOnHop() {
        return cargoUsedOnHop;
    }

    public float getFuelAfter() {
        return fuelAfter;
    }
}
