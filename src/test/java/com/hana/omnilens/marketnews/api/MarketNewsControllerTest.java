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
import com.hana.omnilens.provider.ai.HannahAiGlossaryTerm;
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
        String expectedEnglishWhat = englishTextFor("한국 증시 전문에 근거한 요약입니다.");
        String expectedEnglishWhy = englishTextFor(
                "코스피 상승 마감의 핵심 배경은 원문에서 확인된 최신 시장·기업 이벤트입니다.");
        String expectedEnglishImpact = englishTextFor(
                "투자자는 코스피 상승 마감 관련 보유·관심 종목의 가격, 실적, 수급 영향을 확인해야 합니다.");
        String expectedTranslatedContent = englishTextFor("한국 증시 전문");

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
                .andExpect(jsonPath("$.data.events[0].translatedTitle", equalTo("KOSPI closes higher")))
                .andExpect(jsonPath("$.data.events[0].summaryLines.what", equalTo(expectedEnglishWhat)))
                .andExpect(jsonPath("$.data.events[0].summaryLines.why", equalTo(expectedEnglishWhy)))
                .andExpect(jsonPath("$.data.events[0].summaryLines.impact", equalTo(expectedEnglishImpact)))
                .andExpect(jsonPath("$.data.events[0].translatedContent", equalTo(expectedTranslatedContent)))
                .andExpect(jsonPath("$.data.events[0].sentiment", equalTo("POSITIVE")))
                .andExpect(jsonPath("$.data.events[0].importance", equalTo("MEDIUM")))
                .andExpect(jsonPath("$.data.events[0].contentAvailability", equalTo("FULL_TEXT")))
                .andExpect(jsonPath("$.data.events[0].translationProvider",
                        equalTo("local-open-source-qwen3-translation")))
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
        String englishFallbackWhat = englishTextFor(fallbackSummary);
        String englishFallbackWhy = englishTextFor(fallbackWhy);
        String englishFallbackImpact = englishTextFor(fallbackImpact);

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
                .andExpect(jsonPath("$.data.events[0].summaryLines.what", equalTo(englishFallbackWhat)))
                .andExpect(jsonPath("$.data.events[0].summaryLines.why", equalTo(englishFallbackWhy)))
                .andExpect(jsonPath("$.data.events[0].summaryLines.impact", equalTo(englishFallbackImpact)))
                .andExpect(jsonPath("$.data.events[0].sentiment", equalTo("NEUTRAL")))
                .andExpect(jsonPath("$.data.events[0].importance", equalTo("MEDIUM")));
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
        String repairedTranslatedContent = "The semiconductor ETF completed its regular rebalance and added SK Square. "
                + "Adjustments to SK Hynix and Samsung Electronics weights were the main background. "
                + "Investors should monitor supply-demand and volatility for the added constituents.";
        when(alertTitleTranslationService.translateTextWithResult(anyString(), any()))
                .thenAnswer(invocation -> {
                    String text = invocation.getArgument(0, String.class);
                    if (text.equals("반도체 ETF가 정기 리밸런싱을 마치고 SK스퀘어를 신규 편입했다. SK하이닉스와 삼성전자 비중 조정이 주요 배경이다. 투자자는 편입 종목의 수급과 변동성을 확인해야 한다.")) {
                        return translated(repairedTranslatedContent);
                    }
                    return translated(text);
                });
        String englishFallbackWhat = englishTextFor(
                "원문은 반도체 ETF 리밸런싱 SK스퀘어 신규 편입 관련 최신 시장·기업 이벤트를 다룹니다.");
        String englishFallbackImpact = englishTextFor(
                "투자자는 반도체 ETF 리밸런싱 SK스퀘어 신규 편입 관련 보유·관심 종목의 가격, 실적, 수급 영향을 확인해야 합니다.");

        mockMvc.perform(post("/api/v1/market/news/reprocess/quality-issues")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newsCount", equalTo(1)))
                .andExpect(jsonPath("$.data.news[0].summaryLines.what",
                        equalTo(englishFallbackWhat)))
                .andExpect(jsonPath("$.data.news[0].summaryLines.impact",
                        equalTo(englishFallbackImpact)));

        String storedPayload = jdbcTemplate.queryForObject(
                "SELECT event_json FROM market_news_event WHERE news_id = 'mkt-quality-issue'",
                String.class);
        org.assertj.core.api.Assertions.assertThat(storedPayload)
                .contains(englishFallbackWhat)
                .contains(englishFallbackImpact)
                .contains(repairedTranslatedContent)
                .doesNotContain("The impact is classified")
                .doesNotContain("중요도")
                .doesNotContain("감성");
    }

    @Test
    void reprocessQualityIssuesFixesBlankMarketNewsSummaryLines() throws Exception {
        jdbcTemplate.update(
                """
                INSERT INTO market_news_event (
                    news_id, query, original_url, duplicate_key, published_at, created_at, event_json
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                "mkt-blank-lines",
                "반도체 ETF",
                "https://news.example.com/market/blank-lines",
                "mkt-blank-lines-duplicate",
                java.sql.Timestamp.from(Instant.parse("2026-06-18T07:00:00Z")),
                java.sql.Timestamp.from(Instant.parse("2026-06-18T07:01:00Z")),
                """
                {
                  "newsId": "mkt-blank-lines",
                  "query": "반도체 ETF",
                  "title": "반도체 ETF 리밸런싱 SK스퀘어 신규 편입",
                  "translatedTitle": "Semiconductor ETF rebalance",
                  "summary": "반도체 ETF가 SK스퀘어를 신규 편입했습니다.",
                  "summaryLines": {
                    "what": "반도체 ETF가 SK스퀘어를 신규 편입했습니다.",
                    "why": "",
                    "impact": ""
                  },
                  "translatedSummary": "반도체 ETF가 SK스퀘어를 신규 편입했습니다.",
                  "originalContent": "반도체 ETF가 정기 리밸런싱을 마치고 SK스퀘어를 신규 편입했다. SK하이닉스와 삼성전자 비중 조정이 주요 배경이다. 투자자는 편입 종목의 수급과 변동성을 확인해야 한다.",
                  "translatedContent": "",
                  "imageUrls": [],
                  "contentAvailability": "FULL_TEXT",
                  "originalUrl": "https://news.example.com/market/blank-lines",
                  "canonicalUrl": "https://news.example.com/market/blank-lines",
                  "sourceLicensePolicy": "licensed_naver_original_full_text_v1",
                  "glossaryTerms": [],
                  "translationProvider": "old-provider",
                  "translationModelVersion": "old-model",
                  "translationStatus": "TRANSLATED",
                  "duplicateKey": "mkt-blank-lines-duplicate",
                  "publishedAt": "2026-06-18T07:00:00Z",
                  "createdAt": "2026-06-18T07:01:00Z"
                }
                """);
        when(hannahAiAnalysisClient.analyze(any())).thenReturn(new HannahAiAnalysisResponse(
                "402340",
                "SK스퀘어",
                "NEWS",
                "반도체 ETF 리밸런싱 SK스퀘어 신규 편입",
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
                "blank-lines-duplicate",
                "blank-lines-cluster",
                "financial-ml-tfidf-logreg-test",
                0.75,
                0.75,
                0.75,
                0.75));
        when(alertTitleTranslationService.translateTitleWithResult(anyString(), any()))
                .thenAnswer(invocation -> translated(invocation.getArgument(0, String.class)));
        when(alertTitleTranslationService.translateTextWithResult(anyString(), any()))
                .thenAnswer(invocation -> translated(invocation.getArgument(0, String.class)));
        String englishFallbackWhy = englishTextFor(
                "반도체 ETF 리밸런싱 SK스퀘어 신규 편입의 핵심 배경은 원문에서 확인된 최신 시장·기업 이벤트입니다.");
        String englishFallbackImpact = englishTextFor(
                "투자자는 반도체 ETF 리밸런싱 SK스퀘어 신규 편입 관련 보유·관심 종목의 가격, 실적, 수급 영향을 확인해야 합니다.");

        mockMvc.perform(post("/api/v1/market/news/reprocess/quality-issues")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newsCount", equalTo(1)))
                .andExpect(jsonPath("$.data.news[0].summaryLines.why",
                        equalTo(englishFallbackWhy)))
                .andExpect(jsonPath("$.data.news[0].summaryLines.impact",
                        equalTo(englishFallbackImpact)));
    }

    @Test
    void reprocessByNewsIdRefetchesMissingOriginalContentImagesAndGlossaryDescriptions() throws Exception {
        jdbcTemplate.update(
                """
                INSERT INTO market_news_event (
                    news_id, query, original_url, duplicate_key, published_at, created_at, event_json
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                "mkt-samjeon-nix",
                "한국 증시",
                "https://news.example.com/market/samjeon-nix",
                "mkt-samjeon-nix-duplicate",
                java.sql.Timestamp.from(Instant.parse("2026-07-04T07:00:00Z")),
                java.sql.Timestamp.from(Instant.parse("2026-07-04T07:01:00Z")),
                """
                {
                  "newsId": "mkt-samjeon-nix",
                  "query": "한국 증시",
                  "title": "\\"삼전닉스\\" 수익률 안부럽다",
                  "translatedTitle": "Samjeon Nix returns are enviable.",
                  "summary": "",
                  "summaryLines": {
                    "what": "",
                    "why": "",
                    "impact": ""
                  },
                  "translatedSummary": "",
                  "originalContent": "",
                  "translatedContent": "",
                  "imageUrls": [],
                  "contentAvailability": "DISCOVERY_ONLY",
                  "originalUrl": "https://news.example.com/market/samjeon-nix",
                  "canonicalUrl": "https://news.example.com/market/samjeon-nix",
                  "sourceLicensePolicy": "DISCOVERY_ONLY",
                  "glossaryTerms": [],
                  "sentiment": "POSITIVE",
                  "importance": "MEDIUM",
                  "translationProvider": "old-provider",
                  "translationModelVersion": "old-model",
                  "translationStatus": "TRANSLATED",
                  "duplicateKey": "mkt-samjeon-nix-duplicate",
                  "publishedAt": "2026-07-04T07:00:00Z",
                  "createdAt": "2026-07-04T07:01:00Z"
                }
                """);
        when(originalArticleClient.fetch("https://news.example.com/market/samjeon-nix"))
                .thenReturn(Optional.of(new OriginalArticleContent(
                        "삼전닉스는 삼성전자와 SK하이닉스를 함께 부르는 시장 신조어다. 외국인 순매수가 반도체 대형주 강세를 이끌었다. 투자자는 반도체 대형주의 수급과 실적 기대를 확인해야 한다.",
                        List.of("https://img.example.com/news/samjeon-nix.png"),
                        "https://news.example.com/market/samjeon-nix",
                        "refetched-content-hash",
                        "licensed_naver_original_full_text_v1")));
        when(hannahAiAnalysisClient.analyze(any())).thenAnswer(invocation -> {
            HannahAiAnalysisRequest request = invocation.getArgument(0);
            org.assertj.core.api.Assertions.assertThat(request.content()).contains("삼전닉스는");
            org.assertj.core.api.Assertions.assertThat(request.imageUrls())
                    .containsExactly("https://img.example.com/news/samjeon-nix.png");
            return new HannahAiAnalysisResponse(
                    "",
                    "",
                    "NEWS",
                    request.title(),
                    "삼전닉스 수익률 강세가 시장 관심을 끌었습니다.",
                    new AlertSummaryLines(
                            "삼전닉스 수익률 강세가 시장 관심을 끌었습니다.",
                            "외국인 순매수와 반도체 업황 기대가 주요 배경입니다.",
                            "투자자는 반도체 대형주의 수급과 실적 기대를 확인해야 합니다."),
                    "FULL_TEXT",
                    request.content(),
                    request.imageUrls(),
                    List.of("GENERAL_MARKET"),
                    "POSITIVE",
                    "MEDIUM",
                    List.of(),
                    false,
                    true,
                    List.of(new HannahAiGlossaryTerm(
                            "삼전닉스",
                            "삼전닉스",
                            "Samjeon Nix",
                            "market_slang")),
                    List.of("FINANCIAL_GLOSSARY_APPLIED"),
                    "samjeon-nix-duplicate",
                    "samjeon-nix-cluster",
                    "financial-ml-tfidf-logreg-test",
                    0.75,
                    0.75,
                    0.75,
                    0.0);
        });
        when(alertTitleTranslationService.translateTitleWithResult(anyString(), any()))
                .thenAnswer(invocation -> {
                    String text = invocation.getArgument(0, String.class);
                    if (text.contains("삼전닉스")) {
                        return translated("Samjeon Nix returns are drawing market attention.");
                    }
                    return translated(text);
                });
        when(alertTitleTranslationService.translateTextWithResult(anyString(), any()))
                .thenAnswer(invocation -> {
                    String text = invocation.getArgument(0, String.class);
                    if (text.contains("삼전닉스 수익률 강세")) {
                        return translated("Samjeon Nix returns are drawing market attention.");
                    }
                    if (text.contains("외국인 순매수와 반도체 업황")) {
                        return translated("Foreign buying and semiconductor-cycle expectations are the main background.");
                    }
                    if (text.contains("반도체 대형주의 수급과 실적")) {
                        return translated("Investors should monitor supply-demand and earnings expectations for major semiconductor stocks.");
                    }
                    if (text.contains("삼전닉스는 삼성전자")) {
                        return translated("Samjeon Nix is Korean market slang for Samsung Electronics and SK Hynix. Foreign buying led strength in major semiconductor stocks. Investors should monitor supply-demand and earnings expectations.");
                    }
                    return translated(text);
                });

        mockMvc.perform(post("/api/v1/market/news/{newsId}/reprocess", "mkt-samjeon-nix")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newsId", equalTo("mkt-samjeon-nix")))
                .andExpect(jsonPath("$.data.originalContent").value(org.hamcrest.Matchers.containsString("삼전닉스는")))
                .andExpect(jsonPath("$.data.imageUrls[0]",
                        equalTo("https://img.example.com/news/samjeon-nix.png")))
                .andExpect(jsonPath("$.data.contentAvailability", equalTo("FULL_TEXT")))
                .andExpect(jsonPath("$.data.glossaryTerms[0].sourceTerm", equalTo("Samjeon Nix")))
                .andExpect(jsonPath("$.data.glossaryTerms[0].description").value(
                        org.hamcrest.Matchers.containsString("Samsung Electronics and SK Hynix")));
    }

    private TranslationResult translated(String text) {
        return new TranslationResult(
                englishTextFor(text),
                "local-open-source-qwen3-translation",
                "local-llm:mlx-community/Qwen3-0.6B-4bit",
                "TRANSLATED");
    }

    private String englishTextFor(String text) {
        if (text == null || text.isBlank() || !containsHangul(text)) {
            return text;
        }
        int marker = Math.abs(text.hashCode());
        if (text.contains("핵심 배경") || text.contains("주요 배경")) {
            return "The source article explains the main background for this market update " + marker + ".";
        }
        if (text.contains("투자자는")) {
            return "Investors should monitor supply, demand, and price effects for watched holdings " + marker + ".";
        }
        if (text.contains("원문은")) {
            return "The source article reports a verified market-wide development " + marker + ".";
        }
        if (text.contains("코스피 상승 마감")) {
            return "KOSPI closes higher";
        }
        if (text.contains("한국 증시 전문")) {
            return "The Korean market article summarizes the latest index move.";
        }
        if (text.contains("반도체 ETF가 정기 리밸런싱")) {
            return "The semiconductor ETF completed its regular rebalance and added SK Square.";
        }
        if (text.contains("SK하이닉스와 삼성전자 비중 조정")) {
            return "Adjustments to SK Hynix and Samsung Electronics weights were the main background.";
        }
        if (text.contains("편입 종목의 수급과 변동성")) {
            return "Investors should monitor supply-demand and volatility for the added constituents.";
        }
        return "The source article reports a verified market development " + marker + ".";
    }

    private boolean containsHangul(String text) {
        return text.chars().anyMatch(character -> character >= '가' && character <= '힣');
    }
}
