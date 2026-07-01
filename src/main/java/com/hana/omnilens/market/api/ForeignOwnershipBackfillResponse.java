package com.hana.omnilens.market.api;

import java.time.LocalDate;
import java.util.List;

import com.hana.omnilens.market.application.ForeignOwnershipBackfillResult;

public record ForeignOwnershipBackfillResponse(
        LocalDate fromDate,
        LocalDate toDate,
        int requestedStockCount,
        int missingDateCount,
        int savedCount,
        int failedDateCount,
        String source,
        String status,
        List<StockBackfillResultResponse> stockResults
) {

    public static ForeignOwnershipBackfillResponse from(ForeignOwnershipBackfillResult result) {
        return new ForeignOwnershipBackfillResponse(
                result.fromDate(),
                result.toDate(),
                result.requestedStockCount(),
                result.missingDateCount(),
                result.savedCount(),
                result.failedDateCount(),
                result.source(),
                result.status(),
                result.stockResults().stream()
                        .map(StockBackfillResultResponse::from)
                        .toList());
    }

    public record StockBackfillResultResponse(
            String stockCode,
            int missingDateCount,
            int savedCount,
            String status,
            String errorMessage
    ) {

        private static StockBackfillResultResponse from(ForeignOwnershipBackfillResult.StockBackfillResult result) {
            return new StockBackfillResultResponse(
                    result.stockCode(),
                    result.missingDateCount(),
                    result.savedCount(),
                    result.status(),
                    result.errorMessage());
        }
    }
}
