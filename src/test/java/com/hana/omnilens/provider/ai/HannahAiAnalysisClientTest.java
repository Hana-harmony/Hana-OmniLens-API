package com.hana.omnilens.provider.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hana.omnilens.provider.ProviderTestResilience;

class HannahAiAnalysisClientTest {

    @Test
    void rejectsPartialMarketImpactPayloadAtProviderBoundary() {
        ObjectMapper objectMapper = new ObjectMapper();

        assertThatThrownBy(() -> objectMapper.readValue("""
                {
                  "market_impact_importance": "HIGH",
                  "market_impact_score": 0.8
                }
                """, HannahAiAnalysisResponse.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("market impact fields must be all present or all absent");
    }

    @Test
    void analyzeUsesInternalAiContractWithoutServiceToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HannahAiAnalysisClient client = new HannahAiAnalysisClient(
                builder.baseUrl("http://localhost:8000").build(),
                ProviderTestResilience.disabled());

        server.expect(requestTo("http://localhost:8000/api/v1/alerts/analyze"))
                .andExpect(headerDoesNotExist("X-HANNAH-AI-SERVICE-TOKEN"))
                .andExpect(content().string(containsString("\"source_type\":\"NEWS\"")))
                .andExpect(content().string(containsString("\"stock_universe\"")))
                .andExpect(content().string(containsString("\"stock_code\":\"005930\"")))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "status": 200,
                          "code": "COMMON_000",
                          "message": "OK",
                          "data": {
                            "stock_code": "005930",
                            "stock_name": "삼성전자",
                            "source_type": "NEWS",
                            "original_title": "삼성전자 실적 개선",
                            "summary": "반도체 회복으로 실적 개선 기대",
                            "event_tags": ["EARNINGS"],
                            "sentiment": "POSITIVE",
                            "importance": "HIGH",
                            "market_impact_importance": "MEDIUM",
                            "market_impact_score": 0.42,
                            "market_impact_confidence": 0.81,
                            "related_stocks": ["005930"],
                            "holder_target": true,
                            "watchlist_target": true,
                            "glossary_terms": [
                              {
                                "source_term": "실적",
                                "normalized_term": "실적",
                                "english_term": "earnings",
                                "category": "event"
                              }
                            ],
                            "translation_quality_flags": ["FINANCIAL_GLOSSARY_APPLIED"],
                            "duplicate_key": "duplicate-key",
                            "model_version": "financial-ml-v1|sentiment:kf-deberta-finance-sentiment-lora-v1|impact:k-fnspid-impact-v2",
                            "event_confidence": 0.91,
                            "sentiment_confidence": 0.89,
                            "importance_confidence": 0.93,
                            "stock_match_confidence": 1.0
                          },
                          "timestamp": "2026-06-20T00:00:00Z"
                        }
                        """, APPLICATION_OCTET_STREAM));

        HannahAiAnalysisResponse response = client.analyze(new HannahAiAnalysisRequest(
                "NEWS",
                "삼성전자 실적 개선",
                "반도체 회복으로 실적 개선 기대",
                "https://example.com/news/1",
                List.of(new HannahAiStockCandidate(
                        "005930",
                        "삼성전자",
                        "Samsung Electronics",
                        List.of("Samsung Elec")))));

        assertThat(response.stockCode()).isEqualTo("005930");
        assertThat(response.eventTags()).containsExactly("EARNINGS");
        assertThat(response.sentiment()).isEqualTo("POSITIVE");
        assertThat(response.marketImpactImportance()).isEqualTo("MEDIUM");
        assertThat(response.marketImpactScore()).isEqualTo(0.42);
        assertThat(response.marketImpactConfidence()).isEqualTo(0.81);
        assertThat(response.holderTarget()).isTrue();
        assertThat(response.glossaryTerms()).hasSize(1);
        assertThat(response.glossaryTerms().get(0).englishTerm()).isEqualTo("earnings");
        assertThat(response.translationQualityFlags()).containsExactly("FINANCIAL_GLOSSARY_APPLIED");
        assertThat(response.modelVersion()).isEqualTo(
                "financial-ml-v1|sentiment:kf-deberta-finance-sentiment-lora-v1|impact:k-fnspid-impact-v2");
        assertThat(response.eventConfidence()).isEqualTo(0.91);
        assertThat(response.stockMatchConfidence()).isEqualTo(1.0);
        server.verify();
    }
}
