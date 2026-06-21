package com.hana.omnilens.provider.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.HannahAiProperties;
import com.hana.omnilens.provider.ProviderTestResilience;

class HannahAiForeignOwnershipPredictionClientTest {

    @Test
    void predictUsesInternalAiContractWithoutServiceToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HannahAiForeignOwnershipPredictionClient client = new HannahAiForeignOwnershipPredictionClient(
                builder,
                new HannahAiProperties(URI.create("http://localhost:8000")),
                ProviderTestResilience.disabled());

        server.expect(requestTo("http://localhost:8000/api/v1/market/foreign-ownership/predict"))
                .andExpect(headerDoesNotExist("X-HANNAH-AI-SERVICE-TOKEN"))
                .andExpect(content().string(containsString("\"stock_code\":\"005930\"")))
                .andExpect(content().string(containsString("\"foreign_limit_exhaustion_rate\":99.5000")))
                .andExpect(content().string(containsString("\"history\"")))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "status": 200,
                          "code": "COMMON_000",
                          "message": "OK",
                          "data": {
                            "stock_code": "005930",
                            "min_foreign_limit_exhaustion_rate": 99.475,
                            "base_foreign_limit_exhaustion_rate": 99.975,
                            "max_foreign_limit_exhaustion_rate": 100.475,
                            "order_impact_rate": 0.100000,
                            "intraday_uncertainty_rate": 0.500000,
                            "observed_intraday_volume": 0,
                            "trend_daily_change_rate": 0.375000,
                            "history_observation_count": 5,
                            "history_window_days": 4,
                            "base_date": "2025-06-04",
                            "calculated_at": "2025-06-04T00:00:00Z",
                            "confidence_level": "AI_TIME_SERIES_ADJUSTED",
                            "confidence_score": 0.8000,
                            "model_version": "hannah-foreign-ownership-timeseries-v1",
                            "source": "HANNAH_MONTANA_AI_FOREIGN_OWNERSHIP+DAILY_TIMESERIES"
                          },
                          "timestamp": "2026-06-21T00:00:00Z"
                        }
                        """, APPLICATION_JSON));

        HannahAiForeignOwnershipPredictionResponse response = client.predict(
                new HannahAiForeignOwnershipPredictionRequest(
                        "005930",
                        "BUY",
                        1,
                        995L,
                        new BigDecimal("49.7500"),
                        1_000L,
                        new BigDecimal("99.5000"),
                        LocalDate.of(2025, 6, 4),
                        0L,
                        List.of(new HannahAiForeignOwnershipHistoryPoint(
                                LocalDate.of(2025, 6, 4),
                                995L,
                                new BigDecimal("49.7500"),
                                1_000L,
                                new BigDecimal("99.5000")))));

        assertThat(response.stockCode()).isEqualTo("005930");
        assertThat(response.maxForeignLimitExhaustionRate()).isEqualByComparingTo("100.475");
        assertThat(response.confidenceLevel()).isEqualTo("AI_TIME_SERIES_ADJUSTED");
        assertThat(response.modelVersion()).isEqualTo("hannah-foreign-ownership-timeseries-v1");
        server.verify();
    }
}
