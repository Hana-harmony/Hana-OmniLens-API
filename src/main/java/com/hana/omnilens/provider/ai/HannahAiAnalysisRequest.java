package com.hana.omnilens.provider.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HannahAiAnalysisRequest(
        @JsonProperty("source_type") String sourceType,
        String title,
        String snippet,
        @JsonProperty("original_url") String originalUrl,
        @JsonProperty("stock_universe") List<HannahAiStockCandidate> stockUniverse
) {
}
