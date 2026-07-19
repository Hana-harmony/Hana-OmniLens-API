package com.hana.omniconnect.provider.market;

import java.math.BigDecimal;
import java.time.LocalDate;

public record KrxOpenApiDailyTrade(
        LocalDate baseDate,
        String isinCode,
        String stockCode,
        String stockName,
        String market,
        BigDecimal openingPriceKrw,
        BigDecimal highPriceKrw,
        BigDecimal lowPriceKrw,
        BigDecimal closingPriceKrw,
        BigDecimal changeRate,
        long tradingVolume,
        BigDecimal tradingValueKrw
) {
}
