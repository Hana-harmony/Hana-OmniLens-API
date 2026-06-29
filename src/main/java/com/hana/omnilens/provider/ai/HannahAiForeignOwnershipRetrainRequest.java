package com.hana.omnilens.provider.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HannahAiForeignOwnershipRetrainRequest(
        List<HannahAiForeignOwnershipTrainingPoint> history,
        @JsonProperty("restricted_stock_codes") List<String> restrictedStockCodes,
        @JsonProperty("minimum_promotable_stock_count") int minimumPromotableStockCount,
        @JsonProperty("minimum_promotable_history_days") int minimumPromotableHistoryDays,
        @JsonProperty("minimum_promotable_observations") int minimumPromotableObservations,
        @JsonProperty("max_model_training_samples") int maxModelTrainingSamples
) {
}
