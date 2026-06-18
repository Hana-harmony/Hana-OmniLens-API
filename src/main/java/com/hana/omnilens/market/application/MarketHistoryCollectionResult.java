package com.hana.omnilens.market.application;

import java.time.LocalDate;

public record MarketHistoryCollectionResult(
        LocalDate baseDate,
        int requestedCount,
        int savedCount,
        String source
) {
}
