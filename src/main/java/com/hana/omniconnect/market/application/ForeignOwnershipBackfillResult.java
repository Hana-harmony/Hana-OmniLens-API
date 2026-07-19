package com.hana.omniconnect.market.application;

import java.time.LocalDate;
import java.util.List;

public record ForeignOwnershipBackfillResult(
        LocalDate fromDate,
        LocalDate toDate,
        int requestedStockCount,
        int missingDateCount,
        int savedCount,
        int failedDateCount,
        String source,
        String status,
        List<StockBackfillResult> stockResults
) {

    public record StockBackfillResult(
            String stockCode,
            int missingDateCount,
            int savedCount,
            String status,
            String errorMessage
    ) {
    }
}
