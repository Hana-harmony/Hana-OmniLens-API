package com.hana.omnilens.term.api;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hana.omnilens.provider.ai.HannahAiFinancialTermEvidence;
import com.hana.omnilens.provider.ai.HannahAiKoreanFinancialTermClient;
import com.hana.omnilens.provider.ai.HannahAiKoreanFinancialTermExplainResponse;

@SpringBootTest(properties = {
        "omnilens.security.api-key-enabled=true",
        "omnilens.security.api-key-sha256=4c806362b613f7496abf284146efd31da90e4b16169fe001841ca17290f427c4",
        "omnilens.security.rate-limit.enabled=false",
        "omnilens.security.signature.enabled=false",
        "omnilens.alert.dedupe.mode=in-memory",
        "omnilens.alert.scheduler.enabled=false",
        "omnilens.market-news.scheduler.enabled=false",
        "omnilens.market.foreign-ownership-refresh.enabled=false",
        "omnilens.market.foreign-ownership-model-training.enabled=false",
        "omnilens.market.foreign-ownership-prediction-precompute.enabled=false",
        "omnilens.market.kis-realtime.enabled=false",
        "omnilens.market.chart-warmup.enabled=false",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
class KoreanFinancialTermControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private HannahAiKoreanFinancialTermClient hannahAiClient;

    @BeforeEach
    void deleteTermData() {
        jdbcTemplate.update("DELETE FROM korean_financial_term_click_log");
        jdbcTemplate.update("DELETE FROM korean_financial_term_click_stats");
        jdbcTemplate.update("DELETE FROM korean_financial_term_explanation_cache");
    }

    @Test
    void explainRecordsClickAndReusesCache() throws Exception {
        when(hannahAiClient.explain(any())).thenReturn(dictionaryResponse());

        String payload = """
                {
                  "term": "개미",
                  "locale": "en",
                  "sourceType": "NEWS",
                  "title": "개미 매수세 확대",
                  "context": "개미 투자자들이 코스닥 시장에서 순매수했다.",
                  "stockCode": "005930",
                  "stockName": "삼성전자",
                  "articleId": "news-1",
                  "articleUrl": "https://news.example.com/1",
                  "userKey": "user-1",
                  "sessionKey": "session-1"
                }
                """;

        mockMvc.perform(post("/api/v1/korean-financial-terms/explain")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.term", equalTo("개미")))
                .andExpect(jsonPath("$.data.displayMode", equalTo("EXPLANATION")))
                .andExpect(jsonPath("$.data.source", equalTo("DICTIONARY")))
                .andExpect(jsonPath("$.data.cacheHit", equalTo(false)))
                .andExpect(jsonPath("$.data.clickCount", equalTo(1)));

        mockMvc.perform(post("/api/v1/korean-financial-terms/explain")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cacheHit", equalTo(true)))
                .andExpect(jsonPath("$.data.clickCount", equalTo(2)));

        verify(hannahAiClient, times(1)).explain(any());
        Number clickCount = jdbcTemplate.queryForObject(
                "SELECT click_count FROM korean_financial_term_click_stats WHERE normalized_term = '개미'",
                Number.class);
        Number cacheHitCount = jdbcTemplate.queryForObject(
                "SELECT cache_hit_count FROM korean_financial_term_click_stats WHERE normalized_term = '개미'",
                Number.class);
        org.assertj.core.api.Assertions.assertThat(clickCount.longValue()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(cacheHitCount.longValue()).isEqualTo(1);
    }

    @Test
    void reviewRequiredResponseIsNotCachedButStillCounted() throws Exception {
        when(hannahAiClient.explain(any())).thenReturn(reviewRequiredResponse());

        String payload = """
                {
                  "term": "새신조어",
                  "locale": "en",
                  "sourceType": "NEWS",
                  "context": "아직 검증되지 않은 시장 표현이다.",
                  "articleId": "news-2"
                }
                """;

        mockMvc.perform(post("/api/v1/korean-financial-terms/explain")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayMode", equalTo("REVIEW_REQUIRED")))
                .andExpect(jsonPath("$.data.cacheHit", equalTo(false)))
                .andExpect(jsonPath("$.data.clickCount", equalTo(1)));

        mockMvc.perform(post("/api/v1/korean-financial-terms/explain")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cacheHit", equalTo(false)))
                .andExpect(jsonPath("$.data.clickCount", equalTo(2)));

        verify(hannahAiClient, times(2)).explain(any());
        Number reviewCount = jdbcTemplate.queryForObject(
                "SELECT review_required_count FROM korean_financial_term_click_stats WHERE normalized_term = '새신조어'",
                Number.class);
        org.assertj.core.api.Assertions.assertThat(reviewCount.longValue()).isEqualTo(2);
    }

    @Test
    void statsListsClickedTerms() throws Exception {
        when(hannahAiClient.explain(any())).thenReturn(dictionaryResponse());

        mockMvc.perform(post("/api/v1/korean-financial-terms/explain")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "term": "개미",
                                  "locale": "en",
                                  "sourceType": "NEWS",
                                  "articleId": "news-3"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/korean-financial-terms/stats")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].normalizedTerm", equalTo("개미")))
                .andExpect(jsonPath("$.data[0].clickCount", equalTo(1)));
    }

    @Test
    void translatedEnglishTermClickIsCountedByCanonicalKoreanTerm() throws Exception {
        when(hannahAiClient.explain(any())).thenReturn(new HannahAiKoreanFinancialTermExplainResponse(
                "retail investors",
                "개미",
                "retail investor",
                "market_participant",
                "Individual retail investors in Korean stock-market slang.",
                "Retail investors refers to individual investors in translated Korean market news.",
                "In a translated article, \"retail investors\" is the clickable local-market term.",
                new BigDecimal("0.96"),
                "HIGH",
                "EXPLANATION",
                "DICTIONARY",
                true,
                2592000,
                List.of(),
                List.of("DICTIONARY_HIT", "CACHEABLE"),
                "k-finance-term-rag-v2",
                Instant.parse("2026-07-01T00:00:00Z")));

        mockMvc.perform(post("/api/v1/korean-financial-terms/explain")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "term": "retail investors",
                                  "locale": "en",
                                  "sourceType": "NEWS",
                                  "articleId": "news-english-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.term", equalTo("retail investors")))
                .andExpect(jsonPath("$.data.normalizedTerm", equalTo("개미")))
                .andExpect(jsonPath("$.data.explanation", equalTo(
                        "Retail investors refers to individual investors in translated Korean market news.")));

        Number clickCount = jdbcTemplate.queryForObject(
                "SELECT click_count FROM korean_financial_term_click_stats WHERE normalized_term = '개미'",
                Number.class);
        org.assertj.core.api.Assertions.assertThat(clickCount.longValue()).isEqualTo(1);
    }

    private HannahAiKoreanFinancialTermExplainResponse dictionaryResponse() {
        return new HannahAiKoreanFinancialTermExplainResponse(
                "개미",
                "개미",
                "retail investors",
                "market_participant",
                "Individual retail investors in Korean stock-market slang.",
                "Retail investors refers to individual investors in translated Korean market news.",
                "In a translated article, \"retail investors\" is the clickable local-market term.",
                new BigDecimal("0.96"),
                "HIGH",
                "EXPLANATION",
                "DICTIONARY",
                true,
                2_592_000,
                List.of(new HannahAiFinancialTermEvidence(
                        "Seed dictionary",
                        "Curated Korean financial term glossary.",
                        "",
                        "INTERNAL_DICTIONARY")),
                List.of(),
                "k-finance-term-rag-v1",
                Instant.parse("2026-07-01T00:00:00Z"));
    }

    private HannahAiKoreanFinancialTermExplainResponse reviewRequiredResponse() {
        return new HannahAiKoreanFinancialTermExplainResponse(
                "새신조어",
                "새신조어",
                "",
                "unverified",
                "",
                "This term needs human review before we show an automated explanation.",
                "",
                new BigDecimal("0.10"),
                "LOW",
                "REVIEW_REQUIRED",
                "UNVERIFIED_CONTEXT",
                false,
                0,
                List.of(),
                List.of("REVIEW_REQUIRED"),
                "k-finance-term-rag-v1",
                Instant.parse("2026-07-01T00:00:00Z"));
    }
}
