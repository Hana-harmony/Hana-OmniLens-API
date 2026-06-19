package com.hana.omnilens.tax.api;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "omnilens.security.api-key-enabled=true",
        "omnilens.security.api-key-sha256=4c806362b613f7496abf284146efd31da90e4b16169fe001841ca17290f427c4",
        "omnilens.providers.public-data.service-key=",
        "omnilens.alert.dedupe.mode=in-memory",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
class TaxStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void syncRefundCaseReturnsAdvancePaidStatusInCommonEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/tax/refund-cases/sync")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId": "TAX-CASE-1",
                                  "accountId": "ACC-ABC123456789",
                                  "userId": "USR-ABC123456789",
                                  "taxYear": 2026,
                                  "treatyCountry": "US",
                                  "estimatedRefundUsd": "1.40",
                                  "advancePaymentRequested": true,
                                  "advancePaymentEligible": true,
                                  "matchedTradeIds": ["TRD-1"],
                                  "requestedAt": "2026-06-18T06:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.status", equalTo(200)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_000")))
                .andExpect(jsonPath("$.data.caseId", equalTo("TAX-CASE-1")))
                .andExpect(jsonPath("$.data.status", equalTo("ADVANCE_PAID")))
                .andExpect(jsonPath("$.data.source", equalTo("HANA_TAX_STATUS_RULE_ENGINE")));
    }

    @Test
    void syncRefundCaseRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/v1/tax/refund-cases/sync")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId": "",
                                  "accountId": "ACC-ABC123456789",
                                  "userId": "USR-ABC123456789",
                                  "taxYear": 2019,
                                  "treatyCountry": "usa",
                                  "estimatedRefundUsd": "1.400",
                                  "advancePaymentRequested": true,
                                  "advancePaymentEligible": true,
                                  "matchedTradeIds": [],
                                  "requestedAt": "2026-06-18T06:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_002")));
    }

    @Test
    void classifyRefundCaseReturnsCase01InCommonEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/tax/refund-cases/classify")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId": "TAX-CASE-1",
                                  "taxYear": 2026,
                                  "treatyCountry": "HK",
                                  "investorResidencyCountry": "HK",
                                  "incomeTypes": ["DIVIDEND", "SELL"],
                                  "allListedMarketTrade": true,
                                  "maxOwnershipRatePercent": "0.20",
                                  "residenceCertificateVerified": true,
                                  "treatyApplicationVerified": true,
                                  "requestedAt": "2026-06-18T06:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.status", equalTo(200)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_000")))
                .andExpect(jsonPath("$.data.caseId", equalTo("TAX-CASE-1")))
                .andExpect(jsonPath("$.data.treatyCaseType", equalTo("CASE_01")))
                .andExpect(jsonPath("$.data.eligibleForTreatyBenefit", equalTo(true)))
                .andExpect(jsonPath("$.data.classificationReasons[0]", equalTo("KR_HK_LISTED_STOCK_TREATY_CASE_01")))
                .andExpect(jsonPath("$.data.requiredNextActions[0]", equalTo("PROCEED_TAX_REFUND_STATUS_SYNC")))
                .andExpect(jsonPath("$.data.modelVersion", equalTo("kr-hk-treaty-case-classifier-v1")))
                .andExpect(jsonPath("$.data.source", equalTo("HANA_TAX_TREATY_RULE_ENGINE")));
    }

    @Test
    void classifyRefundCaseRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/v1/tax/refund-cases/classify")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId": "",
                                  "taxYear": 2019,
                                  "treatyCountry": "hkg",
                                  "investorResidencyCountry": "HK",
                                  "incomeTypes": [],
                                  "allListedMarketTrade": true,
                                  "maxOwnershipRatePercent": "101.00",
                                  "residenceCertificateVerified": true,
                                  "treatyApplicationVerified": true,
                                  "requestedAt": "2026-06-18T06:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_002")));
    }
}
