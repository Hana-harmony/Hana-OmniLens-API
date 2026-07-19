package com.hana.omniconnect.market.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderBook(
        String stockCode,
        List<OrderBookLevel> asks,
        List<OrderBookLevel> bids,
        Instant marketDataTime,
        String source
) {
    public record OrderBookLevel(BigDecimal priceKrw, long quantity) {
    }
}
