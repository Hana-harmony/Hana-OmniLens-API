package com.hana.omnilens.tax.domain;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TaxTreatyCaseClassificationRequest(
        @NotBlank @Size(max = 80) String caseId,
        @Min(2020) @Max(2100) int taxYear,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String treatyCountry,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String investorResidencyCountry,
        @NotNull @Size(min = 1, max = 20) List<@Pattern(regexp = "DIVIDEND|SELL") String> incomeTypes,
        boolean allListedMarketTrade,
        @DecimalMin("0.0") @DecimalMax("100.0") String maxOwnershipRatePercent,
        boolean residenceCertificateVerified,
        boolean treatyApplicationVerified,
        @NotNull Instant requestedAt
) {
}
