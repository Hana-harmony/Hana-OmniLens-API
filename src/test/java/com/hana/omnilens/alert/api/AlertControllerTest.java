package com.hana.omnilens.alert.api;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
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

import com.hana.omnilens.alert.application.AlertTitleTranslationService;
import com.hana.omnilens.alert.application.AlertTitleTranslationService.TranslationResult;
import com.hana.omnilens.alert.domain.AlertSummaryLines;
import com.hana.omnilens.provider.ai.HannahAiAnalysisClient;
import com.hana.omnilens.provider.ai.HannahAiAnalysisRequest;
import com.hana.omnilens.provider.ai.HannahAiAnalysisResponse;
import com.hana.omnilens.provider.ai.HannahAiGlossaryTerm;
import com.hana.omnilens.provider.disclosure.OpenDartDisclosure;
import com.hana.omnilens.provider.disclosure.OpenDartDisclosureClient;
import com.hana.omnilens.provider.disclosure.OpenDartDisclosureDocument;
import com.hana.omnilens.provider.news.NaverNewsArticle;
import com.hana.omnilens.provider.news.NaverNewsClient;
import com.hana.omnilens.provider.news.OriginalArticleClient;
import com.hana.omnilens.provider.news.OriginalArticleContent;

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
    private OriginalArticleClient originalArticleClient;

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
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.status", equalTo(200)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_000")))
                .andExpect(jsonPath("$.data.partnerId", equalTo("partner-api")))
                .andExpect(jsonPath("$.data.stockCodes[0]", equalTo("005930")))
                .andExpect(jsonPath("$.data.stockCodes[1]", equalTo("000660")));

        mockMvc.perform(get("/api/v1/alerts/watchlists/partner-api")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.partnerId", equalTo("partner-api")))
                .andExpect(jsonPath("$.data.stockCodes[0]", equalTo("005930")))
                .andExpect(jsonPath("$.data.stockCodes[1]", equalTo("000660")));
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
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("MARKET_001")))
                .andExpect(jsonPath("$.message", equalTo("Stock master row not found: 999999")));
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
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_002")));
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
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("AUTH_005")));
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
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.partnerId", equalTo("partner-a")))
                .andExpect(jsonPath("$.data.stockCodes[0]", equalTo("005930")));
    }

    @Test
    void analyzeAndPublishReturnsAnalyzedAlertEvent() throws Exception {
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
                List.of(new HannahAiGlossaryTerm("실적", "실적", "earnings", "event")),
                List.of("FINANCIAL_GLOSSARY_APPLIED"),
                "duplicate-key",
                "financial-keyword-baseline-2026-06-04",
                0.91,
                0.89,
                0.93,
                1.0));
        when(alertTitleTranslationService.translateTitleWithResult(eq("삼성전자 실적 개선"), any()))
                .thenReturn(translated("Samsung Electronics earnings improve"));
        when(alertTitleTranslationService.translateTextWithResult(any(), any()))
                .thenAnswer(invocation -> {
                    String text = invocation.getArgument(0, String.class);
                    if ("반도체 회복으로 실적 개선 기대가 커졌습니다.".equals(text)) {
                        return translated("Chip recovery raised earnings hopes.");
                    }
                    return translated(text);
                });
        String expectedSummary = String.join("\n",
                "반도체 회복으로 실적 개선 기대가 커졌습니다.",
                "삼성전자 실적 개선의 핵심 배경은 원문에서 확인된 최신 시장·기업 이벤트입니다.",
                "투자자는 삼성전자 실적 개선 관련 보유·관심 종목의 가격, 실적, 수급 영향을 확인해야 합니다.");

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
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.status", equalTo(200)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_000")))
                .andExpect(jsonPath("$.data.partnerId", equalTo("partner-a")))
                .andExpect(jsonPath("$.data.stockCode", equalTo("005930")))
		                .andExpect(jsonPath("$.data.translatedTitle", equalTo("Samsung Electronics earnings improve")))
		                .andExpect(jsonPath("$.data.summary", equalTo(expectedSummary)))
		                .andExpect(jsonPath("$.data.summaryLines.what", equalTo("Chip recovery raised earnings hopes.")))
		                .andExpect(jsonPath("$.data.summaryLines.why",
		                        equalTo("삼성전자 실적 개선의 핵심 배경은 원문에서 확인된 최신 시장·기업 이벤트입니다.")))
		                .andExpect(jsonPath("$.data.summaryLines.impact",
		                        equalTo("투자자는 삼성전자 실적 개선 관련 보유·관심 종목의 가격, 실적, 수급 영향을 확인해야 합니다.")))
		                .andExpect(jsonPath("$.data.importance", equalTo("HIGH")))
                .andExpect(jsonPath("$.data.holderTarget", equalTo(true)))
                .andExpect(jsonPath("$.data.watchlistTarget", equalTo(true)))
                .andExpect(jsonPath("$.data.glossaryTerms[0].sourceTerm", equalTo("earnings")))
                .andExpect(jsonPath("$.data.glossaryTerms[0].englishTerm", equalTo("earnings")))
                .andExpect(jsonPath("$.data.translationQualityFlags[0]", equalTo("FINANCIAL_GLOSSARY_APPLIED")))
                .andExpect(jsonPath("$.data.duplicateKey", equalTo("duplicate-key")))
                .andExpect(jsonPath("$.data.modelVersion", equalTo("financial-keyword-baseline-2026-06-04")))
                .andExpect(jsonPath("$.data.eventConfidence", equalTo(0.91)))
                .andExpect(jsonPath("$.data.stockMatchConfidence", equalTo(1.0)));
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
        when(alertTitleTranslationService.translateTitleWithResult(any(), any()))
                .thenAnswer(invocation -> translated(invocation.getArgument(0, String.class)));
        when(alertTitleTranslationService.translateTextWithResult(any(), any()))
                .thenAnswer(invocation -> translated(invocation.getArgument(0, String.class)));

        String fallbackSummary = "원문은 삼성전자 실적 개선 HBM 수요 확대 관련 최신 시장·기업 이벤트를 다룹니다.";
        String fallbackWhy = "삼성전자 실적 개선 HBM 수요 확대의 핵심 배경은 원문에서 확인된 최신 시장·기업 이벤트입니다.";
        String fallbackImpact = "투자자는 삼성전자 실적 개선 HBM 수요 확대 관련 보유·관심 종목의 가격, 실적, 수급 영향을 확인해야 합니다.";
        String fallbackThreeLineSummary = String.join("\n", fallbackSummary, fallbackWhy, fallbackImpact);

        mockMvc.perform(post("/api/v1/alerts/analyze-and-publish")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary", equalTo(fallbackThreeLineSummary)))
                .andExpect(jsonPath("$.data.summaryLines.what", equalTo(fallbackSummary)))
                .andExpect(jsonPath("$.data.summaryLines.why", equalTo(fallbackWhy)))
                .andExpect(jsonPath("$.data.summaryLines.impact", equalTo(fallbackImpact)));
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
        when(hannahAiAnalysisClient.analyze(any())).thenReturn(new HannahAiAnalysisResponse(
                "005930",
                "삼성전자",
                "NEWS",
                "삼성전자 실적 개선...HBM 수요 확대",
                "삼성전자는 HBM 수요 확대로 실적 개선 기대가 커졌습니다.",
                new AlertSummaryLines(
                        "삼성전자는 HBM 수요 확대로 실적 개선 기대가 커졌습니다.",
                        "데이터센터 투자가 주요 배경입니다.",
                        "투자자는 영업이익 회복 속도를 확인해야 합니다."),
                "FULL_TEXT",
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
                "quality-alert-duplicate",
                "quality-alert-cluster",
                "financial-keyword-baseline-2026-06-04",
                0.75,
                0.75,
                0.75,
                1.0));
        when(alertTitleTranslationService.translateTitleWithResult(any(), any()))
                .thenAnswer(invocation -> translated(invocation.getArgument(0, String.class)));
        when(alertTitleTranslationService.translateTextWithResult(any(), any()))
                .thenAnswer(invocation -> translated(invocation.getArgument(0, String.class)));

        mockMvc.perform(post("/api/v1/alerts/events/reprocess/quality-issues")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventCount", equalTo(1)))
                .andExpect(jsonPath("$.data.events[0].summaryLines.what",
                        equalTo("삼성전자는 HBM 수요 확대로 실적 개선 기대가 커졌습니다.")))
                .andExpect(jsonPath("$.data.events[0].summaryLines.impact",
                        equalTo("투자자는 영업이익 회복 속도를 확인해야 합니다.")));

        String storedPayload = jdbcTemplate.queryForObject(
                "SELECT event_json FROM alert_event WHERE alert_id = 'alert-quality-issue'",
                String.class);
        org.assertj.core.api.Assertions.assertThat(storedPayload)
                .contains("삼성전자는 HBM 수요 확대로 실적 개선 기대가 커졌습니다.")
                .contains("투자자는 영업이익 회복 속도를 확인해야 합니다.")
                .doesNotContain("The impact is classified")
                .doesNotContain("중요도")
                .doesNotContain("감성");
        ArgumentCaptor<HannahAiAnalysisRequest> requestCaptor =
                ArgumentCaptor.forClass(HannahAiAnalysisRequest.class);
        verify(hannahAiAnalysisClient).analyze(requestCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().snippet())
                .isEqualTo("삼성전자 실적 개선...HBM 수요 확대");
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().content())
                .contains("삼성전자는 HBM 수요 확대로 실적 개선 기대가 커졌다.");
    }

    @Test
    void reprocessQualityIssuesSkipsFailedAlertAndContinues() throws Exception {
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
        when(hannahAiAnalysisClient.analyze(any()))
                .thenThrow(new RuntimeException("analysis unavailable"))
                .thenReturn(new HannahAiAnalysisResponse(
                        "005930",
                        "삼성전자",
                        "NEWS",
                        "삼성전자 실적 개선...HBM 수요 확대",
                        "삼성전자는 HBM 수요 확대로 실적 개선 기대가 커졌습니다.",
                        new AlertSummaryLines(
                                "삼성전자는 HBM 수요 확대로 실적 개선 기대가 커졌습니다.",
                                "데이터센터 투자가 주요 배경입니다.",
                                "투자자는 영업이익 회복 속도를 확인해야 합니다."),
                        "FULL_TEXT",
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
                        "quality-alert-match-duplicate",
                        "quality-alert-match-cluster",
                        "financial-keyword-baseline-2026-06-04",
                        0.75,
                        0.75,
                        0.75,
                        1.0));
        when(alertTitleTranslationService.translateTitleWithResult(any(), any()))
                .thenAnswer(invocation -> translated(invocation.getArgument(0, String.class)));
        when(alertTitleTranslationService.translateTextWithResult(any(), any()))
                .thenAnswer(invocation -> translated(invocation.getArgument(0, String.class)));

        mockMvc.perform(post("/api/v1/alerts/events/reprocess/quality-issues")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventCount", equalTo(1)))
                .andExpect(jsonPath("$.data.events[0].alertId", equalTo("alert-quality-match")))
                .andExpect(jsonPath("$.data.events[0].summaryLines.what",
                        equalTo("삼성전자는 HBM 수요 확대로 실적 개선 기대가 커졌습니다.")))
                .andExpect(jsonPath("$.data.events[0].summaryLines.impact",
                        equalTo("투자자는 영업이익 회복 속도를 확인해야 합니다.")));

        String matchedPayload = jdbcTemplate.queryForObject(
                "SELECT event_json FROM alert_event WHERE alert_id = 'alert-quality-match'",
                String.class);
        String failedPayload = jdbcTemplate.queryForObject(
                "SELECT event_json FROM alert_event WHERE alert_id = 'alert-quality-failed'",
                String.class);
        org.assertj.core.api.Assertions.assertThat(matchedPayload)
                .contains("삼성전자는 HBM 수요 확대로 실적 개선 기대가 커졌습니다.")
                .doesNotContain("The impact is classified");
        org.assertj.core.api.Assertions.assertThat(failedPayload)
                .contains("The impact is classified");
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
        when(alertTitleTranslationService.translateTitleWithResult(any(), any()))
                .thenAnswer(invocation -> translated(invocation.getArgument(0, String.class)));
        when(alertTitleTranslationService.translateTextWithResult(any(), any()))
                .thenAnswer(invocation -> translated(invocation.getArgument(0, String.class)));

        mockMvc.perform(post("/api/v1/alerts/events/alert-title-only/reprocess")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockCode", equalTo("005930")))
                .andExpect(jsonPath("$.data.stockName", equalTo("삼성전자")))
                .andExpect(jsonPath("$.data.stockMatchConfidence", equalTo(0.5)))
                .andExpect(jsonPath("$.data.summaryLines.what",
                        equalTo("북미 최대 교육 기술 전시회서 삼성 교육용 전자칠판 공개.")))
                .andExpect(jsonPath("$.data.summaryLines.impact",
                        equalTo("투자자는 B2B 디스플레이 매출 기여도를 확인해야 합니다.")));

        ArgumentCaptor<HannahAiAnalysisRequest> requestCaptor =
                ArgumentCaptor.forClass(HannahAiAnalysisRequest.class);
        verify(hannahAiAnalysisClient).analyze(requestCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().snippet())
                .isEqualTo("북미 최대 교육 기술 전시회서 삼성 교육용 전자칠판 공개");
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().content()).isEmpty();
    }

    @Test
    void analyzeAndPublishUsesTranslatedSurfaceTermForGlossaryClickTarget() throws Exception {
        when(hannahAiAnalysisClient.analyze(any())).thenReturn(new HannahAiAnalysisResponse(
                "005930",
                "삼성전자",
                "NEWS",
                "삼성전자 개미 순매수",
                "개미가 삼성전자를 순매수했다",
                List.of("MARKET"),
                "POSITIVE",
                "HIGH",
                List.of("005930"),
                true,
                true,
                List.of(new HannahAiGlossaryTerm("개미", "개미", "retail investors", "market_slang")),
                List.of("FINANCIAL_GLOSSARY_APPLIED"),
                "duplicate-key-ant-surface",
                "financial-keyword-baseline-2026-06-04",
                0.91,
                0.89,
                0.93,
                1.0));
        when(alertTitleTranslationService.translateTitleWithResult(eq("삼성전자 개미 순매수"), any()))
                .thenReturn(translated("Samsung Electronics Ants net bought"));
        when(alertTitleTranslationService.translateTextWithResult(any(), any()))
                .thenAnswer(invocation -> {
                    String text = invocation.getArgument(0, String.class);
                    return translated(text.replace("개미가 삼성전자를 순매수했다", "Ants net bought Samsung Electronics."));
                });

        mockMvc.perform(post("/api/v1/alerts/analyze-and-publish")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "partnerId": "partner-a",
                                  "sourceType": "NEWS",
                                  "title": "삼성전자 개미 순매수",
                                  "snippet": "개미가 삼성전자를 순매수했다",
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
                .andExpect(jsonPath("$.data.glossaryTerms[0].sourceTerm", equalTo("Ants")))
                .andExpect(jsonPath("$.data.glossaryTerms[0].normalizedTerm", equalTo("개미")))
                .andExpect(jsonPath("$.data.glossaryTerms[0].englishTerm", equalTo("retail investors")));
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
        when(originalArticleClient.fetch("https://news.example.com/1"))
                .thenReturn(Optional.of(new OriginalArticleContent(
                        "삼성전자는 AI 서버 투자 확대로 반도체 실적 개선 기대가 커졌다.",
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
                    List.of(new HannahAiGlossaryTerm("실적", "실적", "earnings", "event")),
                    List.of("FINANCIAL_GLOSSARY_APPLIED"),
                    "duplicate-key",
                    "financial-ml-tfidf-logreg-test",
                    0.91,
                    0.89,
                    0.93,
                    1.0);
        });
        when(alertTitleTranslationService.translateTitleWithResult(any(), any()))
                .thenAnswer(invocation -> translated(invocation.getArgument(0, String.class)));
        when(alertTitleTranslationService.translateTextWithResult(any(), any()))
                .thenAnswer(invocation -> translated(invocation.getArgument(0, String.class)));

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
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.status", equalTo(200)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_000")))
                .andExpect(jsonPath("$.data.partnerId", equalTo("partner-a")))
                .andExpect(jsonPath("$.data.collectedNewsCount", equalTo(3)))
                .andExpect(jsonPath("$.data.collectedDisclosureCount", equalTo(1)))
                .andExpect(jsonPath("$.data.publishedCount", equalTo(2)))
                .andExpect(jsonPath("$.data.skippedDuplicateCount", equalTo(2)))
                .andExpect(jsonPath("$.data.failedAnalysisCount", equalTo(0)))
                .andExpect(jsonPath("$.data.events[0].sourceType", equalTo("NEWS")))
                .andExpect(jsonPath("$.data.events[0].duplicateKey", equalTo("duplicate-key")))
                .andExpect(jsonPath("$.data.events[0].translationQualityFlags[0]",
                        equalTo("FINANCIAL_GLOSSARY_APPLIED")))
                .andExpect(jsonPath("$.data.events[0].modelVersion", equalTo("financial-ml-tfidf-logreg-test")))
                .andExpect(jsonPath("$.data.events[0].contentAvailability", equalTo("FULL_TEXT")))
                .andExpect(jsonPath("$.data.events[0].originalContent",
                        equalTo("삼성전자는 AI 서버 투자 확대로 반도체 실적 개선 기대가 커졌다.")))
                .andExpect(jsonPath("$.data.events[0].translatedContent",
                        equalTo("삼성전자는 AI 서버 투자 확대로 반도체 실적 개선 기대가 커졌다.")))
                .andExpect(jsonPath("$.data.events[0].imageUrls[0]",
                        equalTo("https://news.example.com/images/1.jpg")))
                .andExpect(jsonPath("$.data.events[1].sourceType", equalTo("DISCLOSURE")))
                .andExpect(jsonPath("$.data.events[1].contentAvailability", equalTo("FULL_TEXT")))
                .andExpect(jsonPath("$.data.events[1].originalContent",
                        equalTo("삼성전자 주요사항보고서 전문이다. 자기주식 취득과 소각 결정으로 주주환원 영향이 있다.")));
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
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_002")));
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
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_002")));
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

    private TranslationResult translated(String text) {
        return new TranslationResult(text, "openai", "gpt-4o-mini", "TRANSLATED");
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
