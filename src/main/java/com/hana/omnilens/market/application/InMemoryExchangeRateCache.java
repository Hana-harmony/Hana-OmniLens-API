package com.hana.omnilens.market.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
public class InMemoryExchangeRateCache implements ExchangeRateCache {

    private final Map<String, ExchangeRateSnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public Optional<ExchangeRateSnapshot> find(String localCurrency) {
        return Optional.ofNullable(snapshots.get(localCurrency.toUpperCase(Locale.ROOT)));
    }

    @Override
    public ExchangeRateSnapshot put(String localCurrency, BigDecimal fxRate, Instant updatedAt) {
        ExchangeRateSnapshot snapshot = ExchangeRateSnapshot.krwToLocal(localCurrency, fxRate, updatedAt);
        snapshots.put(snapshot.localCurrency(), snapshot);
        return snapshot;
    }
}
