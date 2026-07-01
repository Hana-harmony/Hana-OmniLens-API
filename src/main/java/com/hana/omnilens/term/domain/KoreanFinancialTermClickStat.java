package com.hana.omnilens.term.domain;

import java.time.Instant;

public record KoreanFinancialTermClickStat(
        String normalizedTerm,
        String locale,
        long clickCount,
        long cacheHitCount,
        long reviewRequiredCount,
        Instant firstClickedAt,
        Instant lastClickedAt
) {
}
