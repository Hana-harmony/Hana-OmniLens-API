package com.hana.omnilens.provider.market;

import java.math.BigDecimal;
import java.time.LocalDate;

public record KisRealtimeTradeTick(
        String stockCode,
        String tradeTime,
        BigDecimal currentPriceKrw,
        BigDecimal changeRate,
        BigDecimal askPrice1Krw,
        BigDecimal bidPrice1Krw,
        long executionVolume,
        long accumulatedVolume,
        LocalDate businessDate
) {
}
