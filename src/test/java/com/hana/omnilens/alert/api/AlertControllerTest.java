package com.hana.omnilens.alert.api;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hana.omnilens.alert.application.AlertTitleTranslationService;
import com.hana.omnilens.provider.ai.HannahAiAnalysisClient;
import com.hana.omnilens.provider.ai.HannahAiAnalysisRequest;
import com.hana.omnilens.provider.ai.HannahAiAnalysisResponse;
import com.hana.omnilens.provider.disclosure.OpenDartDisclosure;
import com.hana.omnilens.provider.disclosure.OpenDartDisclosureClient;
import com.hana.omnilens.provider.news.NaverNewsArticle;
import com.hana.omnilens.provider.news.NaverNewsClient;

@SpringBootTest(properties = {
        "omnilens.security.api-key-enabled=true",
        "omnilens.security.api-key-sha256=4c806362b613f7496abf284146efd31da90e4b16169fe001841ca17290f427c4",
        "omnilens.alert.dedupe.mode=in-memory",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private HannahAiAnalysisClient hannahAiAnalysisClient;

    @MockitoBean
    private NaverNewsClient naverNewsClient;

    @MockitoBean
    private OpenDartDisclosureClient openDartDisclosureClient;

    @MockitoBean
    private AlertTitleTranslationService alertTitleTranslationService;

    @BeforeEach
    void deletePartnerCredentials() {
        jdbcTemplate.update("DELETE FROM partner_api_credential");
    }

    @Test
    void replaceAndGetPartnerWatchlist() throws Exception {
        mockMvc.perform(put("/api/v1/alerts/watchlists/partner-api")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "stockCodes": ["005930", "000660", "005930"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partnerId", equalTo("partner-api")))
                .andExpect(jsonPath("$.stockCodes[0]", equalTo("005930")))
                .andExpect(jsonPath("$.stockCodes[1]", equalTo("000660")));

        mockMvc.perform(get("/api/v1/alerts/watchlists/partner-api")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partnerId", equalTo("partner-api")))
                .andExpect(jsonPath("$.stockCodes[0]", equalTo("005930")))
                .andExpect(jsonPath("$.stockCodes[1]", equalTo("000660")));
    }

    @Test
    void replacePartnerWatchlistRejectsUnsupportedStockCode() throws Exception {
        mockMvc.perform(put("/api/v1/alerts/watchlists/partner-unknown-stock")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "stockCodes": ["999999"]
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type", equalTo("https://hana-omnilens-api/errors/stock-not-found")))
                .andExpect(jsonPath("$.stockCode", equalTo("999999")));
    }

    @Test
    void replacePartnerWatchlistRejectsInvalidStockCode() throws Exception {
        mockMvc.perform(put("/api/v1/alerts/watchlists/partner-invalid-stock")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "stockCodes": ["005930", "INVALID"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", equalTo("https://hana-omnilens-api/errors/validation")));
    }

    @Test
    void partnerCredentialCannotAccessDifferentPartnerWatchlist() throws Exception {
        insertPartnerCredential("partner-a", "partner-a-api-key");

        mockMvc.perform(put("/api/v1/alerts/watchlists/partner-b")
                        .header("X-HANA-OMNILENS-API-KEY", "partner-a-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "stockCodes": ["005930"]
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type", equalTo("https://hana-omnilens-api/errors/partner-access-denied")))
                .andExpect(jsonPath("$.requestedPartnerId", equalTo("partner-b")));
    }

    @Test
    void partnerCredentialCanAccessOwnWatchlist() throws Exception {
        insertPartnerCredential("partner-a", "partner-a-api-key");

        mockMvc.perform(put("/api/v1/alerts/watchlists/partner-a")
                        .header("X-HANA-OMNILENS-API-KEY", "partner-a-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "stockCodes": ["005930"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partnerId", equalTo("partner-a")))
                .andExpect(jsonPath("$.stockCodes[0]", equalTo("005930")));
    }

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
        when(alertTitleTranslationService.translateTitle("삼성전자 실적 개선"))
                .thenReturn("Samsung Electronics earnings improve");

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
                .andExpect(jsonPath("$.translatedTitle", equalTo("Samsung Electronics earnings improve")))
                .andExpect(jsonPath("$.summary", equalTo("반도체 회복으로 실적 개선 기대")))
                .andExpect(jsonPath("$.importance", equalTo("HIGH")))
                .andExpect(jsonPath("$.holderTarget", equalTo(true)))
                .andExpect(jsonPath("$.watchlistTarget", equalTo(true)))
                .andExpect(jsonPath("$.duplicateKey", equalTo("duplicate-key")))
                .andExpect(jsonPath("$.modelVersion", equalTo("financial-keyword-baseline-2026-06-04")));
    }

    @Test
    void collectAndPublishFetchesProviderItemsAndPublishesAnalyzedEvents() throws Exception {
        when(naverNewsClient.search("삼성전자", 2)).thenReturn(List.of(
                new NaverNewsArticle(
                        "삼성전자 실적 개선",
                        "반도체 회복으로 실적 개선 기대",
                        "https://news.example.com/1",
                        Instant.parse("2026-06-04T00:00:00Z")),
                new NaverNewsArticle(
                        "삼성전자 실적 개선",
                        "중복 기사",
                        "https://news.example.com/1",
                        Instant.parse("2026-06-04T00:01:00Z")),
                new NaverNewsArticle(
                        "특징주 삼성전자 실적 개선",
                        "제목 포장만 다른 중복 기사",
                        "https://news.example.com/2",
                        Instant.parse("2026-06-04T00:01:00Z"))));
        when(openDartDisclosureClient.search(
                eq("00126380"),
                any(),
                any()))
                .thenReturn(List.of(new OpenDartDisclosure(
                        "20260604000123",
                        "삼성전자",
                        "주요사항보고서",
                        java.time.LocalDate.of(2026, 6, 4),
                        "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260604000123")));
        when(hannahAiAnalysisClient.analyze(any())).thenAnswer(invocation -> {
            HannahAiAnalysisRequest request = invocation.getArgument(0);
            return new HannahAiAnalysisResponse(
                    "005930",
                    "삼성전자",
                    request.sourceType(),
                    request.title(),
                    request.snippet(),
                    List.of(request.sourceType().equals("NEWS") ? "EARNINGS" : "DISCLOSURE"),
                    "POSITIVE",
                    "HIGH",
                    List.of("005930"),
                    true,
                    true,
                    "duplicate-key",
                    "financial-ml-tfidf-logreg-test");
        });
        when(alertTitleTranslationService.translateTitle(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/v1/alerts/collect-and-publish")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "partnerId": "partner-a",
                                  "stockCodes": ["005930"],
                                  "newsDisplay": 2,
                                  "disclosureLookbackDays": 7
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partnerId", equalTo("partner-a")))
                .andExpect(jsonPath("$.collectedNewsCount", equalTo(3)))
                .andExpect(jsonPath("$.collectedDisclosureCount", equalTo(1)))
                .andExpect(jsonPath("$.publishedCount", equalTo(2)))
                .andExpect(jsonPath("$.skippedDuplicateCount", equalTo(2)))
                .andExpect(jsonPath("$.failedAnalysisCount", equalTo(0)))
                .andExpect(jsonPath("$.events[0].sourceType", equalTo("NEWS")))
                .andExpect(jsonPath("$.events[0].duplicateKey", equalTo("duplicate-key")))
                .andExpect(jsonPath("$.events[0].modelVersion", equalTo("financial-ml-tfidf-logreg-test")))
                .andExpect(jsonPath("$.events[1].sourceType", equalTo("DISCLOSURE")));
    }

    @Test
    void publishRejectsInvalidAlertPayload() throws Exception {
        mockMvc.perform(post("/api/v1/alerts/events")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "partnerId": "",
                                  "stockCode": "ABCDEF",
                                  "stockName": "삼성전자",
                                  "sourceType": "BLOG",
                                  "originalTitle": "삼성전자 실적 개선",
                                  "translatedTitle": "Samsung Electronics earnings improve",
                                  "summary": "반도체 회복으로 실적 개선 기대",
                                  "originalUrl": "https://example.com/news/1",
                                  "publishedAt": "2026-06-04T00:00:00Z",
                                  "eventTags": ["EARNINGS"],
                                  "sentiment": "BULLISH",
                                  "importance": "URGENT",
                                  "relatedStocks": ["005930"],
                                  "holderTarget": true,
                                  "watchlistTarget": true,
                                  "duplicateKey": "manual-duplicate",
                                  "modelVersion": "manual-publisher"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", equalTo("https://hana-omnilens-api/errors/validation")))
                .andExpect(jsonPath("$.title", equalTo("Invalid request")));
    }

    @Test
    void analyzeAndPublishRejectsInvalidNestedStockUniverse() throws Exception {
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
                                      "stockCode": "INVALID",
                                      "stockName": "",
                                      "stockNameEn": "Samsung Electronics",
                                      "aliases": ["Samsung Elec"]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", equalTo("https://hana-omnilens-api/errors/validation")))
                .andExpect(jsonPath("$.title", equalTo("Invalid request")));
    }

    @Test
    void collectAndPublishRejectsInvalidStockCodesAndLimits() throws Exception {
        mockMvc.perform(post("/api/v1/alerts/collect-and-publish")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "partnerId": "partner-a",
                                  "stockCodes": ["005930", "INVALID"],
                                  "newsDisplay": 0,
                                  "disclosureLookbackDays": 31
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private void insertPartnerCredential(String partnerId, String apiKey) throws Exception {
        jdbcTemplate.update(
                """
                INSERT INTO partner_api_credential (api_key_sha256, partner_id, active)
                VALUES (?, ?, TRUE)
                """,
                sha256Hex(apiKey),
                partnerId);
    }

    private String sha256Hex(String rawValue) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(rawValue.getBytes(StandardCharsets.UTF_8)));
    }
}
