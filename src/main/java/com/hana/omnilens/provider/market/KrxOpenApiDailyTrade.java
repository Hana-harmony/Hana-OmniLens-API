package com.hana.omnilens.provider.market;

import java.math.BigDecimal;
import java.time.LocalDate;

public record KrxOpenApiDailyTrade(
        LocalDate baseDate,
        String isinCode,
        String stockCode,
        String stockName,
        String market,
        BigDecimal closingPriceKrw,
        BigDecimal changeRate,
        long tradingVolume
) {
}
