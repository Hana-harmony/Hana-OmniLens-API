package com.hana.omnilens.market.application;

public record RealtimeMarketDataIngestionResult(
        Type type,
        String code
) {
    public enum Type {
        TRADE,
        ORDERBOOK,
        MARKET_STATUS,
        INDEX,
        IGNORED
    }

    static RealtimeMarketDataIngestionResult trade(String stockCode) {
        return new RealtimeMarketDataIngestionResult(Type.TRADE, stockCode);
    }

    static RealtimeMarketDataIngestionResult orderBook(String stockCode) {
        return new RealtimeMarketDataIngestionResult(Type.ORDERBOOK, stockCode);
    }

    static RealtimeMarketDataIngestionResult marketStatus(String stockCode) {
        return new RealtimeMarketDataIngestionResult(Type.MARKET_STATUS, stockCode);
    }

    static RealtimeMarketDataIngestionResult index(String indexCode) {
        return new RealtimeMarketDataIngestionResult(Type.INDEX, indexCode);
    }

    static RealtimeMarketDataIngestionResult ignored() {
        return new RealtimeMarketDataIngestionResult(Type.IGNORED, "");
    }

    public String stockCode() {
        return code;
    }
}
