package com.hana.omniconnect.market.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record Orderability(
        String stockCode,
        String market,
        String side,
        long quantity,
        boolean orderable,
        String orderBlockedReason,
        boolean foreignLimitExceeded,
        BigDecimal currentForeignLimitExhaustionRate,
        BigDecimal predictedForeignLimitExhaustionRate,
        ForeignOwnershipPrediction foreignOwnershipPrediction,
        LocalDate foreignOwnershipBaseDate,
        boolean viActive,
        boolean singlePriceTrading,
        String priceLimitState,
        boolean tradingHalted,
        Instant checkedAt,
        String source
) {
}
