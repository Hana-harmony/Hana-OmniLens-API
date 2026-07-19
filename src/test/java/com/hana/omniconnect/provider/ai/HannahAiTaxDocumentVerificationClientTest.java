package com.hana.omniconnect.provider.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hana.omniconnect.provider.ProviderTestResilience;

class HannahAiTaxDocumentVerificationClientTest {

    @Test
    void verifyUsesInternalAiContractWithoutServiceToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HannahAiTaxDocumentVerificationClient client = new HannahAiTaxDocumentVerificationClient(
                builder.baseUrl("http://localhost:8000").build(),
                ProviderTestResilience.disabled());

        server.expect(requestTo("http://localhost:8000/api/v1/tax/documents/verify"))
                .andExpect(headerDoesNotExist("X-HANNAH-AI-SERVICE-TOKEN"))
                .andExpect(content().string(containsString("\"document_type\":\"RESIDENCE_CERTIFICATE\"")))
                .andExpect(content().string(containsString("\"document_content_base64\":\"VVM=\"")))
                .andExpect(content().string(not(containsString("\"ocr_confidence\""))))
                .andExpect(content().string(not(containsString("\"fraud_signal_score\""))))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "status": 200,
                          "code": "COMMON_000",
                          "message": "OK",
                          "data": {
                            "document_type": "RESIDENCE_CERTIFICATE",
                            "file_name": "residence.txt",
                            "verification_status": "VERIFIED",
                            "ocr_confidence": 0.91,
                            "fraud_risk_score": 0.03,
                            "risk_level": "LOW",
                            "manual_review_required": false,
                            "extracted_fields": {"residency_country_code": "US"},
                            "missing_required_fields": [],
                            "rejection_reasons": [],
                            "document_model_version": "hanah-tax-ocr-e2e-review-v2"
                          },
                          "timestamp": "2026-07-08T00:00:00Z"
                        }
                        """, APPLICATION_JSON));

        HannahAiTaxDocumentVerificationResponse response = client.verify(
                new HannahAiTaxDocumentVerificationRequest(
                        "RESIDENCE_CERTIFICATE",
                        "residence.txt",
                        "",
                        "VVM=",
                        "text/plain",
                        null,
                        null,
                        "US",
                        Map.of()));

        assertThat(response.verificationStatus()).isEqualTo("VERIFIED");
        assertThat(response.extractedFields()).containsEntry("residency_country_code", "US");
        server.verify();
    }
}
