package com.hana.omniconnect.term.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record KoreanFinancialTermExplanation(
        String term,
        String normalizedTerm,
        String englishTerm,
        String category,
        String definition,
        String explanation,
        String example,
        BigDecimal confidenceScore,
        String confidenceLevel,
        String displayMode,
        String source,
        boolean cacheable,
        int cacheTtlSeconds,
        List<FinancialTermEvidence> evidence,
        List<String> qualityFlags,
        String modelVersion,
        Instant generatedAt,
        boolean cacheHit,
        long clickCount
) {
    public KoreanFinancialTermExplanation {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        qualityFlags = qualityFlags == null ? List.of() : List.copyOf(qualityFlags);
    }

    public KoreanFinancialTermExplanation withAnalytics(boolean cacheHit, long clickCount) {
        return new KoreanFinancialTermExplanation(
                term,
                normalizedTerm,
                englishTerm,
                category,
                definition,
                explanation,
                example,
                confidenceScore,
                confidenceLevel,
                displayMode,
                source,
                cacheable,
                cacheTtlSeconds,
                evidence,
                qualityFlags,
                modelVersion,
                generatedAt,
                cacheHit,
                clickCount);
    }
}
