package com.hana.omnilens.market.application;

import java.time.Instant;
import java.util.List;

public record ForeignOwnershipPredictionPrecomputeResult(
        int requestedStockCount,
        int precomputedCount,
        int skippedCount,
        int failedCount,
        Instant calculatedAt,
        List<StockResult> stockResults
) {

    public record StockResult(
            String stockCode,
            String status,
            String modelVersion,
            String errorMessage
    ) {
    }
}
