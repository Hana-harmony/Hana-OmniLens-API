package com.hana.omniconnect.marketnews.application;

import java.time.Instant;
import java.util.List;

import com.hana.omniconnect.marketnews.domain.MarketNewsEvent;

public record MarketNewsCollectionResult(
        List<String> queries,
        int collectedCount,
        int storedCount,
        int duplicateCount,
        List<MarketNewsEvent> events,
        Instant collectedAt
) {

    public MarketNewsCollectionResult {
        queries = queries == null ? List.of() : List.copyOf(queries);
        events = events == null ? List.of() : List.copyOf(events);
    }
}
