package com.hana.omnilens.provider.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hana.omnilens.alert.domain.AlertSummaryLines;

public record HannahAiAnalysisResponse(
        @JsonProperty("stock_code") String stockCode,
        @JsonProperty("stock_name") String stockName,
        @JsonProperty("source_type") String sourceType,
        @JsonProperty("original_title") String originalTitle,
        String summary,
        @JsonProperty("summary_lines") AlertSummaryLines summaryLines,
        @JsonProperty("content_availability") String contentAvailability,
        @JsonProperty("original_content") String originalContent,
        @JsonProperty("image_urls") List<String> imageUrls,
        @JsonProperty("event_tags") List<String> eventTags,
        String sentiment,
        String importance,
        @JsonProperty("related_stocks") List<String> relatedStocks,
        @JsonProperty("holder_target") boolean holderTarget,
        @JsonProperty("watchlist_target") boolean watchlistTarget,
        @JsonProperty("glossary_terms") List<HannahAiGlossaryTerm> glossaryTerms,
        @JsonProperty("translation_quality_flags") List<String> translationQualityFlags,
        @JsonProperty("duplicate_key") String duplicateKey,
        @JsonProperty("cluster_key") String clusterKey,
        @JsonProperty("model_version") String modelVersion,
        @JsonProperty("event_confidence") Double eventConfidence,
        @JsonProperty("sentiment_confidence") Double sentimentConfidence,
        @JsonProperty("importance_confidence") Double importanceConfidence,
        @JsonProperty("stock_match_confidence") Double stockMatchConfidence
) {
    public HannahAiAnalysisResponse(
            String stockCode,
            String stockName,
            String sourceType,
            String originalTitle,
            String summary,
            List<String> eventTags,
            String sentiment,
            String importance,
            List<String> relatedStocks,
            boolean holderTarget,
            boolean watchlistTarget,
            List<HannahAiGlossaryTerm> glossaryTerms,
            List<String> translationQualityFlags,
            String duplicateKey,
            String modelVersion,
            Double eventConfidence,
            Double sentimentConfidence,
            Double importanceConfidence,
            Double stockMatchConfidence) {
        this(
                stockCode,
                stockName,
                sourceType,
                originalTitle,
                summary,
                AlertSummaryLines.fromSummary(summary),
                "SUMMARY_ONLY",
                "",
                List.of(),
                eventTags,
                sentiment,
                importance,
                relatedStocks,
                holderTarget,
                watchlistTarget,
                glossaryTerms,
                translationQualityFlags,
                duplicateKey,
                duplicateKey,
                modelVersion,
                eventConfidence,
                sentimentConfidence,
                importanceConfidence,
                stockMatchConfidence);
    }
}
