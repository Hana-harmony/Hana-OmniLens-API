package com.hana.omnilens.alert.domain;

import java.time.Instant;
import java.util.List;

public record AlertEvent(
        String alertId,
        String partnerId,
        String stockCode,
        String stockName,
        String sourceType,
        String originalTitle,
        String translatedTitle,
        String summary,
        String originalUrl,
        Instant publishedAt,
        List<String> eventTags,
        String sentiment,
        String importance,
        List<String> relatedStocks,
        boolean holderTarget,
        boolean watchlistTarget,
        List<AlertGlossaryTerm> glossaryTerms,
        List<String> translationQualityFlags,
        String duplicateKey,
        String modelVersion,
        Instant createdAt
) {
}
