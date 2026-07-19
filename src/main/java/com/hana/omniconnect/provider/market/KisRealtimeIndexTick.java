package com.hana.omniconnect.provider.market;

import java.math.BigDecimal;
import java.time.Instant;

public record KisRealtimeIndexTick(
        String indexCode,
        String indexName,
        String market,
        String tradeTime,
        BigDecimal currentValue,
        String changeSign,
        BigDecimal changeValue,
        BigDecimal changeRate,
        long accumulatedVolume,
        long accumulatedTradingValue,
        BigDecimal openValue,
        BigDecimal highValue,
        BigDecimal lowValue,
        BigDecimal tickChange,
        Instant marketDataTime,
        String source
) {
}
