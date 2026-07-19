package com.hana.omniconnect.market.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

public record MarketIndexIntradayPrice(
        String indexCode,
        String indexName,
        String market,
        LocalDateTime bucketStart,
        BigDecimal openValue,
        BigDecimal highValue,
        BigDecimal lowValue,
        BigDecimal closeValue,
        long tradingVolume,
        BigDecimal tradingValueKrw,
        String source,
        Instant collectedAt
) {
}
