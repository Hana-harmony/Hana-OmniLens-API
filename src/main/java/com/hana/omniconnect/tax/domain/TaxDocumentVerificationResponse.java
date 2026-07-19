package com.hana.omniconnect.tax.domain;

import java.util.List;
import java.util.Map;

public record TaxDocumentVerificationResponse(
        String documentType,
        String fileName,
        String verificationStatus,
        double ocrConfidence,
        double fraudRiskScore,
        String riskLevel,
        boolean manualReviewRequired,
        Map<String, String> extractedFields,
        List<String> missingRequiredFields,
        List<String> rejectionReasons,
        String documentModelVersion,
        String source
) {
}
