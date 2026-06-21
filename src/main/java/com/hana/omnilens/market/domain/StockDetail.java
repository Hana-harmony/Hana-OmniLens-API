package com.hana.omnilens.market.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record StockDetail(
        String stockCode,
        String stockName,
        String stockNameEn,
        String market,
        String sector,
        BigDecimal currentPriceKrw,
        BigDecimal changeRate,
        long volume,
        String localCurrency,
        BigDecimal localCurrencyPrice,
        Instant marketDataTime,
        long foreignOwnedQuantity,
        BigDecimal foreignOwnershipRate,
        BigDecimal foreignLimitExhaustionRate,
        BigDecimal predictedForeignOwnershipRateMin,
        BigDecimal predictedForeignOwnershipRateMax,
        BigDecimal predictedForeignLimitExhaustionRateMin,
        BigDecimal predictedForeignLimitExhaustionRateMax,
        String foreignOwnershipPredictionConfidenceLevel,
        BigDecimal foreignOwnershipPredictionConfidenceScore,
        String foreignOwnershipPredictionModelVersion,
        LocalDate foreignOwnershipBaseDate,
        boolean viActive,
        boolean singlePriceTrading,
        String priceLimitState,
        boolean tradingHalted,
        boolean orderable,
        String source
) {
}
