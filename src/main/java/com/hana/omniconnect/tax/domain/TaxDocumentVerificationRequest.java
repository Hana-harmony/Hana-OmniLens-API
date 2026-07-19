package com.hana.omniconnect.tax.domain;

import java.util.Map;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TaxDocumentVerificationRequest(
        @NotBlank String documentType,
        @NotBlank @Size(max = 180) String fileName,
        @Size(max = 60_000) String extractedText,
        @Size(max = 14_000_000) String documentContentBase64,
        @Size(max = 120) String contentType,
        @DecimalMin("0.0") @DecimalMax("1.0") Double ocrConfidence,
        @DecimalMin("0.0") @DecimalMax("1.0") Double fraudSignalScore,
        @Pattern(regexp = "^[A-Z]{2}$") String expectedResidencyCountry,
        Map<String, String> extractedFields
) {
}
