package com.hana.omnilens.market.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record MarketQuote(
        String stockCode,
        String stockName,
        String stockNameEn,
        String market,
        BigDecimal currentPriceKrw,
        BigDecimal changeRate,
        long volume,
        BigDecimal executionPriceKrw,
        String marketSession,
        BigDecimal afterHoursPriceKrw,
        BigDecimal afterHoursLocalCurrencyPrice,
        BigDecimal afterHoursChangeRate,
        Long afterHoursVolume,
        Instant afterHoursMarketDataTime,
        String baseCurrency,
        BigDecimal localCurrencyPrice,
        String localCurrency,
        BigDecimal fxRate,
        Instant fxRateTime,
        String fxRateSource,
        boolean fxStale,
        long foreignOwnedQuantity,
        BigDecimal foreignOwnershipRate,
        BigDecimal foreignLimitExhaustionRate,
        LocalDate foreignOwnershipBaseDate,
        Instant marketDataTime,
        boolean viActive,
        boolean singlePriceTrading,
        boolean tradingHalted,
        boolean circuitBreakerActive,
        String tradingHaltReason,
        String source
) {
}
