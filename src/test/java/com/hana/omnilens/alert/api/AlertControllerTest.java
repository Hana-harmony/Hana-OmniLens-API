package com.hana.omnilens.alert.api;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hana.omnilens.provider.ai.HannahAiAnalysisClient;
import com.hana.omnilens.provider.ai.HannahAiAnalysisResponse;

@SpringBootTest(properties = {
        "omnilens.security.api-key-enabled=true",
        "omnilens.security.api-key-sha256=4c806362b613f7496abf284146efd31da90e4b16169fe001841ca17290f427c4"
})
@AutoConfigureMockMvc
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HannahAiAnalysisClient hannahAiAnalysisClient;

    @Test
    void analyzeAndPublishReturnsAnalyzedAlertEvent() throws Exception {
        when(hannahAiAnalysisClient.analyze(any())).thenReturn(new HannahAiAnalysisResponse(
                "005930",
                "삼성전자",
                "NEWS",
                "삼성전자 실적 개선",
                "반도체 회복으로 실적 개선 기대",
                List.of("EARNINGS"),
                "POSITIVE",
                "HIGH",
                List.of("005930"),
                true,
                true,
                "duplicate-key",
                "financial-keyword-baseline-2026-06-04"));

        mockMvc.perform(post("/api/v1/alerts/analyze-and-publish")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "partnerId": "partner-a",
                                  "sourceType": "NEWS",
                                  "title": "삼성전자 실적 개선",
                                  "snippet": "반도체 회복으로 실적 개선 기대",
                                  "originalUrl": "https://example.com/news/1",
                                  "publishedAt": "2026-06-04T00:00:00Z",
                                  "stockUniverse": [
                                    {
                                      "stockCode": "005930",
                                      "stockName": "삼성전자",
                                      "stockNameEn": "Samsung Electronics",
                                      "aliases": ["Samsung Elec"]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partnerId", equalTo("partner-a")))
                .andExpect(jsonPath("$.stockCode", equalTo("005930")))
                .andExpect(jsonPath("$.summary", equalTo("반도체 회복으로 실적 개선 기대")))
                .andExpect(jsonPath("$.importance", equalTo("HIGH")))
                .andExpect(jsonPath("$.holderTarget", equalTo(true)))
                .andExpect(jsonPath("$.watchlistTarget", equalTo(true)));
    }
}
