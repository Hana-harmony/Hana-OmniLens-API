package com.hana.omniconnect.market.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

public record MarketIntradayPrice(
        String stockCode,
        LocalDateTime bucketStart,
        String market,
        BigDecimal openPriceKrw,
        BigDecimal highPriceKrw,
        BigDecimal lowPriceKrw,
        BigDecimal closePriceKrw,
        long tradingVolume,
        BigDecimal tradingValueKrw,
        String source,
        Instant collectedAt
) {
}
