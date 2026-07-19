package com.hana.omniconnect.provider.market;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PublicDataStockPriceSnapshot(
        String stockCode,
        String stockName,
        String market,
        BigDecimal closingPriceKrw,
        BigDecimal changeRate,
        long volume,
        LocalDate baseDate
) {
}
