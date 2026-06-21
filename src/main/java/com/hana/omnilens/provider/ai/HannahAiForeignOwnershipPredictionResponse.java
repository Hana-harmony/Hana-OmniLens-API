package com.hana.omnilens.provider.ai;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HannahAiForeignOwnershipPredictionResponse(
        @JsonProperty("stock_code") String stockCode,
        @JsonProperty("min_foreign_limit_exhaustion_rate") BigDecimal minForeignLimitExhaustionRate,
        @JsonProperty("base_foreign_limit_exhaustion_rate") BigDecimal baseForeignLimitExhaustionRate,
        @JsonProperty("max_foreign_limit_exhaustion_rate") BigDecimal maxForeignLimitExhaustionRate,
        @JsonProperty("order_impact_rate") BigDecimal orderImpactRate,
        @JsonProperty("intraday_uncertainty_rate") BigDecimal intradayUncertaintyRate,
        @JsonProperty("observed_intraday_volume") long observedIntradayVolume,
        @JsonProperty("trend_daily_change_rate") BigDecimal trendDailyChangeRate,
        @JsonProperty("history_observation_count") int historyObservationCount,
        @JsonProperty("history_window_days") int historyWindowDays,
        @JsonProperty("base_date") LocalDate baseDate,
        @JsonProperty("calculated_at") Instant calculatedAt,
        @JsonProperty("confidence_level") String confidenceLevel,
        @JsonProperty("confidence_score") BigDecimal confidenceScore,
        @JsonProperty("model_version") String modelVersion,
        String source
) {
}
