package com.hana.omnilens.provider.ai;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HannahAiTaxDocumentVerificationRequest(
        @JsonProperty("document_type") String documentType,
        @JsonProperty("file_name") String fileName,
        @JsonProperty("extracted_text") String extractedText,
        @JsonProperty("document_content_base64") String documentContentBase64,
        @JsonProperty("content_type") String contentType,
        @JsonProperty("ocr_confidence") double ocrConfidence,
        @JsonProperty("fraud_signal_score") double fraudSignalScore,
        @JsonProperty("expected_investor_id") String expectedInvestorId,
        @JsonProperty("expected_residency_country") String expectedResidencyCountry,
        @JsonProperty("extracted_fields") Map<String, String> extractedFields
) {
}
