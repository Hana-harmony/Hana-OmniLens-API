package com.hana.omniconnect.term.application;

import java.time.Instant;

public record KoreanFinancialTermClickLog(
        String clickId,
        Instant occurredAt,
        String term,
        String normalizedTerm,
        String locale,
        String sourceType,
        String articleId,
        String articleUrl,
        String stockCode,
        String stockName,
        String userHash,
        String sessionHash,
        boolean cacheHit,
        String explanationSource,
        String displayMode
) {
}
