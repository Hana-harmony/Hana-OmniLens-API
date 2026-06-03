package com.hana.omnilens.market.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface ExchangeRateCache {

    Optional<ExchangeRateSnapshot> find(String localCurrency);

    ExchangeRateSnapshot put(String localCurrency, BigDecimal fxRate, Instant updatedAt);
}
