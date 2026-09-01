package org.tradeplanner.service;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.tradeplanner.config.PlannerConfig;

/**
 * Batch quotes via {@code MarketAPI.getSupplyPrice}/{@code getDemandPrice}.
 * Never multiply a unit price by quantity — prices are quantity-dependent.
 *
 * <p>In-game checks still needed (docs/starsector-dev.md §9):
 * <ol>
 *   <li>Does {@code getSupplyPrice(..., true)} already include open-market tariff vs the trade UI total?</li>
 *   <li>Is sell taxed as {@code price × (1 − tariff)} or a separate UI line?</li>
 *   <li>{@code isPlayerPrice} is NOT a black-market flag — always pass {@code true} for player quotes.</li>
 * </ol>
 * Toggle {@link PlannerConfig#assumeApiPriceExcludesTariff()} if the API price is already post-tariff.
 */
public final class PriceQuoter {

    private PriceQuoter() {
    }

    public static float quoteBuy(MarketAPI market, String commodityId, double quantity,
                                 boolean blackMarket, PlannerConfig config) {
        if (quantity <= 0d || market == null) {
            return 0f;
        }
        float raw = market.getSupplyPrice(commodityId, quantity, true);
        return applyTariffOnBuy(raw, tariff(market, blackMarket), config);
    }

    public static float quoteSell(MarketAPI market, String commodityId, double quantity,
                                  boolean blackMarket, PlannerConfig config) {
        if (quantity <= 0d || market == null) {
            return 0f;
        }
        float raw = market.getDemandPrice(commodityId, quantity, true);
        return applyTariffOnSell(raw, tariff(market, blackMarket), config);
    }

    public static float tariff(MarketAPI market, boolean blackMarket) {
        if (blackMarket) {
            return 0f;
        }
        if (market.hasSubmarket("open_market")) {
            return market.getSubmarket("open_market").getTariff();
        }
        return market.getTariff().getModifiedValue();
    }

    private static float applyTariffOnBuy(float raw, float tariff, PlannerConfig config) {
        if (config != null && config.assumeApiPriceExcludesTariff()) {
            return raw * (1f + tariff);
        }
        return raw;
    }

    private static float applyTariffOnSell(float raw, float tariff, PlannerConfig config) {
        if (config != null && config.assumeApiPriceExcludesTariff()) {
            return raw * (1f - tariff);
        }
        return raw;
    }
}
