package com.hana.omnilens.marketnews.api;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

import com.hana.omnilens.alert.domain.AlertSummaryLines;
import com.hana.omnilens.alert.application.AlertTitleTranslationService;
import com.hana.omnilens.alert.application.AlertTitleTranslationService.TranslationResult;
import com.hana.omnilens.provider.ai.HannahAiAnalysisClient;
import com.hana.omnilens.provider.ai.HannahAiAnalysisRequest;
import com.hana.omnilens.provider.ai.HannahAiAnalysisResponse;
import com.hana.omnilens.provider.news.NaverNewsArticle;
import com.hana.omnilens.provider.news.NaverNewsClient;
import com.hana.omnilens.provider.news.OriginalArticleClient;
import com.hana.omnilens.provider.news.OriginalArticleContent;

@SpringBootTest(properties = {
        "omnilens.security.api-key-enabled=true",
        "omnilens.security.api-key-sha256=4c806362b613f7496abf284146efd31da90e4b16169fe001841ca17290f427c4",
        "omnilens.alert.dedupe.mode=in-memory",
        "omnilens.market-news.scheduler.enabled=false",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
class MarketNewsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private NaverNewsClient naverNewsClient;

    @MockitoBean
    private OriginalArticleClient originalArticleClient;

    @MockitoBean
    private HannahAiAnalysisClient hannahAiAnalysisClient;

    @MockitoBean
    private AlertTitleTranslationService alertTitleTranslationService;

    @BeforeEach
    void deleteMarketNews() {
        jdbcTemplate.update("DELETE FROM market_news_event");
    }

    @Test
    void collectThenListAndDetailMarketNews() throws Exception {
        when(naverNewsClient.search(eq("한국 증시"), eq(1))).thenReturn(List.of(new NaverNewsArticle(
                "코스피 상승 마감",
                "외국인 순매수로 한국 증시가 상승했다",
                "https://news.example.com/market/1",
                Instant.parse("2026-06-18T06:00:00Z"))));
        when(originalArticleClient.fetch("https://news.example.com/market/1"))
                .thenReturn(Optional.of(new OriginalArticleContent(
                        "한국 증시 전문",
                        List.of("https://news.example.com/image.jpg"),
                        "https://news.example.com/market/1",
                        "content-hash",
                        "licensed_naver_original_full_text_v1")));
        when(hannahAiAnalysisClient.analyze(any())).thenAnswer(invocation -> {
            HannahAiAnalysisRequest request = invocation.getArgument(0);
            return new HannahAiAnalysisResponse(
                    "005930",
                    "삼성전자",
                    request.sourceType(),
                    request.title(),
                    "한국 증시 전문에 근거한 요약입니다.",
                    List.of("MACRO"),
                    "POSITIVE",
                    "MEDIUM",
                    List.of("005930"),
                    false,
                    true,
                    List.of(),
                    List.of(),
                    "market-news-duplicate",
                    "financial-ml-tfidf-logreg-test",
                    0.88,
                    0.77,
                    0.76,
                    0.74);
        });
        when(alertTitleTranslationService.translateTitleWithResult(anyString(), any()))
                .thenAnswer(invocation -> translated(invocation.getArgument(0, String.class)));
        when(alertTitleTranslationService.translateTextWithResult(anyString(), any()))
                .thenAnswer(invocation -> translated(invocation.getArgument(0, String.class)));
        String expectedSummary = String.join("\n",
                "한국 증시 전문에 근거한 요약입니다.",
                "코스피 상승 마감의 핵심 배경은 원문에서 확인된 최신 시장·기업 이벤트입니다.",
                "투자자는 코스피 상승 마감 관련 보유·관심 종목의 가격, 실적, 수급 영향을 확인해야 합니다.");

        mockMvc.perform(post("/api/v1/market/news/collect")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "queries": ["한국 증시"],
                                  "display": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collectedCount", equalTo(1)))
                .andExpect(jsonPath("$.data.storedCount", equalTo(1)))
                .andExpect(jsonPath("$.data.events[0].query", equalTo("한국 증시")))
                .andExpect(jsonPath("$.data.events[0].title", equalTo("코스피 상승 마감")))
                .andExpect(jsonPath("$.data.events[0].summary", equalTo(expectedSummary)))
                .andExpect(jsonPath("$.data.events[0].contentAvailability", equalTo("FULL_TEXT")))
                .andExpect(jsonPath("$.data.events[0].translationProvider", equalTo("openai")))
                .andExpect(jsonPath("$.data.events[0].translationStatus", equalTo("TRANSLATED")));

        ArgumentCaptor<HannahAiAnalysisRequest> requestCaptor =
                ArgumentCaptor.forClass(HannahAiAnalysisRequest.class);
        verify(hannahAiAnalysisClient).analyze(requestCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().content())
                .isEqualTo("한국 증시 전문");

        String newsId = jdbcTemplate.queryForObject(
                "SELECT news_id FROM market_news_event LIMIT 1",
                String.class);

        mockMvc.perform(get("/api/v1/market/news")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newsCount", equalTo(1)))
                .andExpect(jsonPath("$.data.news[0].newsId", equalTo(newsId)));

        mockMvc.perform(get("/api/v1/market/news/{newsId}", newsId)
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newsId", equalTo(newsId)))
                .andExpect(jsonPath("$.data.originalContent", equalTo("한국 증시 전문")));
    }

    @Test
    void collectRejectsEllipsisAndMetaSummaryFragments() throws Exception {
        when(naverNewsClient.search(eq("반도체 ETF"), eq(1))).thenReturn(List.of(new NaverNewsArticle(
                "NH-Amundi운용, 반도체 ETF 리밸런싱...SK스퀘어 신규 편입",
                "SK하이닉스와 삼성전자 등 반도체 종목을 담는 ETF가 정기 리밸런싱을 마쳤다...",
                "https://news.example.com/market/fragment",
                Instant.parse("2026-06-18T07:00:00Z"))));
        when(originalArticleClient.fetch("https://news.example.com/market/fragment"))
                .thenReturn(Optional.empty());
        when(hannahAiAnalysisClient.analyze(any())).thenReturn(new HannahAiAnalysisResponse(
                "402340",
                "SK스퀘어",
                "NEWS",
                "NH-Amundi운용, 반도체 ETF 리밸런싱...SK스퀘어 신규 편입",
                "반도체 ETF 리밸런싱...",
                new AlertSummaryLines(
                        "반도체 ETF 리밸런싱...",
                        "SK스퀘어 신규 편입 배경은",
                        "The impact is classified as medium importance and neutral sentiment."),
                "SUMMARY_ONLY",
                "",
                List.of(),
                List.of("GENERAL_MARKET"),
                "NEUTRAL",
                "MEDIUM",
                List.of("402340"),
                false,
                true,
                List.of(),
                List.of(),
                "fragment-duplicate",
                "fragment-cluster",
                "financial-ml-tfidf-logreg-test",
                0.55,
                0.55,
                0.55,
                0.55));
        when(alertTitleTranslationService.translateTitleWithResult(anyString(), any()))
                .thenAnswer(invocation -> translated(invocation.getArgument(0, String.class)));
        when(alertTitleTranslationService.translateTextWithResult(anyString(), any()))
                .thenAnswer(invocation -> translated(invocation.getArgument(0, String.class)));

        String fallbackSummary = "원문은 NH-Amundi운용, 반도체 ETF 리밸런싱 SK스퀘어 신규 편입 관련 최신 시장·기업 이벤트를 다룹니다.";
        String fallbackWhy = "NH-Amundi운용, 반도체 ETF 리밸런싱 SK스퀘어 신규 편입의 핵심 배경은 원문에서 확인된 최신 시장·기업 이벤트입니다.";
        String fallbackImpact = "투자자는 NH-Amundi운용, 반도체 ETF 리밸런싱 SK스퀘어 신규 편입 관련 보유·관심 종목의 가격, 실적, 수급 영향을 확인해야 합니다.";
        String fallbackThreeLineSummary = String.join("\n", fallbackSummary, fallbackWhy, fallbackImpact);

        mockMvc.perform(post("/api/v1/market/news/collect")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "queries": ["반도체 ETF"],
                                  "display": 1
                                }
                """))
        .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events[0].summary", equalTo(fallbackThreeLineSummary)))
                .andExpect(jsonPath("$.data.events[0].summaryLines.what", equalTo(fallbackSummary)))
                .andExpect(jsonPath("$.data.events[0].summaryLines.why", equalTo(fallbackWhy)))
                .andExpect(jsonPath("$.data.events[0].summaryLines.impact", equalTo(fallbackImpact)));
    }

    @Test
    void reprocessQualityIssuesFixesStoredBrokenMarketNewsSummary() throws Exception {
        jdbcTemplate.update(
                """
                INSERT INTO market_news_event (
                    news_id, query, original_url, duplicate_key, published_at, created_at, event_json
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                "mkt-quality-issue",
                "반도체 ETF",
                "https://news.example.com/market/quality",
                "mkt-quality-duplicate",
                java.sql.Timestamp.from(Instant.parse("2026-06-18T07:00:00Z")),
                java.sql.Timestamp.from(Instant.parse("2026-06-18T07:01:00Z")),
                """
                {
                  "newsId": "mkt-quality-issue",
                  "query": "반도체 ETF",
                  "title": "반도체 ETF 리밸런싱...SK스퀘어 신규 편입",
                  "translatedTitle": "Semiconductor ETF rebalance...",
                  "summary": "반도체 ETF 리밸런싱...",
                  "summaryLines": {
                    "what": "반도체 ETF 리밸런싱...",
                    "why": "SK스퀘어 신규 편입 배경은",
                    "impact": "The impact is classified as medium importance and neutral sentiment."
                  },
                  "translatedSummary": "Semiconductor ETF rebalance...",
                  "originalContent": "반도체 ETF가 정기 리밸런싱을 마치고 SK스퀘어를 신규 편입했다. SK하이닉스와 삼성전자 비중 조정이 주요 배경이다. 투자자는 편입 종목의 수급과 변동성을 확인해야 한다.",
                  "translatedContent": "",
                  "imageUrls": [],
                  "contentAvailability": "FULL_TEXT",
                  "originalUrl": "https://news.example.com/market/quality",
                  "canonicalUrl": "https://news.example.com/market/quality",
                  "sourceLicensePolicy": "licensed_naver_original_full_text_v1",
                  "glossaryTerms": [],
                  "translationProvider": "old-provider",
                  "translationModelVersion": "old-model",
                  "translationStatus": "TRANSLATED",
                  "duplicateKey": "mkt-quality-duplicate",
                  "publishedAt": "2026-06-18T07:00:00Z",
                  "createdAt": "2026-06-18T07:01:00Z"
                }
                """);
        when(hannahAiAnalysisClient.analyze(any())).thenReturn(new HannahAiAnalysisResponse(
                "402340",
                "SK스퀘어",
                "NEWS",
                "반도체 ETF 리밸런싱...SK스퀘어 신규 편입",
                "반도체 ETF가 정기 리밸런싱을 마치고 SK스퀘어를 신규 편입했습니다.",
                new AlertSummaryLines(
                        "반도체 ETF가 정기 리밸런싱을 마치고 SK스퀘어를 신규 편입했습니다.",
                        "SK하이닉스와 삼성전자 비중 조정이 주요 배경입니다.",
                        "투자자는 편입 종목의 수급과 변동성을 확인해야 합니다."),
                "FULL_TEXT",
                "",
                List.of(),
                List.of("GENERAL_MARKET"),
                "NEUTRAL",
                "MEDIUM",
                List.of("402340"),
                false,
                true,
                List.of(),
                List.of(),
                "quality-duplicate",
                "quality-cluster",
                "financial-ml-tfidf-logreg-test",
                0.75,
                0.75,
                0.75,
                0.75));
        when(alertTitleTranslationService.translateTitleWithResult(anyString(), any()))
                .thenAnswer(invocation -> translated(invocation.getArgument(0, String.class)));
        when(alertTitleTranslationService.translateTextWithResult(anyString(), any()))
                .thenAnswer(invocation -> translated(invocation.getArgument(0, String.class)));

        mockMvc.perform(post("/api/v1/market/news/reprocess/quality-issues")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newsCount", equalTo(1)))
                .andExpect(jsonPath("$.data.news[0].summaryLines.what",
                        equalTo("반도체 ETF가 정기 리밸런싱을 마치고 SK스퀘어를 신규 편입했습니다.")))
                .andExpect(jsonPath("$.data.news[0].summaryLines.impact",
                        equalTo("투자자는 편입 종목의 수급과 변동성을 확인해야 합니다.")));

        String storedPayload = jdbcTemplate.queryForObject(
                "SELECT event_json FROM market_news_event WHERE news_id = 'mkt-quality-issue'",
                String.class);
        org.assertj.core.api.Assertions.assertThat(storedPayload)
                .contains("반도체 ETF가 정기 리밸런싱을 마치고 SK스퀘어를 신규 편입했습니다.")
                .contains("투자자는 편입 종목의 수급과 변동성을 확인해야 합니다.")
                .doesNotContain("The impact is classified")
                .doesNotContain("중요도")
                .doesNotContain("감성");
        ArgumentCaptor<HannahAiAnalysisRequest> requestCaptor =
                ArgumentCaptor.forClass(HannahAiAnalysisRequest.class);
        verify(hannahAiAnalysisClient).analyze(requestCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().snippet())
                .isEqualTo("반도체 ETF 리밸런싱...SK스퀘어 신규 편입");
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().content())
                .contains("반도체 ETF가 정기 리밸런싱을 마치고 SK스퀘어를 신규 편입했다.");
    }

    private TranslationResult translated(String text) {
        return new TranslationResult(text, "openai", "gpt-4o-mini", "TRANSLATED");
    }
}
