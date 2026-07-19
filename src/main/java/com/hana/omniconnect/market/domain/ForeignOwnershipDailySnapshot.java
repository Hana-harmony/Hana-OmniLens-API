package com.hana.omniconnect.market.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ForeignOwnershipDailySnapshot(
        String stockCode,
        LocalDate baseDate,
        long foreignOwnedQuantity,
        BigDecimal foreignOwnershipRate,
        long foreignLimitQuantity,
        BigDecimal foreignLimitExhaustionRate,
        String source,
        Instant collectedAt
) {
}
