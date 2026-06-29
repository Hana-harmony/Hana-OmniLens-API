package com.hana.omnilens.provider.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.ForeignOwnershipModelTrainingProperties;

class HannahAiForeignOwnershipRetrainClientTest {

    @Test
    void retrainSendsRestrictedHistoryWithMaintenanceToken() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HannahAiForeignOwnershipRetrainClient client = new HannahAiForeignOwnershipRetrainClient(
                builder.build(),
                new ForeignOwnershipModelTrainingProperties(
                        true,
                        true,
                        Duration.ofSeconds(2),
                        Duration.ofMinutes(20),
                        "maintenance-secret",
                        29,
                        2_500,
                        50_000,
                        250_000));

        server.expect(requestTo("http://localhost:8000/api/v1/market/foreign-ownership/model/retrain"))
                .andExpect(header("X-HANNAH-AI-MAINTENANCE-TOKEN", "maintenance-secret"))
                .andExpect(content().string(containsString("\"stock_code\":\"015760\"")))
                .andExpect(content().string(containsString("\"restricted_stock_codes\":[\"015760\"]")))
                .andExpect(content().string(containsString("\"minimum_promotable_stock_count\":29")))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "status": 200,
                          "code": "COMMON_000",
                          "message": "OK",
                          "data": {
                            "promoted": true,
                            "release_status": "promoted",
                            "model_reloaded": true,
                            "observation_count": 58784,
                            "stock_count": 32,
                            "sample_count": 21895,
                            "train_date_min": "2019-01-02",
                            "train_date_max": "2026-06-26",
                            "selected_model": "stock_routed_ml_ensemble",
                            "baseline_metrics": {"mae": 53912.99},
                            "guarded_runtime_metrics": {"mae": 51539.19},
                            "guarded_improvement_over_baseline": {"mae": 0.044},
                            "quality_gates": {"status": "pass"},
                            "model_path": "src/hannah_montana_ai/model_store/foreign_ownership_quantity_ml.joblib",
                            "report_path": "reports/foreign-ownership-quantity-training-report.json",
                            "candidate_report_path": null
                          },
                          "timestamp": "2026-06-21T00:00:00Z"
                        }
                        """, APPLICATION_JSON));

        HannahAiForeignOwnershipRetrainResponse response = client.retrain(
                new HannahAiForeignOwnershipRetrainRequest(
                        List.of(new HannahAiForeignOwnershipTrainingPoint(
                                "015760",
                                LocalDate.of(2026, 6, 26),
                                1_000_000L,
                                2_000_000L)),
                        List.of("015760"),
                        29,
                        2_500,
                        50_000,
                        250_000));

        assertThat(response.promoted()).isTrue();
        assertThat(response.releaseStatus()).isEqualTo("promoted");
        assertThat(response.observationCount()).isEqualTo(58_784);
        server.verify();
    }
}
