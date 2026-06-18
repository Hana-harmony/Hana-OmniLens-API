package com.hana.omnilens.market.stream;

import java.time.Instant;

public record MarketQuoteReplayRequest(
        String type,
        String currency,
        Instant after
) {
    boolean isReplayRequest() {
        return "QUOTE_STREAM_REPLAY".equals(type);
    }

    String replayCurrency() {
        return currency == null || currency.isBlank() ? "USD" : currency.toUpperCase();
    }
}
