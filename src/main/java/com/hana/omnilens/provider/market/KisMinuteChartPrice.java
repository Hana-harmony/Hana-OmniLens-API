package com.hana.omnilens.provider.market;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record KisMinuteChartPrice(
        LocalDateTime bucketStart,
        BigDecimal openPriceKrw,
        BigDecimal highPriceKrw,
        BigDecimal lowPriceKrw,
        BigDecimal closePriceKrw,
        long volume,
        BigDecimal tradingValueKrw
) {
}
