package com.hana.omnilens.marketnews.domain;

import java.time.Instant;
import java.util.List;

import com.hana.omnilens.alert.domain.AlertGlossaryTerm;
import com.hana.omnilens.alert.domain.AlertSummaryLines;

public record MarketNewsEvent(
        String newsId,
        String query,
        String title,
        String translatedTitle,
        String summary,
        AlertSummaryLines summaryLines,
        String translatedSummary,
        String originalContent,
        String translatedContent,
        List<String> imageUrls,
        String contentAvailability,
        String originalUrl,
        String canonicalUrl,
        String sourceLicensePolicy,
        List<AlertGlossaryTerm> glossaryTerms,
        String sentiment,
        String importance,
        String translationProvider,
        String translationModelVersion,
        String translationStatus,
        String duplicateKey,
        Instant publishedAt,
        Instant createdAt
) {

    public MarketNewsEvent {
        imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
        glossaryTerms = glossaryTerms == null ? List.of() : List.copyOf(glossaryTerms);
        summaryLines = summaryLines == null ? AlertSummaryLines.fromSummary(summary) : summaryLines;
        sentiment = sentiment == null || sentiment.isBlank() ? "NEUTRAL" : sentiment;
        importance = importance == null || importance.isBlank() ? "MEDIUM" : importance;
        translationProvider = translationProvider == null ? "" : translationProvider;
        translationModelVersion = translationModelVersion == null ? "" : translationModelVersion;
        translationStatus = translationStatus == null ? "" : translationStatus;
    }
}
