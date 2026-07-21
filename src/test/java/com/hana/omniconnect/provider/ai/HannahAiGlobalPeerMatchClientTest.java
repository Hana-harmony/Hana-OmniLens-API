package com.hana.omniconnect.provider.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hana.omniconnect.provider.ProviderTestResilience;

class HannahAiGlobalPeerMatchClientTest {

    @Test
    void matchMapsSnakeCaseComparisonAndKeyStrengthContract() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HannahAiGlobalPeerMatchClient client = new HannahAiGlobalPeerMatchClient(
                builder.baseUrl("http://localhost:8000").build(),
                ProviderTestResilience.disabled());

        server.expect(requestTo("http://localhost:8000/api/v1/market/global-peers/match"))
                .andExpect(content().string(containsString("\"stock_code\":\"005930\"")))
                .andRespond(withSuccess(responseBody(), APPLICATION_JSON));

        HannahAiGlobalPeerMatchResponse response = client.match(new HannahAiGlobalPeerMatchRequest(
                "005930",
                "삼성전자",
                "Samsung Electronics",
                "KOSPI",
                List.of(),
                "",
                5));

        assertThat(response.peers()).hasSize(1);
        assertThat(response.comparisons()).hasSize(1);
        assertThat(response.comparisons().get(0).dimension()).isEqualTo("semiconductor");
        assertThat(response.comparisons().get(0).peer().ticker()).isEqualTo("MU");
        assertThat(response.keyStrengths()).hasSize(4);
        assertThat(response.keyStrengths().get(0).iconKey()).isEqualTo("memory");
        server.verify();
    }

    @Test
    void comparisonRejectsDimensionOutsideAllowlist() {
        assertThatThrownBy(() -> new HannahAiGlobalPeerComparison(
                "arbitrary_dimension",
                "Description",
                peer()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dimension is not allowed");
    }

    @Test
    void keyStrengthRejectsIconOutsideAllowlist() {
        assertThatThrownBy(() -> new HannahAiGlobalPeerKeyStrength(
                "Strength",
                "Description",
                "remote_image_url"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("iconKey is not allowed");
    }

    @Test
    void responseRejectsMissingComparisonCards() {
        assertThatThrownBy(() -> new HannahAiGlobalPeerMatchResponse(
                "005930",
                "삼성전자",
                "Samsung Electronics",
                "Information Technology",
                "Semiconductors",
                "Memory semiconductor manufacturing",
                List.of("memory"),
                "Headline",
                "Summary",
                peer(),
                List.of(peer()),
                List.of(),
                strengths(),
                BigDecimal.ONE,
                "HIGH",
                "model-v1",
                "HANNAH_GLOBAL_PEER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("comparisons size");
    }

    @Test
    void responseRejectsPeerOutsideSourceDomain() {
        assertThatThrownBy(() -> new HannahAiGlobalPeerMatchResponse(
                "086790",
                "하나금융지주",
                "Hana Financial Group",
                "Financials",
                "Banks",
                "Diversified banking group",
                List.of("banking", "financials"),
                "Headline",
                "Summary",
                peer(),
                List.of(peer()),
                List.of(new HannahAiGlobalPeerComparison(
                        "financial_services",
                        "Description",
                        peer())),
                strengths(),
                BigDecimal.ONE,
                "HIGH",
                "model-v1",
                "HANNAH_GLOBAL_PEER_HYBRID_RANKER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("peer sector");
    }

    private HannahAiGlobalPeerMatch peer() {
        return new HannahAiGlobalPeerMatch(
                1,
                "MU",
                "Micron Technology",
                "NASDAQ_GLOBAL_SELECT",
                "US",
                new BigDecimal("0.91"),
                List.of("memory"),
                "Information Technology",
                "Semiconductors",
                "Memory semiconductor manufacturer",
                "LARGE_CAP",
                2025,
                null,
                null,
                null,
                null,
                "SEC_COMPANYFACTS",
                new BigDecimal("0.9"),
                List.of("Memory portfolio"),
                "Comparable memory exposure");
    }

    private List<HannahAiGlobalPeerKeyStrength> strengths() {
        return List.of(
                new HannahAiGlobalPeerKeyStrength("Memory", "Memory leadership", "memory"),
                new HannahAiGlobalPeerKeyStrength("Foundry", "Foundry scale", "foundry"),
                new HannahAiGlobalPeerKeyStrength("AI", "AI demand exposure", "ai"),
                new HannahAiGlobalPeerKeyStrength("Ecosystem", "Device ecosystem", "ecosystem"));
    }

    private String responseBody() {
        return """
                {
                  "success": true,
                  "status": 200,
                  "code": "COMMON_000",
                  "message": "OK",
                  "data": {
                    "stock_code": "005930",
                    "stock_name": "삼성전자",
                    "stock_name_en": "Samsung Electronics",
                    "source_sector": "Information Technology",
                    "source_industry": "Semiconductors",
                    "source_business_model": "Memory semiconductor manufacturing",
                    "source_business_tags": ["memory"],
                    "headline": "Samsung Electronics global comparison",
                    "summary": "A diversified semiconductor and electronics leader.",
                    "primary_peer": {
                      "rank": 1,
                      "ticker": "MU",
                      "company_name": "Micron Technology",
                      "exchange": "NASDAQ_GLOBAL_SELECT",
                      "country": "US",
                      "similarity_score": 0.91,
                      "business_tags": ["memory"],
                      "sector": "Information Technology",
                      "industry": "Semiconductors",
                      "matched_factors": ["Memory portfolio"]
                    },
                    "peers": [{
                      "rank": 1,
                      "ticker": "MU",
                      "company_name": "Micron Technology",
                      "exchange": "NASDAQ_GLOBAL_SELECT",
                      "country": "US",
                      "similarity_score": 0.91,
                      "business_tags": ["memory"],
                      "sector": "Information Technology",
                      "industry": "Semiconductors",
                      "matched_factors": ["Memory portfolio"]
                    }],
                    "comparisons": [{
                      "dimension": "semiconductor",
                      "description": "Micron is a recognizable memory semiconductor peer.",
                      "peer": {
                        "rank": 1,
                        "ticker": "MU",
                        "company_name": "Micron Technology",
                        "exchange": "NASDAQ_GLOBAL_SELECT",
                        "country": "US",
                        "similarity_score": 0.91,
                        "business_tags": ["memory"],
                        "sector": "Information Technology",
                        "industry": "Semiconductors",
                        "matched_factors": ["Memory portfolio"]
                      }
                    }],
                    "key_strengths": [
                      {"title": "Memory", "description": "Memory leadership", "icon_key": "memory"},
                      {"title": "Foundry", "description": "Foundry scale", "icon_key": "foundry"},
                      {"title": "AI", "description": "AI demand exposure", "icon_key": "ai"},
                      {"title": "Ecosystem", "description": "Device ecosystem", "icon_key": "ecosystem"}
                    ],
                    "confidence_score": 0.91,
                    "confidence_level": "HIGH",
                    "model_version": "global-peer-v2",
                    "source": "HANNAH_GLOBAL_PEER"
                  }
                }
                """;
    }
}
