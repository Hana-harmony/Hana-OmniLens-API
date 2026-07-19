package com.hana.omniconnect.tax.refund;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonProperty;

public record TaxRefundDocumentSnapshot(
        @NotBlank String documentId,
        @NotBlank String documentType,
        @NotBlank String fileName,
        Map<String, String> extractedFields,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Pattern(regexp = "image/png|image/jpeg|application/pdf") String contentType,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Pattern(regexp = "[0-9a-f]{64}") String sha256,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Size(max = 14_000_000) String contentBase64
) {
    public TaxRefundDocumentSnapshot(
            String documentId,
            String documentType,
            String fileName,
            Map<String, String> extractedFields) {
        this(documentId, documentType, fileName, extractedFields, null, null, null);
    }
}
