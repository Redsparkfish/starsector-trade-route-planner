package org.tradeplanner.data;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import org.tradeplanner.config.PlannerConfig;

/**
 * Operational supplies/fuel that must not be sold (and should be topped up when buying).
 * Fuel reserve is in hyperspace light-years via {@code getFuelCostPerLightYear()}.
 * Excess above the floor may be bought/sold as a trade good (tank, not cargo).
 */
public final class LogisticsReserve {

    private LogisticsReserve() {
    }

    public static int supplyFloor(PlannerConfig cfg, float suppliesPerDay) {
        if (cfg == null) {
            return 0;
        }
        int days = cfg.getReserveSupplyDays();
        if (days <= 0 || suppliesPerDay <= 0.0001f) {
            return 0;
        }
        return (int) Math.ceil(suppliesPerDay * days);
    }

    public static int fuelFloor(PlannerConfig cfg, float fuelPerLY, float fuelMax) {
        if (cfg == null) {
            return 0;
        }
        int ly = cfg.getReserveFuelLY();
        if (ly <= 0 || fuelPerLY <= 0.0001f) {
            return 0;
        }
        int qty = (int) Math.ceil(fuelPerLY * ly);
        if (fuelMax > 0.0001f) {
            qty = Math.min(qty, (int) Math.floor(fuelMax));
        }
        return Math.max(0, qty);
    }

    public static int supplyFloor(PlannerConfig cfg, FleetState fleet) {
        return fleet == null ? 0 : supplyFloor(cfg, fleet.getSuppliesPerDay());
    }

    public static int fuelFloor(PlannerConfig cfg, FleetState fleet) {
        return fleet == null ? 0 : fuelFloor(cfg, fleet.getFuelPerLightYear(), fleet.getFuelMax());
    }

    /** Tank room above the reserve that can be used to carry fuel as a trade good. */
    public static float tradeFuelRoom(PlannerConfig cfg, FleetState fleet) {
        if (fleet == null) {
            return 0f;
        }
        return Math.max(0f, fleet.getFuelMax() - fuelFloor(cfg, fleet));
    }

    public static int supplyFloorLive(PlannerConfig cfg, CampaignFleetAPI fleet) {
        if (fleet == null || fleet.getLogistics() == null) {
            return 0;
        }
        return supplyFloor(cfg, fleet.getLogistics().getTotalSuppliesPerDay());
    }

    public static int fuelFloorLive(PlannerConfig cfg, CampaignFleetAPI fleet) {
        if (fleet == null) {
            return 0;
        }
        float perLY = 0f;
        try {
            if (fleet.getLogistics() != null) {
                perLY = fleet.getLogistics().getFuelCostPerLightYear();
            }
        } catch (Exception ignored) {
        }
        float max = 0f;
        if (fleet.getCargo() != null) {
            max = fleet.getCargo().getMaxFuel();
        }
        return fuelFloor(cfg, perLY, max);
    }

    public static float supplyCargoSpace() {
        try {
            CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(Commodities.SUPPLIES);
            if (spec != null && spec.getCargoSpace() > 0.0001f) {
                return spec.getCargoSpace();
            }
        } catch (Exception ignored) {
        }
        return 1f;
    }

    /**
     * Free cargo after leaving room to top supplies up to the reserve. Extra supplies already
     * in the hold keep occupying space (they are not auto-sold).
     */
    public static float tradeCargoLeft(FleetState fleet, PlannerConfig cfg) {
        if (fleet == null) {
            return 0f;
        }
        int floor = supplyFloor(cfg, fleet);
        float shortfall = Math.max(0f, floor - fleet.getSupplies());
        return Math.max(0f, fleet.getCargoLeft() - shortfall * fleet.getSupplyCargoSpace());
    }

    public static int maxSellQty(CargoAPI cargo, String id, int planQty, PlannerConfig cfg,
                                 CampaignFleetAPI fleet) {
        int have = held(cargo, id);
        int floor = 0;
        if (Commodities.SUPPLIES.equals(id)) {
            floor = supplyFloorLive(cfg, fleet);
        } else if (Commodities.FUEL.equals(id)) {
            floor = fuelFloorLive(cfg, fleet);
        }
        int sellable = Math.max(0, have - floor);
        return Math.min(Math.max(0, planQty), sellable);
    }

    public static boolean needsRestock(PlannerConfig cfg, CampaignFleetAPI fleet) {
        if (cfg == null || fleet == null || fleet.getCargo() == null) {
            return false;
        }
        CargoAPI cargo = fleet.getCargo();
        return cargo.getSupplies() + 0.5f < supplyFloorLive(cfg, fleet)
                || cargo.getFuel() + 0.5f < fuelFloorLive(cfg, fleet);
    }

    private static int held(CargoAPI cargo, String id) {
        if (cargo == null || id == null) {
            return 0;
        }
        if (Commodities.FUEL.equals(id)) {
            return (int) cargo.getFuel();
        }
        if (Commodities.SUPPLIES.equals(id)) {
            return (int) cargo.getSupplies();
        }
        return (int) cargo.getCommodityQuantity(id);
    }
}
