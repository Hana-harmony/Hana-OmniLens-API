package com.hana.omnilens.alert.api;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.hana.omnilens.alert.domain.AlertGlossaryTerm;
import com.hana.omnilens.alert.domain.AlertSummaryLines;

public record AlertPublishRequest(
        @NotBlank @Size(max = 80) String partnerId,
        @NotBlank @Pattern(regexp = "\\d{6}") String stockCode,
        @NotBlank @Size(max = 80) String stockName,
        @NotBlank @Pattern(regexp = "NEWS|DISCLOSURE") String sourceType,
        @NotBlank @Size(max = 300) String originalTitle,
        @NotBlank @Size(max = 300) String translatedTitle,
        @NotBlank @Size(max = 1000) String summary,
        AlertSummaryLines summaryLines,
        @Size(max = 2000) String translatedSummary,
        @Size(max = 1000000) String originalContent,
        @Size(max = 1000000) String translatedContent,
        List<@Size(max = 1000) String> imageUrls,
        @Size(max = 40) String contentAvailability,
        @NotBlank @Size(max = 500) String originalUrl,
        @NotNull Instant publishedAt,
        List<String> eventTags,
        @NotBlank @Pattern(regexp = "POSITIVE|NEUTRAL|NEGATIVE") String sentiment,
        @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL") String importance,
        List<String> relatedStocks,
        boolean holderTarget,
        boolean watchlistTarget,
        List<AlertGlossaryTerm> glossaryTerms,
        List<String> translationQualityFlags,
        @Size(max = 80) String translationProvider,
        @Size(max = 120) String translationModelVersion,
        @Size(max = 40) String translationStatus,
        @Size(max = 128) String duplicateKey,
        @Size(max = 128) String clusterKey,
        @Size(max = 120) String modelVersion,
        Double eventConfidence,
        Double sentimentConfidence,
        Double importanceConfidence,
        Double stockMatchConfidence
) {
    public String effectiveContentAvailability() {
        return contentAvailability == null || contentAvailability.isBlank() ? "SUMMARY_ONLY" : contentAvailability;
    }

    public String effectiveTranslationProvider() {
        return translationProvider == null || translationProvider.isBlank()
                ? "source-language-fallback"
                : translationProvider;
    }

    public String effectiveTranslationModelVersion() {
        return translationModelVersion == null ? "" : translationModelVersion;
    }

    public String effectiveTranslationStatus() {
        return translationStatus == null || translationStatus.isBlank() ? "SOURCE_LANGUAGE_FALLBACK" : translationStatus;
    }
}
