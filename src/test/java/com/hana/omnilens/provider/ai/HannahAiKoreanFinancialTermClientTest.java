package com.hana.omnilens.provider.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.provider.ProviderTestResilience;

class HannahAiKoreanFinancialTermClientTest {

    @Test
    void explainUsesInternalAiContractWithoutServiceToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HannahAiKoreanFinancialTermClient client = new HannahAiKoreanFinancialTermClient(
                builder.baseUrl("http://localhost:8000").build(),
                ProviderTestResilience.disabled());

        server.expect(requestTo("http://localhost:8000/api/v1/korean-financial-terms/explain"))
                .andExpect(headerDoesNotExist("X-HANNAH-AI-SERVICE-TOKEN"))
                .andExpect(content().string(containsString("\"term\":\"개미\"")))
                .andExpect(content().string(containsString("\"source_type\":\"NEWS\"")))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "status": 200,
                          "code": "COMMON_000",
                          "message": "OK",
                          "data": {
                            "term": "개미",
                            "normalized_term": "개미",
                            "english_term": "retail investors",
                            "category": "market_participant",
                            "definition": "Individual retail investors in Korean stock-market slang.",
                            "explanation": "In Korean market news, 개미 means retail investors, not ants.",
                            "example": "개미 매수세 means retail investor buying pressure.",
                            "confidence_score": 0.96,
                            "confidence_level": "HIGH",
                            "display_mode": "EXPLANATION",
                            "source": "DICTIONARY",
                            "cacheable": true,
                            "cache_ttl_seconds": 2592000,
                            "evidence": [
                              {
                                "title": "Seed dictionary",
                                "snippet": "Curated Korean financial term glossary.",
                                "url": "",
                                "source_type": "INTERNAL_DICTIONARY"
                              }
                            ],
                            "quality_flags": [],
                            "model_version": "k-finance-term-rag-v1",
                            "generated_at": "2026-07-01T00:00:00Z"
                          },
                          "timestamp": "2026-07-01T00:00:00Z"
                        }
                        """, APPLICATION_JSON));

        HannahAiKoreanFinancialTermExplainResponse response = client.explain(
                new HannahAiKoreanFinancialTermExplainRequest(
                        "개미",
                        "en",
                        "NEWS",
                        "개미 매수세 확대",
                        "개미 투자자들이 순매수했다.",
                        "005930",
                        "삼성전자",
                        "news-1",
                        "https://news.example.com/1"));

        assertThat(response.normalizedTerm()).isEqualTo("개미");
        assertThat(response.displayMode()).isEqualTo("EXPLANATION");
        assertThat(response.evidence()).hasSize(1);
        assertThat(response.modelVersion()).isEqualTo("k-finance-term-rag-v1");
        server.verify();
    }
}
