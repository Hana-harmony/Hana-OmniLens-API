package com.hana.omnilens.market.api;

import java.time.LocalDate;

import com.hana.omnilens.market.application.MarketHistoryCollectionResult;

public record MarketHistoryCollectionResponse(
        LocalDate baseDate,
        int requestedCount,
        int savedCount,
        String source
) {

    public static MarketHistoryCollectionResponse from(MarketHistoryCollectionResult result) {
        return new MarketHistoryCollectionResponse(
                result.baseDate(),
                result.requestedCount(),
                result.savedCount(),
                result.source());
    }
}
