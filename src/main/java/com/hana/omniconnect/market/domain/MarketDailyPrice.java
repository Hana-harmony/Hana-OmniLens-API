package com.hana.omniconnect.market.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record MarketDailyPrice(
        String stockCode,
        LocalDate tradeDate,
        String market,
        BigDecimal openPriceKrw,
        BigDecimal highPriceKrw,
        BigDecimal lowPriceKrw,
        BigDecimal closePriceKrw,
        BigDecimal changeRate,
        long tradingVolume,
        BigDecimal tradingValueKrw,
        BigDecimal adjustedClosePriceKrw,
        String source,
        Instant collectedAt
) {
}
