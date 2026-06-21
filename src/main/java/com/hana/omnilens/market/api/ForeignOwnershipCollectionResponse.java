package com.hana.omnilens.market.api;

import java.time.LocalDate;
import java.util.List;

import com.hana.omnilens.market.application.ForeignOwnershipCollectionResult;

public record ForeignOwnershipCollectionResponse(
        LocalDate baseDate,
        int requestedCount,
        int refreshedCount,
        int failedCount,
        String source,
        String status,
        List<StockResultResponse> stockResults
) {

    public static ForeignOwnershipCollectionResponse from(ForeignOwnershipCollectionResult result) {
        return new ForeignOwnershipCollectionResponse(
                result.baseDate(),
                result.requestedCount(),
                result.refreshedCount(),
                result.failedCount(),
                result.source(),
                result.status(),
                result.stockResults().stream()
                        .map(StockResultResponse::from)
                        .toList());
    }

    public record StockResultResponse(
            String stockCode,
            boolean refreshed,
            String status,
            String errorMessage
    ) {

        private static StockResultResponse from(ForeignOwnershipCollectionResult.StockResult result) {
            return new StockResultResponse(
                    result.stockCode(),
                    result.refreshed(),
                    result.status(),
                    result.errorMessage());
        }
    }
}
