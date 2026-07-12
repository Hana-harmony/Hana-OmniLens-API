package com.hana.omnilens.term.domain;

import java.time.Instant;

public record KoreanFinancialTermClickPoint(
        Instant periodStart,
        long clickCount
) {
}
