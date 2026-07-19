package com.hana.omniconnect.provider.market;

public record KisRealtimeSubscriptionRequestResult(
        String stockCode,
        String session,
        String status,
        String message
) {
}
