package com.hana.omnilens.provider.market;

import java.math.BigDecimal;

public record KisCurrentPriceSnapshot(
        String stockCode,
        String stockName,
        BigDecimal currentPriceKrw,
        BigDecimal changeRate,
        long volume
) {
}
