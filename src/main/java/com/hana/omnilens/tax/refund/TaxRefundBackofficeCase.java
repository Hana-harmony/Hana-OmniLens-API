package com.hana.omnilens.tax.refund;

import java.time.Instant;
import java.util.List;

public record TaxRefundBackofficeCase(
        String caseId,
        String accountId,
        String userId,
        int taxYear,
        String treatyCountry,
        String estimatedRefundUsd,
        boolean advancePaymentRequested,
        boolean advancePaymentEligible,
        List<String> matchedTradeIds,
        List<TaxRefundDocumentSnapshot> verifiedDocuments,
        String status,
        Instant requestedAt,
        Instant syncedAt,
        String taxOfficeSubmissionStatus,
        Instant taxOfficeSubmittedAt,
        String correctionRequestStatus,
        String correctionPdfSha256,
        Instant correctionPreparedAt,
        Instant approvedAt
) {
}
