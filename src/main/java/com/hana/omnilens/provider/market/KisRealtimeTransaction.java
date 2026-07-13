package com.hana.omnilens.provider.market;

public enum KisRealtimeTransaction {
    TRADE("H0STCNT0"),
    ORDERBOOK("H0STASP0"),
    AFTER_HOURS_TRADE("H0STOUP0"),
    AFTER_HOURS_ORDERBOOK("H0STOAA0"),
    MARKET_STATUS("H0STMKO0"),
    INDEX_TRADE("H0UPCNT0");

    private final String trId;

    KisRealtimeTransaction(String trId) {
        this.trId = trId;
    }

    public String trId() {
        return trId;
    }
}
