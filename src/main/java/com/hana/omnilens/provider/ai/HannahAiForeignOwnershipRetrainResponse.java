package com.hana.omnilens.provider.ai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HannahAiForeignOwnershipRetrainResponse(
        boolean promoted,
        @JsonProperty("release_status") String releaseStatus,
        @JsonProperty("model_reloaded") boolean modelReloaded,
        @JsonProperty("observation_count") int observationCount,
        @JsonProperty("stock_count") int stockCount,
        @JsonProperty("sample_count") int sampleCount,
        @JsonProperty("train_date_min") LocalDate trainDateMin,
        @JsonProperty("train_date_max") LocalDate trainDateMax,
        @JsonProperty("selected_model") String selectedModel,
        @JsonProperty("baseline_metrics") Map<String, BigDecimal> baselineMetrics,
        @JsonProperty("guarded_runtime_metrics") Map<String, BigDecimal> guardedRuntimeMetrics,
        @JsonProperty("guarded_improvement_over_baseline") Map<String, BigDecimal> guardedImprovementOverBaseline,
        @JsonProperty("quality_gates") Map<String, Object> qualityGates,
        @JsonProperty("model_path") String modelPath,
        @JsonProperty("report_path") String reportPath,
        @JsonProperty("candidate_report_path") String candidateReportPath
) {
}
