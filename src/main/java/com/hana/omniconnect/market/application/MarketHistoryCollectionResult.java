package com.hana.omniconnect.market.application;

import java.time.LocalDate;
import java.util.List;

public record MarketHistoryCollectionResult(
        LocalDate baseDate,
        int requestedCount,
        int savedCount,
        String source,
        String status,
        List<MarketResult> marketResults
) {

    public record MarketResult(
            String market,
            int requestedCount,
            int savedCount,
            String status,
            String errorMessage
    ) {
    }
}
