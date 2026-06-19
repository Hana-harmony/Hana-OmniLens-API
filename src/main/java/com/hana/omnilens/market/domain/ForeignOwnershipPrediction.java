package com.hana.omnilens.market.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ForeignOwnershipPrediction(
        BigDecimal minForeignLimitExhaustionRate,
        BigDecimal baseForeignLimitExhaustionRate,
        BigDecimal maxForeignLimitExhaustionRate,
        BigDecimal orderImpactRate,
        BigDecimal intradayUncertaintyRate,
        long observedIntradayVolume,
        LocalDate baseDate,
        Instant calculatedAt,
        String confidenceLevel,
        String source
) {
}
