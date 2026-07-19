package com.hana.omniconnect.market.api;

import java.time.LocalDate;
import java.util.List;

import com.hana.omniconnect.market.application.MarketHistoryCollectionResult;

public record MarketHistoryCollectionResponse(
        LocalDate baseDate,
        int requestedCount,
        int savedCount,
        String source,
        String status,
        List<MarketResultResponse> marketResults
) {

    public static MarketHistoryCollectionResponse from(MarketHistoryCollectionResult result) {
        return new MarketHistoryCollectionResponse(
                result.baseDate(),
                result.requestedCount(),
                result.savedCount(),
                result.source(),
                result.status(),
                result.marketResults().stream()
                        .map(MarketResultResponse::from)
                        .toList());
    }

    public record MarketResultResponse(
            String market,
            int requestedCount,
            int savedCount,
            String status,
            String errorMessage
    ) {

        private static MarketResultResponse from(MarketHistoryCollectionResult.MarketResult result) {
            return new MarketResultResponse(
                    result.market(),
                    result.requestedCount(),
                    result.savedCount(),
                    result.status(),
                    result.errorMessage());
        }
    }
}
