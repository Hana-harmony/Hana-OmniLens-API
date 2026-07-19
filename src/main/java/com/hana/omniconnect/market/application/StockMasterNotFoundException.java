package com.hana.omniconnect.market.application;

public class StockMasterNotFoundException extends RuntimeException {

    private final String stockCode;

    public StockMasterNotFoundException(String stockCode) {
        super("Stock master row not found: " + stockCode);
        this.stockCode = stockCode;
    }

    public String stockCode() {
        return stockCode;
    }
}
