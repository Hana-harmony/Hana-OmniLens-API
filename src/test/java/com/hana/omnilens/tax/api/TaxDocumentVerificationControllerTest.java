package com.hana.omnilens.tax.api;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hana.omnilens.provider.ai.HannahAiTaxDocumentVerificationClient;
import com.hana.omnilens.provider.ai.HannahAiTaxDocumentVerificationResponse;

@SpringBootTest(properties = {
        "omnilens.security.api-key-enabled=true",
        "omnilens.security.api-key-sha256=4c806362b613f7496abf284146efd31da90e4b16169fe001841ca17290f427c4",
        "omnilens.providers.public-data.service-key=",
        "omnilens.alert.dedupe.mode=in-memory",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
class TaxDocumentVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HannahAiTaxDocumentVerificationClient hannahClient;

    @Test
    void verifyTaxDocumentDelegatesToHannahAi() throws Exception {
        when(hannahClient.verify(any())).thenReturn(new HannahAiTaxDocumentVerificationResponse(
                "RESIDENCE_CERTIFICATE",
                "residence.txt",
                "VERIFIED",
                0.91,
                0.03,
                "LOW",
                false,
                Map.of("residency_country_code", "US"),
                List.of(),
                List.of(),
                "hanah-tax-ocr-e2e-review-v2"));

        mockMvc.perform(post("/api/v1/tax/documents/verify")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentType": "RESIDENCE_CERTIFICATE",
                                  "fileName": "residence.txt",
                                  "documentContentBase64": "VVM=",
                                  "contentType": "text/plain",
                                  "expectedResidencyCountry": "US"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.verificationStatus", equalTo("VERIFIED")))
                .andExpect(jsonPath("$.data.source", equalTo("HANNAH_MONTANA_AI_TAX_OCR")))
                .andExpect(jsonPath("$.data.extractedFields.residency_country_code", equalTo("US")));
    }
}
