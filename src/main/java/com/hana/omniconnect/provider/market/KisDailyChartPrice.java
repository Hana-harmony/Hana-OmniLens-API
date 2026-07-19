package com.hana.omniconnect.provider.market;

import java.math.BigDecimal;
import java.time.LocalDate;

public record KisDailyChartPrice(
        LocalDate tradeDate,
        BigDecimal openPriceKrw,
        BigDecimal highPriceKrw,
        BigDecimal lowPriceKrw,
        BigDecimal closePriceKrw,
        BigDecimal changeRate,
        long tradingVolume,
        BigDecimal tradingValueKrw
) {
}
