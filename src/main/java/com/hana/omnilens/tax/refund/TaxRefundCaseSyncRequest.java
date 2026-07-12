package com.hana.omnilens.tax.refund;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record TaxRefundCaseSyncRequest(
        @NotBlank String caseId,
        @NotBlank String accountId,
        @NotBlank String userId,
        int taxYear,
        @Pattern(regexp = "[A-Z]{2}") String treatyCountry,
        @NotBlank
        @Pattern(regexp = "(?:0|[1-9][0-9]{0,15})(?:\\.[0-9]{1,2})?")
        String estimatedRefundUsd,
        boolean advancePaymentRequested,
        boolean advancePaymentEligible,
        List<String> matchedTradeIds,
        List<TaxRefundDocumentSnapshot> verifiedDocuments,
        @NotNull Instant requestedAt
) {
}
