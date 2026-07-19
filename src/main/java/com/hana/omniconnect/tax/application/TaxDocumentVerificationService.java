package com.hana.omniconnect.tax.application;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.hana.omniconnect.provider.ai.HannahAiTaxDocumentVerificationClient;
import com.hana.omniconnect.provider.ai.HannahAiTaxDocumentVerificationRequest;
import com.hana.omniconnect.provider.ai.HannahAiTaxDocumentVerificationResponse;
import com.hana.omniconnect.tax.domain.TaxDocumentVerificationResponse;

@Service
public class TaxDocumentVerificationService {

    private static final String SOURCE = "HANNAH_MONTANA_AI_TAX_OCR";

    private final HannahAiTaxDocumentVerificationClient hannahClient;

    public TaxDocumentVerificationService(HannahAiTaxDocumentVerificationClient hannahClient) {
        this.hannahClient = hannahClient;
    }

    public TaxDocumentVerificationResponse verify(
            com.hana.omniconnect.tax.domain.TaxDocumentVerificationRequest request) {
        HannahAiTaxDocumentVerificationResponse response = hannahClient.verify(new HannahAiTaxDocumentVerificationRequest(
                normalizeDocumentType(request.documentType()),
                request.fileName(),
                valueOrEmpty(request.extractedText()),
                valueOrEmpty(request.documentContentBase64()),
                valueOrEmpty(request.contentType()),
                request.ocrConfidence(),
                request.fraudSignalScore(),
                blankToNull(request.expectedResidencyCountry()),
                request.extractedFields() == null ? Map.of() : request.extractedFields()));
        return new TaxDocumentVerificationResponse(
                response.documentType(),
                response.fileName(),
                response.verificationStatus(),
                response.ocrConfidence(),
                response.fraudRiskScore(),
                response.riskLevel(),
                response.manualReviewRequired(),
                response.extractedFields(),
                response.missingRequiredFields(),
                response.rejectionReasons(),
                response.documentModelVersion(),
                SOURCE);
    }

    private String normalizeDocumentType(String documentType) {
        return "REDUCED_TAX_APPLICATION".equals(documentType)
                ? "REDUCED_TAX_APPLICATION"
                : documentType;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
