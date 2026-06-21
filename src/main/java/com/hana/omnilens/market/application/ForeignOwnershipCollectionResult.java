package com.hana.omnilens.market.application;

import java.time.LocalDate;
import java.util.List;

public record ForeignOwnershipCollectionResult(
        LocalDate baseDate,
        int requestedCount,
        int refreshedCount,
        int failedCount,
        String source,
        String status,
        List<StockResult> stockResults
) {

    public record StockResult(
            String stockCode,
            boolean refreshed,
            String status,
            String errorMessage
    ) {
    }
}
