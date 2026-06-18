package com.hana.omnilens.provider.market;

public enum KisRealtimeTransaction {
    TRADE("H0STCNT0"),
    ORDERBOOK("H0STASP0");

    private final String trId;

    KisRealtimeTransaction(String trId) {
        this.trId = trId;
    }

    public String trId() {
        return trId;
    }
}
