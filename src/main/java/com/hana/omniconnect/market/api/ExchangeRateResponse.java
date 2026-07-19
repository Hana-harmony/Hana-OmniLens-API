package com.hana.omniconnect.market.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.hana.omniconnect.market.application.ExchangeRateSnapshot;

public record ExchangeRateResponse(
        String baseCurrency,
        String localCurrency,
        BigDecimal fxRate,
        Instant updatedAt
) {
    static ExchangeRateResponse from(ExchangeRateSnapshot snapshot) {
        return new ExchangeRateResponse(
                snapshot.baseCurrency(),
                snapshot.localCurrency(),
                snapshot.fxRate(),
                snapshot.updatedAt());
    }
}
