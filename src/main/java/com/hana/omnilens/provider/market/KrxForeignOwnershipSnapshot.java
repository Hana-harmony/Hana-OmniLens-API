package com.hana.omnilens.provider.market;

import java.math.BigDecimal;
import java.time.LocalDate;

public record KrxForeignOwnershipSnapshot(
        String stockCode,
        long foreignOwnedQuantity,
        BigDecimal foreignOwnershipRate,
        long foreignLimitQuantity,
        BigDecimal foreignLimitExhaustionRate,
        LocalDate baseDate
) {
}
