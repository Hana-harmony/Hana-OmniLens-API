package com.hana.omniconnect.tax.refund;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record TaxRefundBackofficeCase(
        String caseId,
        String accountId,
        String userId,
        int taxYear,
        String treatyCountry,
        @JsonIgnore String estimatedRefundUsd,
        @JsonIgnore boolean advancePaymentRequested,
        @JsonIgnore boolean advancePaymentEligible,
        @JsonIgnore List<String> matchedTradeIds,
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
