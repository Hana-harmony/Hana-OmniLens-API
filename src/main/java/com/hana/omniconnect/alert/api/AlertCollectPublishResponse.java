package com.hana.omniconnect.alert.api;

import java.util.List;

import com.hana.omniconnect.alert.domain.AlertEvent;

public record AlertCollectPublishResponse(
        String partnerId,
        List<String> requestedStockCodes,
        int collectedNewsCount,
        int collectedDisclosureCount,
        int publishedCount,
        int skippedDuplicateCount,
        int failedAnalysisCount,
        List<AlertEvent> events
) {
}
