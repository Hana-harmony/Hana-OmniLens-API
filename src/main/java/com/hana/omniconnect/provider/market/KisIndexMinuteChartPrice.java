package com.hana.omniconnect.provider.market;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record KisIndexMinuteChartPrice(
        LocalDateTime bucketStart,
        BigDecimal openValue,
        BigDecimal highValue,
        BigDecimal lowValue,
        BigDecimal closeValue,
        long volume,
        BigDecimal tradingValueKrw
) {
}
