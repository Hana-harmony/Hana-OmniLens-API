package com.hana.omniconnect.provider.market;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

public record KisCurrentPriceSnapshot(
        String stockCode,
        String stockName,
        BigDecimal currentPriceKrw,
        BigDecimal changeRate,
        long volume,
        Long foreignOwnedQuantity,
        BigDecimal foreignOwnershipRate,
        Long foreignLimitQuantity,
        BigDecimal foreignLimitExhaustionRate
) {

    public KisCurrentPriceSnapshot(
            String stockCode,
            String stockName,
            BigDecimal currentPriceKrw,
            BigDecimal changeRate,
            long volume) {
        this(stockCode, stockName, currentPriceKrw, changeRate, volume, null, null, null, null);
    }

    public Optional<ForeignOwnershipSnapshot> foreignOwnershipSnapshot(LocalDate baseDate) {
        if (foreignOwnedQuantity == null || foreignLimitQuantity == null || foreignLimitExhaustionRate == null) {
            return Optional.empty();
        }
        BigDecimal ownershipRate = foreignOwnershipRate == null
                ? foreignLimitExhaustionRate
                : foreignOwnershipRate;
        return Optional.of(new ForeignOwnershipSnapshot(
                stockCode,
                foreignOwnedQuantity,
                ownershipRate.setScale(4, RoundingMode.HALF_UP),
                foreignLimitQuantity,
                foreignLimitExhaustionRate.setScale(4, RoundingMode.HALF_UP),
                baseDate));
    }
}
