package com.hana.omnilens.market.stream;

import java.time.Instant;

public record MarketQuoteStreamStats(
        int sessionCount,
        long publishedCount,
        long failedDeliveryCount,
        Instant lastMarketDataTime
) {
}
