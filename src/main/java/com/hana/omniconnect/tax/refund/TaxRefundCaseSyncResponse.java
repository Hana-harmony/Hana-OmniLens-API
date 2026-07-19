package com.hana.omniconnect.tax.refund;

import java.time.Instant;

public record TaxRefundCaseSyncResponse(String caseId, String status, Instant syncedAt, String source) {
}
