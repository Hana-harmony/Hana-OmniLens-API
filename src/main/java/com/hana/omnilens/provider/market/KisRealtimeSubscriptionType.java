package com.hana.omnilens.provider.market;

public enum KisRealtimeSubscriptionType {
    SUBSCRIBE("1"),
    UNSUBSCRIBE("2");

    private final String code;

    KisRealtimeSubscriptionType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
