package com.hana.omnilens.provider.market;

import java.math.BigDecimal;
import java.util.List;

public record KisRealtimeOrderBookSnapshot(
        String stockCode,
        String marketTime,
        List<Level> asks,
        List<Level> bids,
        long accumulatedVolume
) {
    public record Level(BigDecimal priceKrw, long quantity) {
    }
}
