package com.hana.omnilens.tax.refund;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record TaxRefundDocumentSnapshot(
        @NotBlank String documentId,
        @NotBlank String documentType,
        @NotBlank String fileName,
        Map<String, String> extractedFields
) {
}
