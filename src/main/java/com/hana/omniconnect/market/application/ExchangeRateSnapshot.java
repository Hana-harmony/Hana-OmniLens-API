package com.hana.omniconnect.market.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public record ExchangeRateSnapshot(
        String baseCurrency,
        String localCurrency,
        BigDecimal fxRate,
        Instant updatedAt
) {
    private static final String BASE_CURRENCY = "KRW";

    public ExchangeRateSnapshot {
        Objects.requireNonNull(localCurrency, "localCurrency");
        Objects.requireNonNull(fxRate, "fxRate");
        Objects.requireNonNull(updatedAt, "updatedAt");
        baseCurrency = BASE_CURRENCY;
        localCurrency = localCurrency.toUpperCase(Locale.ROOT);
    }

    static ExchangeRateSnapshot krwToLocal(String localCurrency, BigDecimal fxRate, Instant updatedAt) {
        return new ExchangeRateSnapshot(BASE_CURRENCY, localCurrency, fxRate, updatedAt);
    }
}
