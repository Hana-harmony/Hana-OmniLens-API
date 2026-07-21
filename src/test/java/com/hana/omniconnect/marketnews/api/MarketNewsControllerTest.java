package com.hana.omniconnect.marketnews.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

import com.hana.omniconnect.alert.domain.AlertSummaryLines;
import com.hana.omniconnect.alert.application.ArticleTranslationResult;
import com.hana.omniconnect.alert.application.NewsTranslationEnrichmentAttemptStore;
import com.hana.omniconnect.marketnews.application.MarketNewsCollectionService;
import com.hana.omniconnect.provider.ai.HannahAiAnalysisClient;
import com.hana.omniconnect.provider.ai.HannahAiAnalysisRequest;
import com.hana.omniconnect.provider.ai.HannahAiAnalysisResponse;
import com.hana.omniconnect.provider.ai.HannahAiGlossaryTerm;
import com.hana.omniconnect.provider.news.NaverNewsArticle;
import com.hana.omniconnect.provider.news.NaverNewsClient;
import com.hana.omniconnect.provider.news.OriginalArticleClient;
import com.hana.omniconnect.provider.news.OriginalArticleContent;

@SpringBootTest(properties = {
        "omni-connect.alert.dedupe.mode=in-memory",
        "omni-connect.market-news.scheduler.enabled=false",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
class MarketNewsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MarketNewsCollectionService marketNewsCollectionService;

    @MockitoBean
    private NaverNewsClient naverNewsClient;

    @MockitoBean
    private OriginalArticleClient originalArticleClient;

    @MockitoBean
    private HannahAiAnalysisClient hannahAiAnalysisClient;

    @MockitoBean
    private NewsTranslationEnrichmentAttemptStore enrichmentAttemptStore;

    @BeforeEach
    void deleteMarketNews() {
        com.hana.omniconnect.support.PartnerCredentialTestData.replace(
                jdbcTemplate, "partner-market-news", "test-api-key");
        jdbcTemplate.update("DELETE FROM market_news_event");
        when(enrichmentAttemptStore.claim(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void collectThenListAndDetailMarketNews() throws Exception {
        String originalBody = "한국 증시는 외국인 순매수와 반도체 대형주 강세에 힘입어 상승 마감했다. "
                + "한국거래소에 따르면 코스피는 장중 변동성을 줄이며 주요 이동평균선을 회복했고 코스닥도 바이오와 2차전지주 반등으로 낙폭을 만회했다. "
                + "증권가는 환율 안정과 미국 기술주 실적 기대가 투자심리 개선에 영향을 줬다고 설명했다. "
                + "장 후반 기관 매수세도 유입되며 프로그램 매매가 지수 회복을 보탰고, 거래대금은 전 거래일보다 늘었다. "
                + "투자자는 외국인 수급 지속 여부와 삼성전자, SK하이닉스의 실적 전망을 함께 확인해야 한다.";
        when(naverNewsClient.search(eq("한국 증시"), eq(5))).thenReturn(List.of(new NaverNewsArticle(
                "코스피 상승 마감",
                "외국인 순매수로 한국 증시가 상승했다",
                "https://news.example.com/market/1",
                Instant.parse("2026-06-18T06:00:00Z"))));
        when(originalArticleClient.fetch("https://news.example.com/market/1"))
                .thenReturn(Optional.of(new OriginalArticleContent(
                        originalBody,
                        List.of("https://news.example.com/image.jpg"),
                        "https://news.example.com/market/1",
                        "content-hash",
                        "licensed_naver_original_full_text_v1")));
        when(hannahAiAnalysisClient.analyze(any())).thenAnswer(invocation -> {
            HannahAiAnalysisRequest request = invocation.getArgument(0);
            return fullMarketAnalysis(request, "market-news-duplicate");
        });
        String expectedEnglishWhat = "KOSPI closed higher as foreign investors bought large-cap semiconductor shares.";
        String expectedEnglishWhy = "Foreign net buying and technology earnings expectations drove the index rebound.";
        String expectedEnglishImpact = "Investors should monitor foreign flows and the earnings outlook for major chipmakers.";
        String expectedSummary = String.join("\n", expectedEnglishWhat, expectedEnglishWhy, expectedEnglishImpact);

        mockMvc.perform(post("/api/v1/market/news/collect")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
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
                .andExpect(jsonPath("$.data.events[0].translatedTitle", equalTo(expectedEnglishWhat)))
                .andExpect(jsonPath("$.data.events[0].summaryLines.what", equalTo(expectedEnglishWhat)))
                .andExpect(jsonPath("$.data.events[0].summaryLines.why", equalTo(expectedEnglishWhy)))
                .andExpect(jsonPath("$.data.events[0].summaryLines.impact", equalTo(expectedEnglishImpact)))
                .andExpect(jsonPath("$.data.events[0].translatedContent", equalTo("")))
                .andExpect(jsonPath("$.data.events[0].sentiment", equalTo("POSITIVE")))
                .andExpect(jsonPath("$.data.events[0].importance", equalTo("MEDIUM")))
                .andExpect(jsonPath("$.data.events[0].contentAvailability", equalTo("ORIGINAL_TEXT_ONLY")))
                .andExpect(jsonPath("$.data.events[0].translationProvider",
                        equalTo("local-open-source-qwen3-translation")))
                .andExpect(jsonPath("$.data.events[0].translationStatus",
                        equalTo("PARTIAL_SOURCE_LANGUAGE_FALLBACK")));

        ArgumentCaptor<HannahAiAnalysisRequest> requestCaptor =
                ArgumentCaptor.forClass(HannahAiAnalysisRequest.class);
        verify(hannahAiAnalysisClient).analyze(requestCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().content())
                .isEqualTo(originalBody);
        assertThat(requestCaptor.getValue().translationMode()).isEqualTo("DEFERRED");

        String newsId = jdbcTemplate.queryForObject(
                "SELECT news_id FROM market_news_event LIMIT 1",
                String.class);
        jdbcTemplate.update(
                "UPDATE market_news_event SET event_json = REPLACE(event_json, ?, ?) WHERE news_id = ?",
                "\"imageUrls\":[\"https://news.example.com/image.jpg\"]",
                "\"imageUrls\":[]",
                newsId);

        mockMvc.perform(get("/api/v1/market/news")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newsCount", equalTo(0)))
                .andExpect(jsonPath("$.data.news").isEmpty());
        mockMvc.perform(get("/api/v1/market/news/{newsId}", newsId)
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key"))
                .andExpect(status().isNotFound());

        assertThat(marketNewsCollectionService.enrichNextPendingFullTranslation()).isPresent();

        mockMvc.perform(get("/api/v1/market/news")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newsCount", equalTo(1)))
                .andExpect(jsonPath("$.data.news[0].newsId", equalTo(newsId)));

        mockMvc.perform(get("/api/v1/market/news/{newsId}", newsId)
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newsId", equalTo(newsId)))
                .andExpect(jsonPath("$.data.contentAvailability", equalTo("FULL_TEXT")))
                .andExpect(jsonPath("$.data.translatedContent", containsString("The Korean stock market closed higher")))
                .andExpect(jsonPath("$.data.originalContent", equalTo(originalBody)))
                .andExpect(jsonPath("$.data.imageUrls[0]", equalTo("https://news.example.com/image.jpg")));
    }

    @Test
    void collectRejectsEllipsisAndMetaSummaryFragments() throws Exception {
        when(naverNewsClient.search(eq("반도체 ETF"), eq(5))).thenReturn(List.of(new NaverNewsArticle(
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

        String fallbackSummary = "원문은 NH-Amundi운용, 반도체 ETF 리밸런싱 SK스퀘어 신규 편입 관련 최신 시장·기업 이벤트를 다룹니다.";
        String fallbackWhy = "NH-Amundi운용, 반도체 ETF 리밸런싱 SK스퀘어 신규 편입의 핵심 배경은 원문에서 확인된 최신 시장·기업 이벤트입니다.";
        String fallbackImpact = "투자자는 NH-Amundi운용, 반도체 ETF 리밸런싱 SK스퀘어 신규 편입 관련 보유·관심 종목의 가격, 실적, 수급 영향을 확인해야 합니다.";
        String fallbackThreeLineSummary = String.join("\n", fallbackSummary, fallbackWhy, fallbackImpact);
        String englishFallbackWhat = englishTextFor(fallbackSummary);
        String englishFallbackWhy = englishTextFor(fallbackWhy);
        String englishFallbackImpact = englishTextFor(fallbackImpact);

        mockMvc.perform(post("/api/v1/market/news/collect")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "queries": ["반도체 ETF"],
                                  "display": 1
                                }
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storedCount", equalTo(0)))
                .andExpect(jsonPath("$.data.events").isEmpty());
    }

    @Test
    void collectSkipsMarketQueryResultWithoutMarketEvidenceBeforeTranslation() throws Exception {
        when(naverNewsClient.search(eq("코스피"), eq(5))).thenReturn(List.of(new NaverNewsArticle(
                "[세상 읽기]계란값을 잡을 것인가, 산란계협회를 잡을 것인가",
                "계란값과 산란계협회 관련 칼럼입니다.",
                "https://www.khan.co.kr/article/202607092021005",
                Instant.parse("2026-07-09T11:00:00Z"))));

        mockMvc.perform(post("/api/v1/market/news/collect")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "queries": ["코스피"],
                                  "display": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collectedCount", equalTo(1)))
                .andExpect(jsonPath("$.data.storedCount", equalTo(0)))
                .andExpect(jsonPath("$.data.events").isEmpty());

        verify(originalArticleClient, never()).fetch(anyString());
        verify(hannahAiAnalysisClient, never()).analyze(any());
    }

    @Test
    void collectSearchesExtraCandidatesAndStoresOnlyMarketWideTargetCount() throws Exception {
        String marketBody = "한국 증시는 외국인 순매수와 반도체 대형주 강세에 힘입어 상승 마감했다. "
                + "한국거래소에 따르면 코스피는 장중 변동성을 줄이며 주요 이동평균선을 회복했고 코스닥도 바이오와 2차전지주 반등으로 낙폭을 만회했다. "
                + "증권가는 환율 안정과 미국 기술주 실적 기대가 투자심리 개선에 영향을 줬다고 설명했다. "
                + "장 후반 기관 매수세도 유입되며 프로그램 매매가 지수 회복을 보탰고, 거래대금은 전 거래일보다 늘었다. "
                + "투자자는 외국인 수급 지속 여부와 삼성전자, SK하이닉스의 실적 전망을 함께 확인해야 한다.";
        when(naverNewsClient.search(eq("한국 증시"), eq(5))).thenReturn(List.of(
                new NaverNewsArticle(
                        "[세상 읽기]계란값을 잡을 것인가, 산란계협회를 잡을 것인가",
                        "계란값과 산란계협회 관련 칼럼입니다.",
                        "https://www.khan.co.kr/article/202607092021005",
                        Instant.parse("2026-07-09T11:00:00Z")),
                new NaverNewsArticle(
                        "코스피 상승 마감",
                        "외국인 순매수로 한국 증시가 상승했다",
                        "https://news.example.com/market/extra-candidate",
                        Instant.parse("2026-06-18T06:00:00Z"))));
        when(originalArticleClient.fetch("https://news.example.com/market/extra-candidate"))
                .thenReturn(Optional.of(new OriginalArticleContent(
                        marketBody,
                        List.of("https://news.example.com/image.jpg"),
                        "https://news.example.com/market/extra-candidate",
                        "extra-candidate-hash",
                        "licensed_naver_original_full_text_v1",
                        "코스피 상승 마감")));
        when(hannahAiAnalysisClient.analyze(any())).thenAnswer(invocation -> {
            HannahAiAnalysisRequest request = invocation.getArgument(0);
            return fullMarketAnalysis(request, "extra-candidate-duplicate");
        });

        mockMvc.perform(post("/api/v1/market/news/collect")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "queries": ["한국 증시"],
                                  "display": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collectedCount", equalTo(2)))
                .andExpect(jsonPath("$.data.storedCount", equalTo(1)))
                .andExpect(jsonPath("$.data.events[0].title", equalTo("코스피 상승 마감")));

        verify(originalArticleClient, never()).fetch("https://www.khan.co.kr/article/202607092021005");
    }

    @Test
    void collectSkipsSingleIssuerArticleWhenOriginalPageTitleContradictsMarketSearchResult() throws Exception {
        String stockOnlyBody = "SK하이닉스가 미국 나스닥 ADR 공모가를 최종 확정했다. "
                + "회사는 해외 투자자 대상 수요예측 결과를 반영해 가격을 정했다고 설명했다. "
                + "이번 공모는 개별 기업의 자금조달 일정과 주주 구성 변화에 관한 내용이다. "
                + "투자자는 상장 이후 유통 물량과 기존 주주 지분 변화를 확인해야 한다.";
        when(naverNewsClient.search(eq("한국 증시"), eq(5))).thenReturn(List.of(new NaverNewsArticle(
                "코스피 하락 속 반도체주 움직임",
                "한국 증시와 반도체 투자심리를 함께 다룬 기사입니다.",
                "https://news.example.com/market/sk-adr",
                Instant.parse("2026-06-18T06:00:00Z"))));
        when(originalArticleClient.fetch("https://news.example.com/market/sk-adr"))
                .thenReturn(Optional.of(new OriginalArticleContent(
                        stockOnlyBody,
                        List.of(),
                        "https://news.example.com/market/sk-adr",
                        "sk-adr-hash",
                        "licensed_naver_original_full_text_v1",
                        "SK하이닉스, 美나스닥 ADR 공모가 149달러 최종 확정…IPO 규모 中알리바바 제쳤다")));

        mockMvc.perform(post("/api/v1/market/news/collect")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "queries": ["한국 증시"],
                                  "display": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collectedCount", equalTo(1)))
                .andExpect(jsonPath("$.data.storedCount", equalTo(0)))
                .andExpect(jsonPath("$.data.events").isEmpty());

        verify(hannahAiAnalysisClient, never()).analyze(any());
    }

    @Test
    void collectSkipsShortOriginalBodyWithoutFullArticleText() throws Exception {
        when(naverNewsClient.search(eq("코스피 단문"), eq(5))).thenReturn(List.of(new NaverNewsArticle(
                "코스피 5% 하락",
                "코스피와 코스닥이 급락했다",
                "https://news.example.com/market/short-body",
                Instant.parse("2026-06-18T07:30:00Z"))));
        when(originalArticleClient.fetch("https://news.example.com/market/short-body"))
                .thenReturn(Optional.of(new OriginalArticleContent(
                        "코스피는 전 거래일보다 5% 하락했고 코스닥도 800선을 밑돌았다.",
                        List.of(),
                        "https://news.example.com/market/short-body",
                        "short-hash",
                        "licensed_naver_original_full_text_v1")));
        when(hannahAiAnalysisClient.analyze(any())).thenReturn(new HannahAiAnalysisResponse(
                "",
                "",
                "NEWS",
                "코스피 5% 하락",
                "코스피와 코스닥이 급락했습니다.",
                new AlertSummaryLines(
                        "코스피와 코스닥이 급락했습니다.",
                        "반도체 약세가 주요 배경입니다.",
                        "투자자는 변동성을 확인해야 합니다."),
                "SUMMARY_ONLY",
                "",
                List.of(),
                List.of("GENERAL_MARKET"),
                "NEGATIVE",
                "MEDIUM",
                List.of(),
                false,
                false,
                List.of(),
                List.of(),
                "short-body-duplicate",
                "short-body-cluster",
                "financial-ml-tfidf-logreg-test",
                0.75,
                0.75,
                0.75,
                0.75));

        mockMvc.perform(post("/api/v1/market/news/collect")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "queries": ["코스피 단문"],
                                  "display": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storedCount", equalTo(0)))
                .andExpect(jsonPath("$.data.events").isEmpty());
    }

    @Test
    void listAndDetailHideStoredShortFullTextAvailability() throws Exception {
        jdbcTemplate.update(
                """
                INSERT INTO market_news_event (
                    news_id, query, original_url, duplicate_key, published_at, created_at, event_json
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                "mkt-stale-short-full-text",
                "코스피 단문",
                "https://news.example.com/market/stale-short",
                "mkt-stale-short-duplicate",
                java.sql.Timestamp.from(Instant.parse("2026-06-18T07:40:00Z")),
                java.sql.Timestamp.from(Instant.parse("2026-06-18T07:41:00Z")),
                """
                {
                  "newsId": "mkt-stale-short-full-text",
                  "query": "코스피 단문",
                  "title": "코스피 단문",
                  "translatedTitle": "KOSPI brief",
                  "summary": "코스피 단문 요약",
                  "summaryLines": {
                    "what": "KOSPI moved.",
                    "why": "Semiconductors were weak.",
                    "impact": "Investors should monitor volatility."
                  },
                  "translatedSummary": "KOSPI moved.",
                  "originalContent": "코스피는 전 거래일보다 5% 하락했고 코스닥도 800선을 밑돌았다.",
                  "translatedContent": "KOSPI fell 5%.",
                  "imageUrls": [],
                  "contentAvailability": "FULL_TEXT",
                  "originalUrl": "https://news.example.com/market/stale-short",
                  "canonicalUrl": "https://news.example.com/market/stale-short",
                  "sourceLicensePolicy": "licensed_naver_original_full_text_v1",
                  "glossaryTerms": [],
                  "translationProvider": "old-provider",
                  "translationModelVersion": "old-model",
                  "translationStatus": "TRANSLATED",
                  "duplicateKey": "mkt-stale-short-duplicate",
                  "publishedAt": "2026-06-18T07:40:00Z",
                  "createdAt": "2026-06-18T07:41:00Z"
                }
                """);

        mockMvc.perform(get("/api/v1/market/news")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newsCount", equalTo(0)))
                .andExpect(jsonPath("$.data.news").isEmpty());

        mockMvc.perform(get("/api/v1/market/news/mkt-stale-short-full-text")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key"))
                .andExpect(status().isNotFound());
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
        when(hannahAiAnalysisClient.analyze(any())).thenAnswer(invocation ->
                fullMarketAnalysis(invocation.getArgument(0), "quality-duplicate"));
        String repairedTranslatedContent = "The semiconductor ETF completed its regular rebalance and added SK Square. "
                + "Adjustments to SK Hynix and Samsung Electronics weights were the main background. "
                + "Investors should monitor supply-demand and volatility for the added constituents.";
        String repairedWhat = "KOSPI closed higher as foreign investors bought large-cap semiconductor shares.";
        String repairedImpact = "Investors should monitor foreign flows and the earnings outlook for major chipmakers.";

        mockMvc.perform(post("/api/v1/market/news/reprocess/quality-issues")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newsCount", equalTo(1)))
                .andExpect(jsonPath("$.data.news[0].summaryLines.what",
                        equalTo(repairedWhat)))
                .andExpect(jsonPath("$.data.news[0].summaryLines.impact",
                        equalTo(repairedImpact)));

        String storedPayload = jdbcTemplate.queryForObject(
                "SELECT event_json FROM market_news_event WHERE news_id = 'mkt-quality-issue'",
                String.class);
        org.assertj.core.api.Assertions.assertThat(storedPayload)
                .contains(repairedWhat)
                .contains(repairedImpact)
                .contains("The Korean stock market closed higher")
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
        when(hannahAiAnalysisClient.analyze(any())).thenAnswer(invocation ->
                fullMarketAnalysis(invocation.getArgument(0), "blank-lines-duplicate"));
        String repairedWhy = "Foreign net buying and technology earnings expectations drove the index rebound.";
        String repairedImpact = "Investors should monitor foreign flows and the earnings outlook for major chipmakers.";

        mockMvc.perform(post("/api/v1/market/news/reprocess/quality-issues")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newsCount", equalTo(1)))
                .andExpect(jsonPath("$.data.news[0].summaryLines.why",
                        equalTo(repairedWhy)))
                .andExpect(jsonPath("$.data.news[0].summaryLines.impact",
                        equalTo(repairedImpact)));
    }

    @Test
    void reprocessQualityIssuesDoesNotStoreSourceLanguageFallbackAsTranslatedContent() throws Exception {
        String originalContent = """
                삼성전자는 HBM 수요 확대로 실적 개선 기대가 커졌다. 데이터센터 투자가 주요 배경이다.
                투자자는 영업이익 회복 속도와 메모리 공급 부족 지속 여부를 확인해야 한다.
                회사는 주요 고객사의 AI 서버 투자가 늘면서 고부가 메모리 판매 비중이 커지고 있다고 설명했다.
                증권가는 하반기 수급 개선과 가격 반등이 실적에 반영될 가능성이 높다고 평가했다.
                다만 환율과 글로벌 기술주 변동성이 단기 주가에 영향을 줄 수 있다고 덧붙였다.
                """;
        String originalJsonContent = originalContent.replace("\n", "\\n").replace("\"", "\\\"");
        jdbcTemplate.update(
                """
                INSERT INTO market_news_event (
                    news_id, query, original_url, duplicate_key, published_at, created_at, event_json
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                "mkt-source-fallback-content",
                "삼성전자",
                "https://news.example.com/market/source-fallback",
                "mkt-source-fallback-duplicate",
                java.sql.Timestamp.from(Instant.parse("2026-06-18T07:00:00Z")),
                java.sql.Timestamp.from(Instant.parse("2026-06-18T07:01:00Z")),
                """
                {
                  "newsId": "mkt-source-fallback-content",
                  "query": "삼성전자",
                  "title": "삼성전자 HBM 수요 확대",
                  "translatedTitle": "Samsung Electronics HBM demand expands",
                  "summary": "삼성전자는 HBM 수요 확대로 실적 개선 기대가 커졌다.",
                  "summaryLines": {
                    "what": "Samsung Electronics expects earnings to improve as HBM demand expands.",
                    "why": "Data center investment is the main driver.",
                    "impact": "Investors should monitor operating-profit recovery."
                  },
                  "translatedSummary": "Samsung Electronics expects earnings to improve as HBM demand expands. Data center investment is the main driver. Investors should monitor operating-profit recovery.",
                  "originalContent": "%s",
                  "translatedContent": "What: Samsung Electronics expects earnings to improve as HBM demand expands. Why: Data center investment is the main driver. Impact: Investors should monitor operating-profit recovery.",
                  "imageUrls": [],
                  "contentAvailability": "FULL_TEXT",
                  "originalUrl": "https://news.example.com/market/source-fallback",
                  "canonicalUrl": "https://news.example.com/market/source-fallback",
                  "sourceLicensePolicy": "licensed_naver_original_full_text_v1",
                  "glossaryTerms": [],
                  "translationProvider": "old-provider",
                  "translationModelVersion": "old-model",
                  "translationStatus": "TRANSLATED",
                  "duplicateKey": "mkt-source-fallback-duplicate",
                  "publishedAt": "2026-06-18T07:00:00Z",
                  "createdAt": "2026-06-18T07:01:00Z"
                }
                """.formatted(originalJsonContent));

        mockMvc.perform(post("/api/v1/market/news/reprocess/quality-issues")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newsCount", equalTo(0)));

        String persistedJson = jdbcTemplate.queryForObject(
                "SELECT event_json FROM market_news_event WHERE news_id = ?",
                String.class,
                "mkt-source-fallback-content");
        assertThat(persistedJson)
                .contains("\"translationStatus\": \"TRANSLATED\"")
                .doesNotContain("The original Korean text is retained because machine translation was unavailable")
                .doesNotContain("\"contentAvailability\":\"ORIGINAL_TEXT_ONLY\"");
    }

    @Test
    void reprocessByNewsIdRefetchesShortOriginalContentImagesAndGlossaryDescriptions() throws Exception {
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
                  "originalContent": "코스피 5% 하락 단문 요약",
                  "translatedContent": "",
                  "imageUrls": [],
                  "contentAvailability": "SUMMARY_ONLY",
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
            AlertSummaryLines summaryLines = new AlertSummaryLines(
                    "Samjeon Nix returns are drawing market attention.",
                    "Foreign buying and semiconductor-cycle expectations are the main background.",
                    "Investors should monitor supply-demand and earnings expectations for major semiconductor stocks.");
            String summary = String.join("\n", summaryLines.what(), summaryLines.why(), summaryLines.impact());
            return new HannahAiAnalysisResponse(
                    "",
                    "",
                    "NEWS",
                    request.title(),
                    summaryLines.what(),
                    summary,
                    summaryLines,
                    summary,
                    "FULL_TEXT",
                    request.content(),
                    "Samjeon Nix is Korean market slang for Samsung Electronics and SK Hynix. "
                            + "Foreign buying led strength in major semiconductor stocks. "
                            + "Investors should monitor supply-demand and earnings expectations.",
                    request.imageUrls(),
                    List.of("GENERAL_MARKET"),
                    "POSITIVE",
                    "MEDIUM",
                    null,
                    null,
                    null,
                    List.of(),
                    false,
                    true,
                    List.of(new HannahAiGlossaryTerm(
                            "삼전닉스",
                            "삼전닉스",
                            "Samjeon Nix",
                            "market_slang")),
                    List.of("FINANCIAL_GLOSSARY_APPLIED"),
                    "local-open-source-qwen3-translation",
                    "local-llm:Qwen3-4B-GGUF-Q4",
                    "TRANSLATED",
                    "samjeon-nix-duplicate",
                    "samjeon-nix-cluster",
                    "financial-ml-tfidf-logreg-test",
                    0.75,
                    0.75,
                    0.75,
                    0.0);
        });

        mockMvc.perform(post("/api/v1/market/news/{newsId}/reprocess", "mkt-samjeon-nix")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newsId", equalTo("mkt-samjeon-nix")))
                .andExpect(jsonPath("$.data.originalContent").value(org.hamcrest.Matchers.containsString("삼전닉스는")))
                .andExpect(jsonPath("$.data.imageUrls[0]",
                        equalTo("https://img.example.com/news/samjeon-nix.png")))
                .andExpect(jsonPath("$.data.contentAvailability", equalTo("SUMMARY_ONLY")))
                .andExpect(jsonPath("$.data.glossaryTerms[0].sourceTerm", equalTo("Samjeon Nix")))
                .andExpect(jsonPath("$.data.glossaryTerms[0].description").value(
                        org.hamcrest.Matchers.containsString("Samsung Electronics and SK Hynix")));
    }

    private HannahAiAnalysisResponse fullMarketAnalysis(
            HannahAiAnalysisRequest request,
            String duplicateKey) {
        AlertSummaryLines summaryLines = new AlertSummaryLines(
                "KOSPI closed higher as foreign investors bought large-cap semiconductor shares.",
                "Foreign net buying and technology earnings expectations drove the index rebound.",
                "Investors should monitor foreign flows and the earnings outlook for major chipmakers.");
        String summary = String.join("\n", summaryLines.what(), summaryLines.why(), summaryLines.impact());
        return new HannahAiAnalysisResponse(
                "",
                "",
                request.sourceType(),
                request.title(),
                summaryLines.what(),
                summary,
                summaryLines,
                summary,
                "FULL_TEXT",
                request.content(),
                "The Korean stock market closed higher as foreign investors bought large-cap semiconductor shares. "
                        + "Technology earnings expectations and a stable exchange rate improved investor sentiment. "
                        + "Institutional buying strengthened late in the session as trading value increased. "
                        + "Investors should monitor foreign flows and the earnings outlook for major chipmakers.",
                request.imageUrls(),
                List.of("MACRO"),
                "POSITIVE",
                "MEDIUM",
                null,
                null,
                null,
                List.of(),
                false,
                true,
                List.of(),
                List.of(),
                "local-open-source-qwen3-translation",
                "local-llm:Qwen3-4B-GGUF-Q4",
                "TRANSLATED",
                duplicateKey,
                duplicateKey,
                "financial-ml-tfidf-logreg-test",
                0.88,
                0.77,
                0.76,
                0.74);
    }

    private ArticleTranslationResult translated(String text) {
        return new ArticleTranslationResult(
                englishTextFor(text),
                "local-open-source-qwen3-translation",
                "local-llm:Qwen3-4B-GGUF-Q4",
                "TRANSLATED");
    }

    private ArticleTranslationResult sourceLanguageFallback(String text) {
        return new ArticleTranslationResult(
                text,
                "source-language-fallback",
                "local-llm:Qwen3-4B-GGUF-Q4",
                "SOURCE_LANGUAGE_FALLBACK");
    }

    private String englishTextFor(String text) {
        if (text == null || text.isBlank() || !containsHangul(text)) {
            return text;
        }
        if (text.contains("반도체 ETF가 정기 리밸런싱")) {
            if (text.contains("SK하이닉스와 삼성전자 비중 조정")) {
                return "The semiconductor ETF completed its regular rebalance and added SK Square. "
                        + "Adjustments to SK Hynix and Samsung Electronics weights were the main background. "
                        + "Investors should monitor supply-demand and volatility for the added constituents.";
            }
            return "The semiconductor ETF completed its regular rebalance and added SK Square.";
        }
        if (text.contains("SK하이닉스와 삼성전자 비중 조정")) {
            return "Adjustments to SK Hynix and Samsung Electronics weights were the main background.";
        }
        if (text.contains("편입 종목의 수급과 변동성")) {
            return "Investors should monitor supply-demand and volatility for the added constituents.";
        }
        if (text.contains("한국 증시는 외국인 순매수와 반도체 대형주 강세")) {
            return "Korean stocks closed higher on foreign net buying and strength in large semiconductor shares. "
                    + "According to the Korea Exchange, KOSPI reduced intraday volatility and recovered key moving averages, while KOSDAQ also pared losses as biotechnology and secondary-battery shares rebounded. "
                    + "Brokerages said foreign-exchange stability and expectations for U.S. technology earnings helped improve investor sentiment. "
                    + "Late institutional buying also supported the index recovery through program trading, and trading value increased from the previous session. "
                    + "The article added that investors were watching whether risk appetite would continue into the next session as semiconductor bellwethers remained the main driver of market direction. "
                    + "Investors should monitor whether foreign buying continues and review the earnings outlooks for Samsung Electronics and SK Hynix.";
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
        return "The source article reports a verified market development " + marker + ".";
    }

    private boolean containsHangul(String text) {
        return text.chars().anyMatch(character -> character >= '가' && character <= '힣');
    }
}
