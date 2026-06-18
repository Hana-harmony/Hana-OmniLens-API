package com.hana.omnilens.provider.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HannahAiAnalysisResponse(
        @JsonProperty("stock_code") String stockCode,
        @JsonProperty("stock_name") String stockName,
        @JsonProperty("source_type") String sourceType,
        @JsonProperty("original_title") String originalTitle,
        String summary,
        @JsonProperty("event_tags") List<String> eventTags,
        String sentiment,
        String importance,
        @JsonProperty("related_stocks") List<String> relatedStocks,
        @JsonProperty("holder_target") boolean holderTarget,
        @JsonProperty("watchlist_target") boolean watchlistTarget,
        @JsonProperty("duplicate_key") String duplicateKey,
        @JsonProperty("model_version") String modelVersion
) {
}
