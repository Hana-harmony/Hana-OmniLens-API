package com.hana.omniconnect.alert.api;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hana.omniconnect.alert.application.ArticleTranslationResult;
import com.hana.omniconnect.alert.domain.AlertSummaryLines;
import com.hana.omniconnect.provider.ai.HannahAiAnalysisClient;
import com.hana.omniconnect.provider.ai.HannahAiAnalysisRequest;
import com.hana.omniconnect.provider.ai.HannahAiAnalysisResponse;
import com.hana.omniconnect.provider.ai.HannahAiGlossaryTerm;
import com.hana.omniconnect.provider.disclosure.OpenDartDisclosure;
import com.hana.omniconnect.provider.disclosure.OpenDartDisclosureClient;
import com.hana.omniconnect.provider.disclosure.OpenDartDisclosureDocument;
import com.hana.omniconnect.provider.news.NaverNewsArticle;
import com.hana.omniconnect.provider.news.NaverNewsClient;
import com.hana.omniconnect.provider.news.OriginalArticleClient;
import com.hana.omniconnect.provider.news.OriginalArticleContent;

@SpringBootTest(properties = {
        "omni-connect.alert.dedupe.mode=in-memory",
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
    private OriginalArticleClient originalArticleClient;

    @MockitoBean
    private OpenDartDisclosureClient openDartDisclosureClient;

    @BeforeEach
    void deletePartnerCredentials() {
        jdbcTemplate.update("DELETE FROM partner_api_credential");
        com.hana.omniconnect.support.PartnerCredentialTestData.replace(
                jdbcTemplate, "partner-a", "test-api-key");
        com.hana.omniconnect.support.PartnerCredentialTestData.replace(
                jdbcTemplate, "partner-api", "partner-api-test-key");
        com.hana.omniconnect.support.PartnerCredentialTestData.replace(
                jdbcTemplate, "partner-unknown-stock", "partner-unknown-stock-test-key");
        com.hana.omniconnect.support.PartnerCredentialTestData.replace(
                jdbcTemplate, "partner-invalid-stock", "partner-invalid-stock-test-key");
        com.hana.omniconnect.support.PartnerCredentialTestData.replace(
                jdbcTemplate, "partner-provider-collection", "partner-provider-collection-test-key");
    }

    @Test
    void replaceAndGetPartnerWatchlist() throws Exception {
        mockMvc.perform(put("/api/v1/alerts/watchlists/partner-api")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "partner-api-test-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "stockCodes": ["005930", "000660", "005930"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.status", equalTo(200)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_000")))
                .andExpect(jsonPath("$.data.partnerId", equalTo("partner-api")))
                .andExpect(jsonPath("$.data.stockCodes[0]", equalTo("005930")))
                .andExpect(jsonPath("$.data.stockCodes[1]", equalTo("000660")));

        mockMvc.perform(get("/api/v1/alerts/watchlists/partner-api")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "partner-api-test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.partnerId", equalTo("partner-api")))
                .andExpect(jsonPath("$.data.stockCodes[0]", equalTo("005930")))
                .andExpect(jsonPath("$.data.stockCodes[1]", equalTo("000660")));
    }

    @Test
    void replacePartnerWatchlistRejectsUnsupportedStockCode() throws Exception {
        mockMvc.perform(put("/api/v1/alerts/watchlists/partner-unknown-stock")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "partner-unknown-stock-test-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "stockCodes": ["999999"]
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("MARKET_001")))
                .andExpect(jsonPath("$.message", equalTo("Stock master row not found: 999999")));
    }

    @Test
    void replacePartnerWatchlistRejectsInvalidStockCode() throws Exception {
        mockMvc.perform(put("/api/v1/alerts/watchlists/partner-invalid-stock")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "partner-invalid-stock-test-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "stockCodes": ["005930", "INVALID"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_002")));
    }

    @Test
    void partnerCredentialCannotAccessDifferentPartnerWatchlist() throws Exception {
        insertPartnerCredential("partner-a", "partner-a-api-key");

        mockMvc.perform(put("/api/v1/alerts/watchlists/partner-b")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "partner-a-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "stockCodes": ["005930"]
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("AUTH_005")));
    }

    @Test
    void partnerCredentialCanAccessOwnWatchlist() throws Exception {
        insertPartnerCredential("partner-a", "partner-a-api-key");

        mockMvc.perform(put("/api/v1/alerts/watchlists/partner-a")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "partner-a-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "stockCodes": ["005930"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.partnerId", equalTo("partner-a")))
                .andExpect(jsonPath("$.data.stockCodes[0]", equalTo("005930")));
    }

    @Test
    void listStockEventsReturnsAKeysetPage() throws Exception {
        jdbcTemplate.update("DELETE FROM alert_event");
        Instant baseTime = Instant.parse("2026-07-09T09:00:00Z");
        for (int index = 0; index < 120; index++) {
            insertAlertEvent(
                    "news-backfill-" + index,
                    "NEWS",
                    "005930",
                    baseTime.minusSeconds(index),
                    "삼성전자 뉴스 " + index);
        }
        insertAlertEvent(
                "disclosure-backfill-1",
                "DISCLOSURE",
                "005930",
                baseTime.minusSeconds(300),
                "삼성전자 주요사항보고서");

        mockMvc.perform(get("/api/v1/alerts/stocks/005930/events")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events.length()", equalTo(20)))
                .andExpect(jsonPath("$.data.events[0].alertId", equalTo("news-backfill-0")))
                .andExpect(jsonPath("$.data.nextCursor").isNotEmpty());
    }

    @Test
    void listAndDetailHideAlertUntilFullEnglishArticleIsReady() throws Exception {
        jdbcTemplate.update("DELETE FROM alert_event");
        insertBrokenAlertEvent(
                "news-pending-translation",
                "005930",
                "삼성전자",
                "https://news.example.com/pending",
                "2026-07-09T09:01:00Z");
        insertAlertEvent(
                "news-complete-translation",
                "NEWS",
                "005930",
                Instant.parse("2026-07-09T09:00:00Z"),
                "삼성전자 전문 번역 완료");

        mockMvc.perform(get("/api/v1/alerts/stocks/005930/events")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events.length()", equalTo(1)))
                .andExpect(jsonPath("$.data.events[0].alertId", equalTo("news-complete-translation")));

        mockMvc.perform(get("/api/v1/alerts/events/news-pending-translation")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key"))
                .andExpect(status().isNotFound());
    }

    @Test
    void analyzeAndPublishReturnsAnalyzedAlertEvent() throws Exception {
        when(hannahAiAnalysisClient.analyze(any())).thenAnswer(invocation ->
                fullCollectionAnalysis(invocation.getArgument(0), "duplicate-key"));
        String expectedWhat = "KOSPI-listed Samsung Electronics reported an earnings-related stock market update.";
        String expectedEnglishWhy = "The source links the event to semiconductor demand and corporate decisions.";
        String expectedEnglishImpact = "Investors should monitor earnings, liquidity, and the next official filing.";
        String expectedSummary = String.join("\n", expectedWhat, expectedEnglishWhy, expectedEnglishImpact);

        mockMvc.perform(post("/api/v1/alerts/analyze-and-publish")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "partnerId": "partner-a",
                                  "sourceType": "NEWS",
                                  "title": "삼성전자 실적 개선",
                                  "snippet": "반도체 회복으로 실적 개선 기대",
                                  "content": "삼성전자는 반도체 수요 회복으로 실적 개선을 기대한다. 데이터센터 투자가 수요를 뒷받침했다. 투자자는 다음 실적 발표와 수급을 확인해야 한다.",
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
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.status", equalTo(200)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_000")))
                .andExpect(jsonPath("$.data.partnerId", equalTo("partner-a")))
                .andExpect(jsonPath("$.data.stockCode", equalTo("005930")))
                .andExpect(jsonPath("$.data.translatedTitle", equalTo(expectedWhat)))
                .andExpect(jsonPath("$.data.summary", equalTo(expectedSummary)))
                .andExpect(jsonPath("$.data.summaryLines.what", equalTo(expectedWhat)))
                .andExpect(jsonPath("$.data.summaryLines.why", equalTo(expectedEnglishWhy)))
                .andExpect(jsonPath("$.data.summaryLines.impact", equalTo(expectedEnglishImpact)))
                .andExpect(jsonPath("$.data.importance", equalTo("HIGH")))
                .andExpect(jsonPath("$.data.holderTarget", equalTo(true)))
                .andExpect(jsonPath("$.data.watchlistTarget", equalTo(true)))
                .andExpect(jsonPath("$.data.glossaryTerms[*].sourceTerm", hasItem("KOSPI")))
                .andExpect(jsonPath("$.data.translationQualityFlags", hasItem("FINANCIAL_GLOSSARY_APPLIED")))
                .andExpect(jsonPath("$.data.duplicateKey", equalTo("duplicate-key")))
                .andExpect(jsonPath("$.data.modelVersion", equalTo("financial-ml-tfidf-logreg-test")))
                .andExpect(jsonPath("$.data.eventConfidence", equalTo(0.91)))
                .andExpect(jsonPath("$.data.stockMatchConfidence", equalTo(1.0)));

        ArgumentCaptor<HannahAiAnalysisRequest> requestCaptor =
                ArgumentCaptor.forClass(HannahAiAnalysisRequest.class);
        verify(hannahAiAnalysisClient).analyze(requestCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().canonicalUrl()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().contentHash()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().sourceLicensePolicy()).isEmpty();
    }

    @Test
    void analyzeAndPublishRejectsStaleFatalQualityFlagsWithoutLegacyRetranslation() throws Exception {
        when(hannahAiAnalysisClient.analyze(any())).thenReturn(new HannahAiAnalysisResponse(
                "005930",
                "삼성전자",
                "NEWS",
                "삼성전자 실적 개선",
                "반도체 회복으로 실적 개선 기대가 커졌습니다.",
                List.of("EARNINGS"),
                "POSITIVE",
                "HIGH",
                List.of("005930"),
                true,
                true,
                List.of(),
                List.of("HANGUL_REMAINS", "MISSING_TRANSLATED_CHUNK"),
                "stale-quality-flags",
                "financial-keyword-baseline-2026-06-04",
                0.91,
                0.89,
                0.93,
                1.0));

        mockMvc.perform(post("/api/v1/alerts/analyze-and-publish")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "partnerId": "partner-a",
                                  "sourceType": "NEWS",
                                  "title": "삼성전자 실적 개선",
                                  "snippet": "반도체 회복으로 실적 개선 기대",
                                  "originalUrl": "https://example.com/news/stale-quality-flags",
                                  "publishedAt": "2026-06-04T00:00:00Z",
                                  "stockUniverse": [
                                    {
                                      "stockCode": "005930",
                                      "stockName": "삼성전자",
                                      "stockNameEn": "Samsung Electronics"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void analyzeAndPublishRejectsFatalTranslationQualityFlags() throws Exception {
        when(hannahAiAnalysisClient.analyze(any())).thenReturn(new HannahAiAnalysisResponse(
                "005930",
                "삼성전자",
                "NEWS",
                "삼성전자 실적 개선",
                "반도체 회복으로 실적 개선 기대가 커졌습니다.",
                List.of("EARNINGS"),
                "POSITIVE",
                "HIGH",
                List.of("005930"),
                true,
                true,
                List.of(),
                List.of(),
                "fatal-translation-flags",
                "financial-keyword-baseline-2026-06-04",
                0.91,
                0.89,
                0.93,
                1.0));

        mockMvc.perform(post("/api/v1/alerts/analyze-and-publish")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "partnerId": "partner-a",
                                  "sourceType": "NEWS",
                                  "title": "삼성전자 실적 개선",
                                  "snippet": "반도체 회복으로 실적 개선 기대",
                                  "originalUrl": "https://example.com/news/fatal-translation-flags",
                                  "publishedAt": "2026-06-04T00:00:00Z",
                                  "stockUniverse": [
                                    {
                                      "stockCode": "005930",
                                      "stockName": "삼성전자",
                                      "stockNameEn": "Samsung Electronics"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void analyzeAndPublishRejectsEllipsisAndMetaSummaryFragments() throws Exception {
        when(hannahAiAnalysisClient.analyze(any())).thenReturn(new HannahAiAnalysisResponse(
                "005930",
                "삼성전자",
                "NEWS",
                "삼성전자 실적 개선...HBM 수요 확대",
                "반도체 수요 회복으로 영업이익 전망이 상향...",
                new AlertSummaryLines(
                        "반도체 수요 회복으로 영업이익 전망이 상향...",
                        "HBM 수요 확대 배경은",
                        "The impact is classified as high importance and positive sentiment."),
                "SUMMARY_ONLY",
                "",
                List.of(),
                List.of("EARNINGS"),
                "POSITIVE",
                "HIGH",
                List.of("005930"),
                true,
                true,
                List.of(),
                List.of(),
                "fragment-alert",
                "fragment-alert",
                "financial-keyword-baseline-2026-06-04",
                0.55,
                0.55,
                0.55,
                1.0));

        mockMvc.perform(post("/api/v1/alerts/analyze-and-publish")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "partnerId": "partner-a",
                                  "sourceType": "NEWS",
                                  "title": "삼성전자 실적 개선...HBM 수요 확대",
                                  "snippet": "반도체 수요 회복으로 영업이익 전망이 상향...",
                                  "originalUrl": "https://example.com/news/fragment-alert",
                                  "publishedAt": "2026-06-04T00:00:00Z",
                                  "stockUniverse": [
                                    {
                                      "stockCode": "005930",
                                      "stockName": "삼성전자",
                                      "stockNameEn": "Samsung Electronics"
                                    }
                                  ]
                                }
		                                """))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void reprocessQualityIssuesFixesStoredBrokenAlertSummary() throws Exception {
        jdbcTemplate.update("DELETE FROM alert_event");
        jdbcTemplate.update(
                """
                INSERT INTO alert_event (
                    alert_id, partner_id, stock_code, source_type, original_url,
                    duplicate_key, cluster_key, content_availability, published_at, created_at, event_json
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "alert-quality-issue",
                "partner-a",
                "005930",
                "NEWS",
                "https://news.example.com/alert/quality",
                "alert-quality-duplicate",
                "alert-quality-cluster",
                "FULL_TEXT",
                java.sql.Timestamp.from(Instant.parse("2026-06-18T07:00:00Z")),
                java.sql.Timestamp.from(Instant.parse("2026-06-18T07:01:00Z")),
                """
                {
                  "alertId": "alert-quality-issue",
                  "partnerId": "partner-a",
                  "stockCode": "005930",
                  "stockName": "삼성전자",
                  "sourceType": "NEWS",
                  "originalTitle": "삼성전자 실적 개선...HBM 수요 확대",
                  "translatedTitle": "Samsung earnings...",
                  "summary": "반도체 수요 회복으로 영업이익 전망이 상향...",
                  "summaryLines": {
                    "what": "반도체 수요 회복으로 영업이익 전망이 상향...",
                    "why": "HBM 수요 확대 배경은",
                    "impact": "영향은 high 중요도와 positive 감성으로 분류되어 보유·관심 종목 사용자 확인이 필요합니다."
                  },
                  "translatedSummary": "The impact is classified as high importance and positive sentiment.",
                  "originalContent": "삼성전자는 HBM 수요 확대로 실적 개선 기대가 커졌다. 데이터센터 투자가 주요 배경이다. 투자자는 영업이익 회복 속도를 확인해야 한다.",
                  "translatedContent": "",
                  "imageUrls": [],
                  "contentAvailability": "FULL_TEXT",
                  "originalUrl": "https://news.example.com/alert/quality",
                  "publishedAt": "2026-06-18T07:00:00Z",
                  "eventTags": ["EARNINGS"],
                  "sentiment": "POSITIVE",
                  "importance": "HIGH",
                  "relatedStocks": ["005930"],
                  "holderTarget": true,
                  "watchlistTarget": true,
                  "glossaryTerms": [],
                  "translationQualityFlags": [],
                  "translationProvider": "old-provider",
                  "translationModelVersion": "old-model",
                  "translationStatus": "TRANSLATED",
                  "duplicateKey": "alert-quality-duplicate",
                  "clusterKey": "alert-quality-cluster",
                  "modelVersion": "old-model",
                  "eventConfidence": 0.55,
                  "sentimentConfidence": 0.55,
                  "importanceConfidence": 0.55,
                  "stockMatchConfidence": 1.0,
                  "createdAt": "2026-06-18T07:01:00Z"
                }
                """);
        when(hannahAiAnalysisClient.analyze(any())).thenAnswer(invocation ->
                fullCollectionAnalysis(invocation.getArgument(0), "quality-alert-duplicate"));
        String repairedTranslatedContent = "Samsung Electronics expects earnings to improve as HBM demand expands. "
                + "Data center investment is the main driver. "
                + "Investors should monitor the pace of operating-profit recovery.";
        String repairedWhat = "KOSPI-listed Samsung Electronics reported an earnings-related stock market update.";
        String repairedWhy = "The source links the event to semiconductor demand and corporate decisions.";
        String repairedImpact = "Investors should monitor earnings, liquidity, and the next official filing.";

        mockMvc.perform(post("/api/v1/alerts/events/reprocess/quality-issues")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventCount", equalTo(1)))
                .andExpect(jsonPath("$.data.events[0].summaryLines.what",
                        equalTo(repairedWhat)))
                .andExpect(jsonPath("$.data.events[0].summaryLines.why",
                        equalTo(repairedWhy)))
                .andExpect(jsonPath("$.data.events[0].summaryLines.impact",
                        equalTo(repairedImpact)));

        String storedPayload = jdbcTemplate.queryForObject(
                "SELECT event_json FROM alert_event WHERE alert_id = 'alert-quality-issue'",
                String.class);
        org.assertj.core.api.Assertions.assertThat(storedPayload)
                .contains(repairedWhat)
                .contains(repairedWhy)
                .contains(repairedImpact)
                .contains("Samsung Electronics reported a market-moving update")
                .doesNotContain("The impact is classified")
                .doesNotContain("중요도")
                .doesNotContain("감성");
    }

    @Test
    void reprocessQualityIssuesFixesBlankAlertSummaryLines() throws Exception {
        jdbcTemplate.update("DELETE FROM alert_event");
        jdbcTemplate.update(
                """
                INSERT INTO alert_event (
                    alert_id, partner_id, stock_code, source_type, original_url,
                    duplicate_key, cluster_key, content_availability, published_at, created_at, event_json
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "alert-blank-lines",
                "partner-a",
                "005930",
                "NEWS",
                "https://news.example.com/alert/blank-lines",
                "alert-blank-lines-duplicate",
                "alert-blank-lines-cluster",
                "FULL_TEXT",
                java.sql.Timestamp.from(Instant.parse("2026-06-18T07:00:00Z")),
                java.sql.Timestamp.from(Instant.parse("2026-06-18T07:01:00Z")),
                """
                {
                  "alertId": "alert-blank-lines",
                  "partnerId": "partner-a",
                  "stockCode": "005930",
                  "stockName": "삼성전자",
                  "sourceType": "NEWS",
                  "originalTitle": "삼성전자 실적 개선 HBM 수요 확대",
                  "translatedTitle": "Samsung earnings improve",
                  "summary": "삼성전자는 HBM 수요 확대로 실적 개선 기대가 커졌습니다.",
                  "summaryLines": {
                    "what": "삼성전자는 HBM 수요 확대로 실적 개선 기대가 커졌습니다.",
                    "why": "",
                    "impact": ""
                  },
                  "translatedSummary": "삼성전자는 HBM 수요 확대로 실적 개선 기대가 커졌습니다.",
                  "originalContent": "삼성전자는 HBM 수요 확대로 실적 개선 기대가 커졌다. 데이터센터 투자가 주요 배경이다. 투자자는 영업이익 회복 속도를 확인해야 한다.",
                  "translatedContent": "",
                  "imageUrls": [],
                  "contentAvailability": "FULL_TEXT",
                  "originalUrl": "https://news.example.com/alert/blank-lines",
                  "publishedAt": "2026-06-18T07:00:00Z",
                  "eventTags": ["EARNINGS"],
                  "sentiment": "POSITIVE",
                  "importance": "HIGH",
                  "relatedStocks": ["005930"],
                  "holderTarget": true,
                  "watchlistTarget": true,
                  "glossaryTerms": [],
                  "translationQualityFlags": [],
                  "translationProvider": "old-provider",
                  "translationModelVersion": "old-model",
                  "translationStatus": "TRANSLATED",
                  "duplicateKey": "alert-blank-lines-duplicate",
                  "clusterKey": "alert-blank-lines-cluster",
                  "modelVersion": "old-model",
                  "eventConfidence": 0.55,
                  "sentimentConfidence": 0.55,
                  "importanceConfidence": 0.55,
                  "stockMatchConfidence": 1.0,
                  "createdAt": "2026-06-18T07:01:00Z"
                }
                """);
        when(hannahAiAnalysisClient.analyze(any())).thenAnswer(invocation ->
                fullCollectionAnalysis(invocation.getArgument(0), "blank-alert-duplicate"));
        String repairedWhy = "The source links the event to semiconductor demand and corporate decisions.";
        String repairedImpact = "Investors should monitor earnings, liquidity, and the next official filing.";

        mockMvc.perform(post("/api/v1/alerts/events/reprocess/quality-issues")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventCount", equalTo(1)))
                .andExpect(jsonPath("$.data.events[0].summaryLines.why",
                        equalTo(repairedWhy)))
                .andExpect(jsonPath("$.data.events[0].summaryLines.impact",
                        equalTo(repairedImpact)));
    }

    @Test
    void reprocessQualityIssuesKeepsStoredAlertsPendingWhenQwenFails() throws Exception {
        jdbcTemplate.update("DELETE FROM alert_event");
        insertBrokenAlertEvent(
                "alert-quality-failed",
                "999999",
                "미매칭종목",
                "https://news.example.com/alert/failed",
                "2026-06-18T08:00:00Z");
        insertBrokenAlertEvent(
                "alert-quality-match",
                "005930",
                "삼성전자",
                "https://news.example.com/alert/match",
                "2026-06-18T07:00:00Z");
        mockMvc.perform(post("/api/v1/alerts/events/reprocess/quality-issues")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventCount", equalTo(0)))
                .andExpect(jsonPath("$.data.events").isEmpty());

        String matchedPayload = jdbcTemplate.queryForObject(
                "SELECT event_json FROM alert_event WHERE alert_id = 'alert-quality-match'",
                String.class);
        String failedPayload = jdbcTemplate.queryForObject(
                "SELECT event_json FROM alert_event WHERE alert_id = 'alert-quality-failed'",
                String.class);
        org.assertj.core.api.Assertions.assertThat(matchedPayload).contains("The impact is classified");
        org.assertj.core.api.Assertions.assertThat(failedPayload).contains("The impact is classified");
    }

    @Test
    void reprocessEventUsesStoredSingleStockWhenTitleOnlyAnalysisDoesNotMatch() throws Exception {
        jdbcTemplate.update("DELETE FROM alert_event");
        insertTitleOnlyAlertEvent(
                "alert-title-only",
                "005930",
                "삼성전자",
                "북미 최대 교육 기술 전시회서 삼성 교육용 전자칠판 공개");
        when(hannahAiAnalysisClient.analyze(any())).thenReturn(new HannahAiAnalysisResponse(
                "",
                "",
                "NEWS",
                "북미 최대 교육 기술 전시회서 삼성 교육용 전자칠판 공개",
                "북미 최대 교육 기술 전시회서 삼성 교육용 전자칠판 공개.",
                new AlertSummaryLines(
                        "북미 최대 교육 기술 전시회서 삼성 교육용 전자칠판 공개.",
                        "북미 교육 시장 공략이 주요 배경입니다.",
                        "투자자는 B2B 디스플레이 매출 기여도를 확인해야 합니다."),
                "SUMMARY_ONLY",
                "",
                List.of(),
                List.of("GENERAL_MARKET"),
                "NEUTRAL",
                "MEDIUM",
                List.of(),
                false,
                true,
                List.of(),
                List.of(),
                "title-only-duplicate",
                "title-only-cluster",
                "financial-keyword-baseline-2026-06-04",
                0.55,
                0.55,
                0.55,
                0.0));
        String englishFallbackWhat = englishTextFor("북미 최대 교육 기술 전시회서 삼성 교육용 전자칠판 공개.");
        String englishFallbackImpact = englishTextFor("투자자는 B2B 디스플레이 매출 기여도를 확인해야 합니다.");

        mockMvc.perform(post("/api/v1/alerts/events/alert-title-only/reprocess")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key"))
                .andExpect(status().is5xxServerError());

        ArgumentCaptor<HannahAiAnalysisRequest> requestCaptor =
                ArgumentCaptor.forClass(HannahAiAnalysisRequest.class);
        verify(hannahAiAnalysisClient).analyze(requestCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().snippet())
                .isEqualTo("북미 최대 교육 기술 전시회서 삼성 교육용 전자칠판 공개");
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().content()).isEmpty();
    }

    @Test
    void analyzeAndPublishAcceptsLongDisclosureAndKeepsCollectedOriginalContent() throws Exception {
        String longDisclosureContent = (
                "삼성전자는 자기주식 처분과 실적 전망을 공시했다. "
                        + "투자자는 공시 원문과 수급 영향을 확인해야 한다. ")
                .repeat(420)
                + "장문 공시의 마지막 원문 문장이다.";
        String longTranslatedContent = (
                "Samsung Electronics disclosed a treasury-share disposal and an earnings outlook. "
                        + "The filing explains the purpose, schedule, and expected market impact. ")
                .repeat(180);
        when(hannahAiAnalysisClient.analyze(any())).thenAnswer(invocation -> {
            HannahAiAnalysisRequest request = invocation.getArgument(0);
            return fullQwenAnalysis(
                    request,
                    "Samsung Electronics disclosed a treasury-share disposal plan.",
                    new AlertSummaryLines(
                            "Samsung Electronics decided to dispose of treasury shares.",
                            "The filing specifies the disposal purpose and schedule.",
                            "Investors should monitor supply-demand and the share-price reaction."),
                    longTranslatedContent,
                    List.of(),
                    List.of(),
                    "long-disclosure");
        });

        mockMvc.perform(post("/api/v1/alerts/analyze-and-publish")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "partnerId": "partner-a",
                                  "sourceType": "DISCLOSURE",
                                  "title": "삼성전자 주요사항보고서",
                                  "snippet": "자기주식 처분 결정",
                                  "content": %s,
                                  "originalUrl": "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260706000444",
                                  "publishedAt": "2026-07-09T00:00:00Z",
                                  "stockUniverse": [
                                    {
                                      "stockCode": "005930",
                                      "stockName": "삼성전자",
                                      "stockNameEn": "Samsung Electronics",
                                      "aliases": ["Samsung Electronics"]
                                    }
                                  ]
                                }
                                """.formatted(jsonString(longDisclosureContent))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentAvailability", equalTo("FULL_TEXT")))
                .andExpect(jsonPath("$.data.originalContent", containsString("장문 공시의 마지막 원문 문장이다")))
                .andExpect(jsonPath("$.data.originalContent", containsString("삼성전자는 자기주식 처분과 실적 전망을 공시했다")))
                .andExpect(jsonPath("$.data.translatedContent", containsString("treasury-share disposal")));

        ArgumentCaptor<HannahAiAnalysisRequest> requestCaptor =
                ArgumentCaptor.forClass(HannahAiAnalysisRequest.class);
        verify(hannahAiAnalysisClient).analyze(requestCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().content()).hasSizeGreaterThan(20_000);
    }

    @Test
    void analyzeAndPublishUsesTranslatedSurfaceTermForGlossaryClickTarget() throws Exception {
        when(hannahAiAnalysisClient.analyze(any())).thenAnswer(invocation -> fullQwenAnalysis(
                invocation.getArgument(0),
                "Samsung Electronics Ants net bought while Daejangju rallied.",
                new AlertSummaryLines(
                        "Ants net bought Samsung Electronics while Daejangju momentum continued.",
                        "Retail demand and semiconductor expectations supported the move.",
                        "Investors should monitor flows and earnings expectations."),
                "Ants net bought Samsung Electronics while Daejangju momentum continued. "
                        + "Retail demand supported the market leader during the session. "
                        + "Investors should monitor flows and earnings expectations.",
                List.of(
                        new HannahAiGlossaryTerm("개미", "개미", "Ant", "market_slang"),
                        new HannahAiGlossaryTerm("대장주", "대장주", "Market Leader", "market_slang")),
                List.of("FINANCIAL_GLOSSARY_APPLIED"),
                "duplicate-key-ant-surface"));

        mockMvc.perform(post("/api/v1/alerts/analyze-and-publish")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "partnerId": "partner-a",
                                  "sourceType": "NEWS",
                                  "title": "삼성전자 개미 순매수와 대장주 강세",
                                  "snippet": "개미가 삼성전자를 순매수했고 대장주 흐름이 이어졌다",
                                  "content": "개미가 삼성전자를 순매수했고 대장주 흐름이 이어졌다. 반도체 기대가 수급을 뒷받침했다. 투자자는 실적과 수급을 확인해야 한다.",
                                  "originalUrl": "https://example.com/news/ant-surface",
                                  "publishedAt": "2026-06-04T00:00:00Z",
                                  "stockUniverse": [
                                    {
                                      "stockCode": "005930",
                                      "stockName": "삼성전자",
                                      "stockNameEn": "Samsung Electronics"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.glossaryTerms[0].sourceTerm", equalTo("Ant")))
                .andExpect(jsonPath("$.data.glossaryTerms[0].normalizedTerm", equalTo("개미")))
                .andExpect(jsonPath("$.data.glossaryTerms[0].englishTerm", equalTo("Ant")))
                .andExpect(jsonPath("$.data.glossaryTerms[*].sourceTerm", hasItem("Daejangju")))
                .andExpect(jsonPath("$.data.glossaryTerms[*].englishTerm", hasItem("Market Leader")))
                .andExpect(jsonPath("$.data.glossaryTerms[*].description", hasItem(
                        "Refers to the leading stock in a particular sector or the entire market that dictates the overall trend.")));
    }

    @Test
    void collectAndPublishFetchesProviderItemsAndPublishesAnalyzedEvents() throws Exception {
        when(naverNewsClient.search(eq("삼성전자 주가"), anyInt())).thenReturn(List.of(
                new NaverNewsArticle(
                        "삼성전자 주가, 반도체 실적 개선 기대에 강세",
                        "반도체 회복으로 주가와 실적 개선 기대",
                        "https://news.example.com/1",
                        Instant.parse("2026-06-04T00:00:00Z")),
                new NaverNewsArticle(
                        "삼성전자 주가, 반도체 실적 개선 기대에 강세",
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
        when(originalArticleClient.fetch("https://news.example.com/1"))
                .thenReturn(Optional.of(new OriginalArticleContent(
                        """
                                삼성전자는 AI 서버 투자 확대로 반도체 실적 개선 기대가 커지며 주가가 강세를 보였다.
                                증권가는 메모리 가격 반등과 HBM 공급 확대가 영업이익 회복을 이끌 수 있다고 분석했다.
                                외국인 순매수와 기관 매수세도 이어지면서 코스피 대장주 수급이 개선됐다는 평가가 나왔다.
                                투자자는 다음 분기 실적 발표와 반도체 업황 회복 속도를 함께 확인해야 한다.
                                """,
                        List.of("https://news.example.com/images/1.jpg"),
                        "https://news.example.com/1",
                        "news-content-hash",
                        "licensed_naver_original_full_text_v1")));
        when(originalArticleClient.fetch("https://news.example.com/2"))
                .thenReturn(Optional.empty());
        when(openDartDisclosureClient.fetchDocumentContent("20260604000123"))
                .thenReturn(Optional.of(new OpenDartDisclosureDocument(
                        "삼성전자 주요사항보고서 전문이다. 자기주식 취득과 소각 결정으로 주주환원 영향이 있다.",
                        "disclosure-content-hash",
                        "opendart_public_disclosure_text_v1")));
        when(hannahAiAnalysisClient.analyze(any())).thenAnswer(invocation -> {
            HannahAiAnalysisRequest request = invocation.getArgument(0);
            return fullCollectionAnalysis(request, "provider-collection-duplicate-key");
        });
        mockMvc.perform(post("/api/v1/alerts/collect-and-publish")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "partner-provider-collection-test-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "partnerId": "partner-provider-collection",
                                  "stockCodes": ["005930"],
                                  "newsDisplay": 2,
                                  "disclosureLookbackDays": 7
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.status", equalTo(200)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_000")))
                .andExpect(jsonPath("$.data.partnerId", equalTo("partner-provider-collection")))
                .andExpect(jsonPath("$.data.collectedNewsCount", equalTo(3)))
                .andExpect(jsonPath("$.data.collectedDisclosureCount", equalTo(1)))
                .andExpect(jsonPath("$.data.publishedCount", equalTo(2)))
                .andExpect(jsonPath("$.data.skippedDuplicateCount", equalTo(1)))
                .andExpect(jsonPath("$.data.failedAnalysisCount", equalTo(0)))
                .andExpect(jsonPath("$.data.events[0].sourceType", equalTo("DISCLOSURE")))
                .andExpect(jsonPath("$.data.events[0].contentAvailability", equalTo("FULL_TEXT")))
                .andExpect(jsonPath("$.data.events[0].originalContent",
                        equalTo("삼성전자 주요사항보고서 전문이다. 자기주식 취득과 소각 결정으로 주주환원 영향이 있다.")))
                .andExpect(jsonPath("$.data.events[1].sourceType", equalTo("NEWS")))
                .andExpect(jsonPath("$.data.events[1].duplicateKey", equalTo("provider-collection-duplicate-key")))
                .andExpect(jsonPath("$.data.events[1].glossaryTerms[*].sourceTerm", hasItem("KOSPI")))
                .andExpect(jsonPath("$.data.events[1].translationQualityFlags", hasItem("FINANCIAL_GLOSSARY_APPLIED")))
                .andExpect(jsonPath("$.data.events[1].modelVersion", equalTo("financial-ml-tfidf-logreg-test")))
                .andExpect(jsonPath("$.data.events[1].contentAvailability", equalTo("FULL_TEXT")))
                .andExpect(jsonPath("$.data.events[1].originalContent",
                        containsString("주가가 강세를 보였다")))
                .andExpect(jsonPath("$.data.events[1].translatedContent", containsString("Samsung Electronics")))
                .andExpect(jsonPath("$.data.events[1].imageUrls[0]",
                        equalTo("https://news.example.com/images/1.jpg")));
    }

    @Test
    void publishRejectsInvalidAlertPayload() throws Exception {
        mockMvc.perform(post("/api/v1/alerts/events")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
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
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_002")));
    }

    @Test
    void publishRejectsPartialMarketImpactPayload() throws Exception {
        mockMvc.perform(post("/api/v1/alerts/events")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "partnerId": "partner-a",
                                  "stockCode": "005930",
                                  "stockName": "삼성전자",
                                  "sourceType": "NEWS",
                                  "originalTitle": "삼성전자 실적 개선",
                                  "translatedTitle": "Samsung Electronics earnings improve",
                                  "summary": "반도체 회복으로 실적 개선 기대",
                                  "originalUrl": "https://example.com/news/partial-market-impact",
                                  "publishedAt": "2026-06-04T00:00:00Z",
                                  "eventTags": ["EARNINGS"],
                                  "sentiment": "POSITIVE",
                                  "importance": "HIGH",
                                  "marketImpactScore": 0.42,
                                  "relatedStocks": ["005930"],
                                  "holderTarget": true,
                                  "watchlistTarget": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_002")));
    }

    @Test
    void analyzeAndPublishRejectsInvalidNestedStockUniverse() throws Exception {
        mockMvc.perform(post("/api/v1/alerts/analyze-and-publish")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
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
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_002")));
    }

    @Test
    void collectAndPublishRejectsInvalidStockCodesAndLimits() throws Exception {
        mockMvc.perform(post("/api/v1/alerts/collect-and-publish")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
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

    @Test
    void collectAndPublishRequiresCompleteFullTextTranslation() throws Exception {
        when(naverNewsClient.search(eq("삼성전자 주가"), anyInt())).thenReturn(List.of(
                new NaverNewsArticle(
                        "삼성전자 주가 번역 실패 기사",
                        "삼성전자 코스피 VI 관련 오역 가능 기사",
                        "https://news.example.com/translation-fail",
                        Instant.parse("2026-06-04T00:00:00Z")),
                new NaverNewsArticle(
                        "삼성전자 주가 실적 개선",
                        "반도체 회복으로 주가와 실적 개선 기대",
                        "https://news.example.com/translation-ok",
                        Instant.parse("2026-06-04T00:01:00Z"))));
        when(openDartDisclosureClient.search(eq("00126380"), any(), any()))
                .thenReturn(List.of());
        when(originalArticleClient.fetch(any())).thenReturn(Optional.of(new OriginalArticleContent(
                """
                        삼성전자는 AI 서버 투자 확대로 반도체 실적 개선 기대가 커지며 주가가 강세를 보였다.
                        증권가는 메모리 가격 반등과 HBM 공급 확대가 영업이익 회복을 이끌 수 있다고 분석했다.
                        외국인 순매수와 기관 매수세도 이어지면서 코스피 대장주 수급이 개선됐다는 평가가 나왔다.
                        투자자는 다음 분기 실적 발표와 반도체 업황 회복 속도를 함께 확인해야 한다.
                        """,
                List.of(),
                "https://news.example.com/article",
                "content-hash",
                "licensed_naver_original_full_text_v1")));
        when(hannahAiAnalysisClient.analyze(any())).thenAnswer(invocation -> {
            HannahAiAnalysisRequest request = invocation.getArgument(0);
            if (request.title().contains("번역 실패")) {
                return incompleteCollectionAnalysis(
                        request,
                        "duplicate-" + Math.abs(request.title().hashCode()));
            }
            return fullCollectionAnalysis(
                    request,
                    "duplicate-" + Math.abs(request.title().hashCode()));
        });

        mockMvc.perform(post("/api/v1/alerts/collect-and-publish")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "partnerId": "partner-a",
                                  "stockCodes": ["005930"],
                                  "newsDisplay": 2,
                                  "disclosureLookbackDays": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.collectedNewsCount", equalTo(2)))
                .andExpect(jsonPath("$.data.collectedDisclosureCount", equalTo(0)))
                .andExpect(jsonPath("$.data.publishedCount", equalTo(1)))
                .andExpect(jsonPath("$.data.skippedDuplicateCount", equalTo(0)))
                .andExpect(jsonPath("$.data.failedAnalysisCount", equalTo(1)))
                .andExpect(jsonPath("$.data.events[0].contentAvailability", equalTo("FULL_TEXT")))
                .andExpect(jsonPath("$.data.events[0].originalTitle", equalTo("삼성전자 주가 실적 개선")));
    }

    private void insertBrokenAlertEvent(
            String alertId,
            String stockCode,
            String stockName,
            String originalUrl,
            String publishedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO alert_event (
                    alert_id, partner_id, stock_code, source_type, original_url,
                    duplicate_key, cluster_key, content_availability, published_at, created_at, event_json
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                alertId,
                "partner-a",
                stockCode,
                "NEWS",
                originalUrl,
                alertId + "-duplicate",
                alertId + "-cluster",
                "FULL_TEXT",
                java.sql.Timestamp.from(Instant.parse(publishedAt)),
                java.sql.Timestamp.from(Instant.parse(publishedAt).plusSeconds(60)),
                """
                {
                  "alertId": "%s",
                  "partnerId": "partner-a",
                  "stockCode": "%s",
                  "stockName": "%s",
                  "sourceType": "NEWS",
                  "originalTitle": "%s 실적 개선...HBM 수요 확대",
                  "translatedTitle": "Samsung earnings...",
                  "summary": "반도체 수요 회복으로 영업이익 전망이 상향...",
                  "summaryLines": {
                    "what": "반도체 수요 회복으로 영업이익 전망이 상향...",
                    "why": "HBM 수요 확대 배경은",
                    "impact": "The impact is classified as high importance and positive sentiment."
                  },
                  "translatedSummary": "The impact is classified as high importance and positive sentiment.",
                  "originalContent": "%s는 HBM 수요 확대로 실적 개선 기대가 커졌다. 데이터센터 투자가 주요 배경이다. 투자자는 영업이익 회복 속도를 확인해야 한다.",
                  "translatedContent": "",
                  "imageUrls": [],
                  "contentAvailability": "FULL_TEXT",
                  "originalUrl": "%s",
                  "publishedAt": "%s",
                  "eventTags": ["EARNINGS"],
                  "sentiment": "POSITIVE",
                  "importance": "HIGH",
                  "relatedStocks": ["%s"],
                  "holderTarget": true,
                  "watchlistTarget": true,
                  "glossaryTerms": [],
                  "translationQualityFlags": [],
                  "translationProvider": "old-provider",
                  "translationModelVersion": "old-model",
                  "translationStatus": "TRANSLATED",
                  "duplicateKey": "%s-duplicate",
                  "clusterKey": "%s-cluster",
                  "modelVersion": "old-model",
                  "eventConfidence": 0.55,
                  "sentimentConfidence": 0.55,
                  "importanceConfidence": 0.55,
                  "stockMatchConfidence": 1.0,
                  "createdAt": "%s"
                }
                """.formatted(
                        alertId,
                        stockCode,
                        stockName,
                        stockName,
                        stockName,
                        originalUrl,
                        publishedAt,
                        stockCode,
                        alertId,
                        alertId,
                        Instant.parse(publishedAt).plusSeconds(60)));
    }

    private void insertAlertEvent(
            String alertId,
            String sourceType,
            String stockCode,
            Instant publishedAt,
            String title) {
        jdbcTemplate.update(
                """
                INSERT INTO alert_event (
                    alert_id, partner_id, stock_code, source_type, original_url,
                    duplicate_key, cluster_key, content_availability, published_at, created_at, event_json
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                alertId,
                "partner-a",
                stockCode,
                sourceType,
                "https://example.com/alerts/" + alertId,
                alertId + "-duplicate",
                alertId + "-cluster",
                sourceType.equals("DISCLOSURE") ? "FULL_TEXT" : "SUMMARY_ONLY",
                java.sql.Timestamp.from(publishedAt),
                java.sql.Timestamp.from(publishedAt.plusSeconds(1)),
                """
                {
                  "alertId": "%s",
                  "partnerId": "partner-a",
                  "stockCode": "%s",
                  "stockName": "삼성전자",
                  "sourceType": "%s",
                  "originalTitle": "%s",
                  "translatedTitle": "%s",
                  "summary": "원문은 삼성전자 이벤트를 보고했다.",
                  "summaryLines": {
                    "what": "Samsung Electronics reported a verified event.",
                    "why": "The source cited market and disclosure context.",
                    "impact": "Investors should monitor follow-up filings and price reaction."
                  },
                  "translatedSummary": "Samsung Electronics reported a verified event.",
                  "originalContent": "원문은 삼성전자 이벤트를 보고했다. 투자자는 후속 공시와 주가 반응을 확인해야 한다.",
                  "translatedContent": "The source reported a Samsung Electronics event. Investors should monitor follow-up filings and price reaction.",
                  "imageUrls": [],
                  "contentAvailability": "%s",
                  "originalUrl": "https://example.com/alerts/%s",
                  "publishedAt": "%s",
                  "eventTags": ["EARNINGS"],
                  "sentiment": "NEUTRAL",
                  "importance": "MEDIUM",
                  "relatedStocks": ["%s"],
                  "holderTarget": false,
                  "watchlistTarget": true,
                  "glossaryTerms": [],
                  "translationQualityFlags": [],
                  "translationProvider": "test-provider",
                  "translationModelVersion": "test-model",
                  "translationStatus": "TRANSLATED",
                  "duplicateKey": "%s-duplicate",
                  "clusterKey": "%s-cluster",
                  "modelVersion": "test-model",
                  "eventConfidence": 0.7,
                  "sentimentConfidence": 0.7,
                  "importanceConfidence": 0.7,
                  "stockMatchConfidence": 1.0,
                  "createdAt": "%s"
                }
                """.formatted(
                        alertId,
                        stockCode,
                        sourceType,
                        title,
                        englishTextFor(title),
                        sourceType.equals("DISCLOSURE") ? "FULL_TEXT" : "SUMMARY_ONLY",
                        alertId,
                        publishedAt,
                        stockCode,
                        alertId,
                        alertId,
                        publishedAt.plusSeconds(1)));
    }

    private void insertTitleOnlyAlertEvent(
            String alertId,
            String stockCode,
            String stockName,
            String title) {
        Instant publishedAt = Instant.parse("2026-06-18T09:00:00Z");
        jdbcTemplate.update(
                """
                INSERT INTO alert_event (
                    alert_id, partner_id, stock_code, source_type, original_url,
                    duplicate_key, cluster_key, content_availability, published_at, created_at, event_json
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                alertId,
                "partner-a",
                stockCode,
                "NEWS",
                "https://news.example.com/alert/title-only",
                alertId + "-duplicate",
                alertId + "-cluster",
                "SUMMARY_ONLY",
                java.sql.Timestamp.from(publishedAt),
                java.sql.Timestamp.from(publishedAt.plusSeconds(60)),
                """
                {
                  "alertId": "%s",
                  "partnerId": "partner-a",
                  "stockCode": "%s",
                  "stockName": "%s",
                  "sourceType": "NEWS",
                  "originalTitle": "%s",
                  "translatedTitle": "%s",
                  "summary": "과거 요약이 중간에서 잘린",
                  "summaryLines": {
                    "what": "과거 요약이 중간에서 잘린",
                    "why": "",
                    "impact": "The impact is classified as medium importance and neutral sentiment."
                  },
                  "translatedSummary": "The impact is classified as medium importance and neutral sentiment.",
                  "originalContent": "",
                  "translatedContent": "",
                  "imageUrls": [],
                  "contentAvailability": "SUMMARY_ONLY",
                  "originalUrl": "https://news.example.com/alert/title-only",
                  "publishedAt": "2026-06-18T09:00:00Z",
                  "eventTags": ["GENERAL_MARKET"],
                  "sentiment": "NEUTRAL",
                  "importance": "MEDIUM",
                  "relatedStocks": ["%s"],
                  "holderTarget": false,
                  "watchlistTarget": true,
                  "glossaryTerms": [],
                  "translationQualityFlags": [],
                  "translationProvider": "old-provider",
                  "translationModelVersion": "old-model",
                  "translationStatus": "TRANSLATED",
                  "duplicateKey": "%s-duplicate",
                  "clusterKey": "%s-cluster",
                  "modelVersion": "old-model",
                  "eventConfidence": 0.55,
                  "sentimentConfidence": 0.55,
                  "importanceConfidence": 0.55,
                  "stockMatchConfidence": 1.0,
                  "createdAt": "2026-06-18T09:01:00Z"
                }
                """.formatted(alertId, stockCode, stockName, title, title, stockCode, alertId, alertId));
    }

    private HannahAiAnalysisResponse fullCollectionAnalysis(
            HannahAiAnalysisRequest request,
            String duplicateKey) {
        String subject = "NEWS".equals(request.sourceType())
                ? "KOSPI-listed Samsung Electronics reported an earnings-related stock market update."
                : "Samsung Electronics filed a material corporate disclosure.";
        AlertSummaryLines summaryLines = new AlertSummaryLines(
                subject,
                "The source links the event to semiconductor demand and corporate decisions.",
                "Investors should monitor earnings, liquidity, and the next official filing.");
        String summary = String.join("\n", summaryLines.what(), summaryLines.why(), summaryLines.impact());
        return new HannahAiAnalysisResponse(
                "005930",
                "삼성전자",
                request.sourceType(),
                request.title(),
                summaryLines.what(),
                summary,
                summaryLines,
                summary,
                "FULL_TEXT",
                request.content(),
                "Samsung Electronics reported a market-moving update in the source article. "
                        + "Semiconductor demand and corporate decisions were identified as the main drivers. "
                        + "Foreign and institutional flows supported the market reaction. "
                        + "Investors should monitor earnings, liquidity, and the next official filing.",
                request.imageUrls(),
                List.of("NEWS".equals(request.sourceType()) ? "EARNINGS" : "DISCLOSURE"),
                "POSITIVE",
                "HIGH",
                null,
                null,
                null,
                List.of("005930"),
                true,
                true,
                List.of(new HannahAiGlossaryTerm("실적", "실적", "earnings", "event")),
                List.of("FINANCIAL_GLOSSARY_APPLIED"),
                "local-open-source-qwen3-translation",
                "local-llm:Qwen3-4B-GGUF-Q4",
                "TRANSLATED",
                duplicateKey,
                duplicateKey,
                "financial-ml-tfidf-logreg-test",
                0.91,
                0.89,
                0.93,
                1.0);
    }

    private HannahAiAnalysisResponse fullQwenAnalysis(
            HannahAiAnalysisRequest request,
            String translatedTitle,
            AlertSummaryLines summaryLines,
            String translatedContent,
            List<HannahAiGlossaryTerm> glossaryTerms,
            List<String> qualityFlags,
            String duplicateKey) {
        String summary = String.join("\n", summaryLines.what(), summaryLines.why(), summaryLines.impact());
        return new HannahAiAnalysisResponse(
                "005930",
                "삼성전자",
                request.sourceType(),
                request.title(),
                translatedTitle,
                summary,
                summaryLines,
                summary,
                "FULL_TEXT",
                request.content(),
                translatedContent,
                request.imageUrls(),
                List.of("DISCLOSURE".equals(request.sourceType()) ? "DISCLOSURE" : "EARNINGS"),
                "POSITIVE",
                "HIGH",
                null,
                null,
                null,
                List.of("005930"),
                true,
                true,
                glossaryTerms,
                qualityFlags,
                "local-open-source-qwen3-translation",
                "local-llm:Qwen3-4B-GGUF-Q4",
                "TRANSLATED",
                duplicateKey,
                duplicateKey,
                "financial-ml-tfidf-logreg-test",
                0.91,
                0.89,
                0.93,
                1.0);
    }

    private HannahAiAnalysisResponse incompleteCollectionAnalysis(
            HannahAiAnalysisRequest request,
            String duplicateKey) {
        AlertSummaryLines summaryLines = new AlertSummaryLines(
                "Samsung Electronics reported a stock-market update.",
                "The source links the event to semiconductor demand.",
                "Investors should monitor the next earnings release.");
        String summary = String.join("\n", summaryLines.what(), summaryLines.why(), summaryLines.impact());
        return new HannahAiAnalysisResponse(
                "005930",
                "삼성전자",
                request.sourceType(),
                request.title(),
                "",
                summary,
                summaryLines,
                "",
                "FULL_TEXT",
                request.content(),
                "",
                request.imageUrls(),
                List.of("EARNINGS"),
                "POSITIVE",
                "HIGH",
                null,
                null,
                null,
                List.of("005930"),
                true,
                true,
                List.of(),
                List.of("LOCAL_TRANSLATION_PROVIDER_ERROR"),
                "source-language-fallback",
                "local-llm:Qwen3-4B-GGUF-Q4",
                "SOURCE_LANGUAGE_FALLBACK",
                duplicateKey,
                duplicateKey,
                "financial-ml-tfidf-logreg-test",
                0.91,
                0.89,
                0.93,
                1.0);
    }

    private ArticleTranslationResult translated(String text) {
        return new ArticleTranslationResult(
                englishTextFor(text),
                "local-open-source-qwen3-translation",
                "local-llm:Qwen3-4B-GGUF-Q4",
                "TRANSLATED");
    }

    private ArticleTranslationResult fatalTranslated(String text) {
        return new ArticleTranslationResult(
                text,
                "local-open-source-qwen3-translation",
                "local-llm:Qwen3-4B-GGUF-Q4",
                "TRANSLATED",
                List.of("HANGUL_REMAINS"));
    }

    private ArticleTranslationResult sourceLanguageFallback() {
        return new ArticleTranslationResult(
                "",
                "source-language-fallback",
                "local-llm:Qwen3-4B-GGUF-Q4",
                "SOURCE_LANGUAGE_FALLBACK");
    }

    private String englishTextFor(String text) {
        if (text == null || text.isBlank() || !containsHangul(text)) {
            return text;
        }
        if (text.length() > 20_000 && text.contains("자기주식 처분과 실적 전망")) {
            return ("Samsung Electronics disclosed treasury-share disposal details and earnings outlook. "
                    + "Investors should review the filing text, liquidity impact, and market reaction. ")
                    .repeat(180);
        }
        if (text.contains("HBM 수요 확대로 실적 개선 기대가 커졌습니다")) {
            return "Samsung Electronics expects stronger earnings as HBM demand expands.";
        }
        if (text.contains("데이터센터 투자가 주요 배경입니다")) {
            return "Data center investment is the main background.";
        }
        if (text.contains("영업이익 회복 속도를 확인해야 합니다")) {
            return "Investors should monitor the pace of operating-profit recovery.";
        }
        int marker = Math.abs(text.hashCode());
        if (text.contains("핵심 배경") || text.contains("주요 배경")) {
            return "The source article explains the main background for this market update " + marker + ".";
        }
        if (text.contains("주가가 강세를 보였다")) {
            return """
                    Samsung Electronics shares traded stronger as AI server investment lifted expectations for a semiconductor earnings recovery.
                    Securities firms said memory-price rebounds and broader HBM supply could support operating-profit improvement.
                    Foreign net buying and institutional demand also improved liquidity around the KOSPI bellwether.
                    Investors should monitor the next quarterly earnings release and the pace of chip-cycle recovery.
                    """;
        }
        if (text.contains("투자자는")) {
            return "Investors should monitor price, earnings, and liquidity effects for watched holdings " + marker + ".";
        }
        if (text.contains("원문은")) {
            return "The source article reports a verified company event for this market update " + marker + ".";
        }
        if (text.contains("삼성전자 실적 개선 HBM 수요 확대")) {
            return "Samsung Electronics earnings improve as HBM demand expands";
        }
        if (text.contains("삼성전자 실적 개선")) {
            return "Samsung Electronics earnings improve";
        }
        if (text.contains("북미 최대 교육 기술 전시회")) {
            return "Samsung showcases education displays at North America's largest edtech expo.";
        }
        if (text.contains("개미가 삼성전자를 순매수했다")) {
            return "Ants net bought Samsung Electronics.";
        }
        if (text.contains("AI 서버 투자 확대로 반도체 실적 개선 기대가 커졌다")) {
            return "AI server investment raised expectations for a semiconductor earnings recovery.";
        }
        if (text.contains("자기주식 취득과 소각 결정")) {
            return "Samsung Electronics disclosed a treasury share buyback and cancellation that may affect shareholder returns.";
        }
        return "The source article reports a verified market development " + marker + ".";
    }

    private boolean containsHangul(String text) {
        return text.chars().anyMatch(character -> character >= '가' && character <= '힣');
    }

    private String jsonString(String value) {
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "\"" + escaped + "\"";
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
