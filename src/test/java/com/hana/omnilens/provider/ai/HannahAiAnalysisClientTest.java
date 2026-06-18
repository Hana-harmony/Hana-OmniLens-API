package com.hana.omnilens.provider.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.HannahAiProperties;
import com.hana.omnilens.provider.ProviderTestResilience;

class HannahAiAnalysisClientTest {

    @Test
    void analyzeUsesInternalAiContractWithoutServiceToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HannahAiAnalysisClient client = new HannahAiAnalysisClient(
                builder,
                new HannahAiProperties(URI.create("http://localhost:8000")),
                ProviderTestResilience.disabled());

        server.expect(requestTo("http://localhost:8000/api/v1/alerts/analyze"))
                .andExpect(headerDoesNotExist("X-HANNAH-AI-SERVICE-TOKEN"))
                .andExpect(content().string(containsString("\"source_type\":\"NEWS\"")))
                .andExpect(content().string(containsString("\"stock_universe\"")))
                .andExpect(content().string(containsString("\"stock_code\":\"005930\"")))
                .andRespond(withSuccess("""
                        {
                          "stock_code": "005930",
                          "stock_name": "삼성전자",
                          "source_type": "NEWS",
                          "original_title": "삼성전자 실적 개선",
                          "summary": "반도체 회복으로 실적 개선 기대",
                          "event_tags": ["EARNINGS"],
                          "sentiment": "POSITIVE",
                          "importance": "HIGH",
                          "related_stocks": ["005930"],
                          "holder_target": true,
                          "watchlist_target": true,
                          "duplicate_key": "duplicate-key",
                          "model_version": "financial-keyword-baseline-2026-06-04"
                        }
                        """, APPLICATION_JSON));

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
        assertThat(response.holderTarget()).isTrue();
        assertThat(response.modelVersion()).isEqualTo("financial-keyword-baseline-2026-06-04");
        server.verify();
    }
}
