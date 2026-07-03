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
                    "한국 증시 전문 기반 요약",
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
                .andExpect(jsonPath("$.data.events[0].summary", equalTo("한국 증시 전문 기반 요약")))
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

    private TranslationResult translated(String text) {
        return new TranslationResult(text, "openai", "gpt-4o-mini", "TRANSLATED");
    }
}
