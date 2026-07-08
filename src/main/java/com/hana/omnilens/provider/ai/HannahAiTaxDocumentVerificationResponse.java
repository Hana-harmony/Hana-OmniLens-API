package com.hana.omnilens.provider.ai;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HannahAiTaxDocumentVerificationResponse(
        @JsonProperty("document_type") String documentType,
        @JsonProperty("file_name") String fileName,
        @JsonProperty("verification_status") String verificationStatus,
        @JsonProperty("ocr_confidence") double ocrConfidence,
        @JsonProperty("fraud_risk_score") double fraudRiskScore,
        @JsonProperty("risk_level") String riskLevel,
        @JsonProperty("manual_review_required") boolean manualReviewRequired,
        @JsonProperty("extracted_fields") Map<String, String> extractedFields,
        @JsonProperty("missing_required_fields") List<String> missingRequiredFields,
        @JsonProperty("rejection_reasons") List<String> rejectionReasons,
        @JsonProperty("document_model_version") String documentModelVersion
) {
}
