package com.hana.omniconnect.tax.refund;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record TaxRefundCaseSyncRequest(
        @NotBlank @Pattern(regexp = TaxRefundIdentifiers.CASE_ID_PATTERN) String caseId,
        @NotBlank String accountId,
        @NotBlank String userId,
        int taxYear,
        @Pattern(regexp = "[A-Z]{2}") String treatyCountry,
        List<TaxRefundDocumentSnapshot> verifiedDocuments,
        @NotNull Instant requestedAt
) {
    public TaxRefundCaseSyncRequest(
            String caseId,
            String accountId,
            String userId,
            int taxYear,
            String treatyCountry,
            String ignoredEstimatedRefundUsd,
            boolean ignoredAdvancePaymentRequested,
            boolean ignoredAdvancePaymentEligible,
            List<String> ignoredMatchedTradeIds,
            List<TaxRefundDocumentSnapshot> verifiedDocuments,
            Instant requestedAt) {
        this(caseId, accountId, userId, taxYear, treatyCountry, verifiedDocuments, requestedAt);
    }
}
