package com.hana.omniconnect.term.domain;

import java.time.Instant;

public record KoreanFinancialTermClickPoint(
        Instant periodStart,
        long clickCount
) {
}
