package com.hana.omnilens.market.application;

import java.util.List;

public record KisRealtimeDynamicSubscriptionResult(
        boolean enabled,
        int subscriptionFrameLimit,
        int activeSubscriptionFrameCount,
        List<String> subscribedStockCodes,
        List<String> alreadySubscribedStockCodes,
        List<String> rotatedOutStockCodes,
        List<String> unsupportedStockCodes,
        List<String> rejectedStockCodes
) {

    public KisRealtimeDynamicSubscriptionResult {
        subscribedStockCodes = subscribedStockCodes == null ? List.of() : List.copyOf(subscribedStockCodes);
        alreadySubscribedStockCodes = alreadySubscribedStockCodes == null ? List.of() : List.copyOf(alreadySubscribedStockCodes);
        rotatedOutStockCodes = rotatedOutStockCodes == null ? List.of() : List.copyOf(rotatedOutStockCodes);
        unsupportedStockCodes = unsupportedStockCodes == null ? List.of() : List.copyOf(unsupportedStockCodes);
        rejectedStockCodes = rejectedStockCodes == null ? List.of() : List.copyOf(rejectedStockCodes);
    }
}
