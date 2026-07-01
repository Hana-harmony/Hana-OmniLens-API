package com.hana.omnilens.term.application;

import java.time.Instant;

import com.hana.omnilens.term.domain.KoreanFinancialTermExplanation;

public record KoreanFinancialTermExplanationCacheEntry(
        String cacheKey,
        String term,
        String normalizedTerm,
        String locale,
        String articleId,
        String stockCode,
        String source,
        String displayMode,
        boolean cacheable,
        Instant expiresAt,
        KoreanFinancialTermExplanation response
) {
}
