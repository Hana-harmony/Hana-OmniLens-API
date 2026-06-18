package com.hana.omnilens.market.application;

public record RealtimeMarketDataIngestionResult(
        Type type,
        String stockCode
) {
    public enum Type {
        TRADE,
        ORDERBOOK,
        IGNORED
    }

    static RealtimeMarketDataIngestionResult trade(String stockCode) {
        return new RealtimeMarketDataIngestionResult(Type.TRADE, stockCode);
    }

    static RealtimeMarketDataIngestionResult orderBook(String stockCode) {
        return new RealtimeMarketDataIngestionResult(Type.ORDERBOOK, stockCode);
    }

    static RealtimeMarketDataIngestionResult ignored() {
        return new RealtimeMarketDataIngestionResult(Type.IGNORED, "");
    }
}
