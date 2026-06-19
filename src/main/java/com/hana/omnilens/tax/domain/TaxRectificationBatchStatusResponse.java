package com.hana.omnilens.tax.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TaxRectificationBatchStatusResponse(
        String batchId,
        int taxYear,
        int quarter,
        String status,
        LocalDate filingWindowStart,
        LocalDate filingWindowEnd,
        int totalCaseCount,
        int readyCaseCount,
        int pendingReviewCaseCount,
        List<String> requiredNextActions,
        Instant checkedAt,
        String source
) {
}
