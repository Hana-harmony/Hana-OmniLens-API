package com.hana.omnilens.tax.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TaxStatusSyncRequest(
        @NotBlank @Size(max = 80) String caseId,
        @NotBlank @Size(max = 80) String accountId,
        @NotBlank @Size(max = 80) String userId,
        @Min(2020) @Max(2100) int taxYear,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String treatyCountry,
        @NotBlank @Pattern(regexp = "^-?\\d+(\\.\\d{1,2})?$") String estimatedRefundUsd,
        boolean advancePaymentRequested,
        boolean advancePaymentEligible,
        @NotNull @Size(max = 500) List<@NotBlank @Size(max = 80) String> matchedTradeIds,
        @NotNull Instant requestedAt
) {
    public BigDecimal estimatedRefundAmount() {
        return new BigDecimal(estimatedRefundUsd);
    }
}
