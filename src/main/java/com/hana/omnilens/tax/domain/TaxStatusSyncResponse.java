package com.hana.omnilens.tax.domain;

import java.time.Instant;

public record TaxStatusSyncResponse(
        String caseId,
        String status,
        Instant syncedAt,
        String source
) {
}
