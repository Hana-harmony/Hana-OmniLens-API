package com.hana.omniconnect.provider.market;

import java.math.BigDecimal;
import java.util.List;

public record KisRestOrderBookSnapshot(
        String stockCode,
        List<Level> asks,
        List<Level> bids
) {
    public record Level(BigDecimal priceKrw, long quantity) {
    }
}
