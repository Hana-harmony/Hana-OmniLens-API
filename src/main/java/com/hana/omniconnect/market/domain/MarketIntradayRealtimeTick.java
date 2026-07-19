package com.hana.omniconnect.market.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

public record MarketIntradayRealtimeTick(
        String stockCode,
        LocalDateTime bucketStart,
        String market,
        BigDecimal priceKrw,
        long executionVolume,
        BigDecimal tradingValueKrw,
        String source,
        Instant collectedAt
) {
}
