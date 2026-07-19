package com.hana.omniconnect.provider.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hana.omniconnect.alert.domain.AlertSummaryLines;

public record HannahAiAnalysisResponse(
        @JsonProperty("stock_code") String stockCode,
        @JsonProperty("stock_name") String stockName,
        @JsonProperty("source_type") String sourceType,
        @JsonProperty("original_title") String originalTitle,
        @JsonProperty("translated_title") String translatedTitle,
        String summary,
        @JsonProperty("summary_lines") AlertSummaryLines summaryLines,
        @JsonProperty("translated_summary") String translatedSummary,
        @JsonProperty("content_availability") String contentAvailability,
        @JsonProperty("original_content") String originalContent,
        @JsonProperty("translated_content") String translatedContent,
        @JsonProperty("image_urls") List<String> imageUrls,
        @JsonProperty("event_tags") List<String> eventTags,
        String sentiment,
        String importance,
        @JsonProperty("market_impact_importance") String marketImpactImportance,
        @JsonProperty("market_impact_score") Double marketImpactScore,
        @JsonProperty("market_impact_confidence") Double marketImpactConfidence,
        @JsonProperty("related_stocks") List<String> relatedStocks,
        @JsonProperty("holder_target") boolean holderTarget,
        @JsonProperty("watchlist_target") boolean watchlistTarget,
        @JsonProperty("glossary_terms") List<HannahAiGlossaryTerm> glossaryTerms,
        @JsonProperty("translation_quality_flags") List<String> translationQualityFlags,
        @JsonProperty("translation_provider") String translationProvider,
        @JsonProperty("translation_model_version") String translationModelVersion,
        @JsonProperty("translation_status") String translationStatus,
        @JsonProperty("duplicate_key") String duplicateKey,
        @JsonProperty("cluster_key") String clusterKey,
        @JsonProperty("model_version") String modelVersion,
        @JsonProperty("event_confidence") Double eventConfidence,
        @JsonProperty("sentiment_confidence") Double sentimentConfidence,
        @JsonProperty("importance_confidence") Double importanceConfidence,
        @JsonProperty("stock_match_confidence") Double stockMatchConfidence
) {
    public HannahAiAnalysisResponse {
        int marketImpactFieldCount = 0;
        marketImpactFieldCount += marketImpactImportance == null ? 0 : 1;
        marketImpactFieldCount += marketImpactScore == null ? 0 : 1;
        marketImpactFieldCount += marketImpactConfidence == null ? 0 : 1;
        if (marketImpactFieldCount != 0 && marketImpactFieldCount != 3) {
            throw new IllegalArgumentException("market impact fields must be all present or all absent");
        }
        if (marketImpactImportance != null
                && !List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(marketImpactImportance)) {
            throw new IllegalArgumentException("market impact importance is invalid");
        }
        if (!isUnitInterval(marketImpactScore) || !isUnitInterval(marketImpactConfidence)) {
            throw new IllegalArgumentException("market impact score and confidence must be between 0 and 1");
        }
    }

    private static boolean isUnitInterval(Double value) {
        return value == null || (Double.isFinite(value) && value >= 0.0 && value <= 1.0);
    }

    public HannahAiAnalysisResponse(
            String stockCode,
            String stockName,
            String sourceType,
            String originalTitle,
            String summary,
            AlertSummaryLines summaryLines,
            String contentAvailability,
            String originalContent,
            List<String> imageUrls,
            List<String> eventTags,
            String sentiment,
            String importance,
            List<String> relatedStocks,
            boolean holderTarget,
            boolean watchlistTarget,
            List<HannahAiGlossaryTerm> glossaryTerms,
            List<String> translationQualityFlags,
            String duplicateKey,
            String clusterKey,
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
                "",
                summary,
                summaryLines,
                "",
                contentAvailability,
                originalContent,
                "",
                imageUrls,
                eventTags,
                sentiment,
                importance,
                null,
                null,
                null,
                relatedStocks,
                holderTarget,
                watchlistTarget,
                glossaryTerms,
                translationQualityFlags,
                "",
                "",
                "",
                duplicateKey,
                clusterKey,
                modelVersion,
                eventConfidence,
                sentimentConfidence,
                importanceConfidence,
                stockMatchConfidence);
    }

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
                "",
                summary,
                AlertSummaryLines.fromSummary(summary),
                "",
                "SUMMARY_ONLY",
                "",
                "",
                List.of(),
                eventTags,
                sentiment,
                importance,
                null,
                null,
                null,
                relatedStocks,
                holderTarget,
                watchlistTarget,
                glossaryTerms,
                translationQualityFlags,
                "",
                "",
                "",
                duplicateKey,
                duplicateKey,
                modelVersion,
                eventConfidence,
                sentimentConfidence,
                importanceConfidence,
                stockMatchConfidence);
    }
}
