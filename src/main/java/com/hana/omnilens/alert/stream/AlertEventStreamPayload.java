package com.hana.omnilens.alert.stream;

import java.time.Instant;
import java.util.List;

import com.hana.omnilens.alert.domain.AlertGlossaryTerm;
import com.hana.omnilens.alert.domain.AlertSummaryLines;

public record AlertEventStreamPayload(
        String eventId,
        String idempotencyKey,
        String sourceType,
        String title,
        String summary,
        AlertSummaryLines summaryLines,
        String translatedSummary,
        String originalContent,
        String translatedContent,
        List<String> imageUrls,
        String contentAvailability,
        String originalUrl,
        String stockCode,
        List<String> relatedStocks,
        List<AlertGlossaryTerm> glossaryTerms,
        List<String> translationQualityFlags,
        String translationProvider,
        String translationModelVersion,
        String translationStatus,
        String clusterKey,
        String sentiment,
        String importance,
        String marketImpactImportance,
        Double marketImpactScore,
        Double marketImpactConfidence,
        String modelVersion,
        Double eventConfidence,
        Double sentimentConfidence,
        Double importanceConfidence,
        Double stockMatchConfidence,
        String riskLevel,
        boolean watchlistTarget,
        boolean holderTarget,
        Instant publishedAt
) {
}
