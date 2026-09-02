package org.tradeplanner.data;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.fleet.FleetLogisticsAPI;

/**
 * Player fleet snapshot at calculate/refresh time. Fuel cost is per light-year, not per day.
 */
public final class FleetState {

    private final float credits;
    private final float cargoMax;
    private final float cargoUsed;
    private final float cargoLeft;
    private final float fuel;
    private final float fuelMax;
    private final float supplies;
    private final float supplyCargoSpace;
    private final float burnLevel;
    private final float suppliesPerDay;
    private final float fuelPerLightYear;
    private final boolean inHyperspace;
    private final boolean transponderOn;
    private final String locationName;

    private FleetState(float credits, float cargoMax, float cargoUsed, float cargoLeft,
                       float fuel, float fuelMax, float supplies, float supplyCargoSpace,
                       float burnLevel, float suppliesPerDay, float fuelPerLightYear,
                       boolean inHyperspace, boolean transponderOn,
                       String locationName) {
        this.credits = credits;
        this.cargoMax = cargoMax;
        this.cargoUsed = cargoUsed;
        this.cargoLeft = cargoLeft;
        this.fuel = fuel;
        this.fuelMax = fuelMax;
        this.supplies = supplies;
        this.supplyCargoSpace = supplyCargoSpace;
        this.burnLevel = burnLevel;
        this.suppliesPerDay = suppliesPerDay;
        this.fuelPerLightYear = fuelPerLightYear;
        this.inHyperspace = inHyperspace;
        this.transponderOn = transponderOn;
        this.locationName = locationName;
    }

    public static FleetState fromPlayer() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        CargoAPI cargo = fleet.getCargo();
        FleetLogisticsAPI logistics = fleet.getLogistics();
        String locName = fleet.getContainingLocation() == null
                ? "?"
                : fleet.getContainingLocation().getName();
        float burn = fleet.getFleetData().getBurnLevel();
        return new FleetState(
                cargo.getCredits().get(),
                cargo.getMaxCapacity(),
                cargo.getSpaceUsed(),
                cargo.getSpaceLeft(),
                cargo.getFuel(),
                cargo.getMaxFuel(),
                cargo.getSupplies(),
                LogisticsReserve.supplyCargoSpace(),
                burn,
                logistics.getTotalSuppliesPerDay(),
                logistics.getFuelCostPerLightYear(),
                fleet.isInHyperspace(),
                fleet.isTransponderOn(),
                locName
        );
    }

    public CampaignFleetAPI getFleet() {
        return Global.getSector().getPlayerFleet();
    }

    public SectorEntityToken getLocationToken() {
        return getFleet();
    }

    public float getCredits() {
        return credits;
    }

    public float getCargoMax() {
        return cargoMax;
    }

    public float getCargoUsed() {
        return cargoUsed;
    }

    public float getCargoLeft() {
        return cargoLeft;
    }

    public float getFuel() {
        return fuel;
    }

    public float getFuelMax() {
        return fuelMax;
    }

    public float getSupplies() {
        return supplies;
    }

    public float getSupplyCargoSpace() {
        return supplyCargoSpace <= 0.0001f ? 1f : supplyCargoSpace;
    }

    public float getBurnLevel() {
        return burnLevel;
    }

    public float getSuppliesPerDay() {
        return suppliesPerDay;
    }

    public float getFuelPerLightYear() {
        return fuelPerLightYear;
    }

    public boolean isInHyperspace() {
        return inHyperspace;
    }

    public boolean isTransponderOn() {
        return transponderOn;
    }

    public String getLocationName() {
        return locationName;
    }
}
