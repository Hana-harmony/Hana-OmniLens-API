package com.hana.omniconnect.market.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketIndexQuote(
        String indexCode,
        String indexName,
        String market,
        BigDecimal currentValue,
        String changeSign,
        BigDecimal changeValue,
        BigDecimal changeRate,
        long accumulatedVolume,
        long accumulatedTradingValue,
        BigDecimal openValue,
        BigDecimal highValue,
        BigDecimal lowValue,
        Instant marketDataTime,
        String source
) {
}
