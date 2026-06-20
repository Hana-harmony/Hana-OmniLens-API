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
        @JsonProperty("glossary_terms") List<HannahAiGlossaryTerm> glossaryTerms,
        @JsonProperty("translation_quality_flags") List<String> translationQualityFlags,
        @JsonProperty("duplicate_key") String duplicateKey,
        @JsonProperty("model_version") String modelVersion,
        @JsonProperty("event_confidence") Double eventConfidence,
        @JsonProperty("sentiment_confidence") Double sentimentConfidence,
        @JsonProperty("importance_confidence") Double importanceConfidence,
        @JsonProperty("stock_match_confidence") Double stockMatchConfidence,
        @JsonProperty("review_required") Boolean reviewRequired,
        @JsonProperty("review_reasons") List<String> reviewReasons
) {
}
