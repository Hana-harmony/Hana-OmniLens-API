package com.hana.omniconnect.market.stream;

import java.time.Instant;
import java.util.List;

public record MarketQuoteReplayRequest(
        String type,
        String currency,
        Instant after,
        List<String> stockCodes
) {
    boolean isReplayRequest() {
        return "QUOTE_STREAM_REPLAY".equals(type);
    }

    boolean isSubscribeRequest() {
        return "QUOTE_STREAM_SUBSCRIBE".equals(type);
    }

    String replayCurrency() {
        return currency == null || currency.isBlank() ? "USD" : currency.toUpperCase();
    }

    List<String> requestedStockCodes() {
        return stockCodes == null ? List.of() : List.copyOf(stockCodes);
    }
}
