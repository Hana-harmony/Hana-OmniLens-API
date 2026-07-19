package com.hana.omniconnect.market.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.hana.omniconnect.config.ExchangeRateRefreshProperties;

class ExchangeRateRefreshSchedulerTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-04T01:00:00Z");
    private final ExchangeRateProviderRefreshService refreshService = mock(ExchangeRateProviderRefreshService.class);

    @Test
    void skipsRefreshWhenSchedulerIsDisabled() {
        ExchangeRateRefreshScheduler scheduler = new ExchangeRateRefreshScheduler(
                refreshService,
                new ExchangeRateRefreshProperties(false, 60_000L, 0, List.of("USD")),
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

        scheduler.refreshConfiguredCurrencies();

        verify(refreshService, never()).refresh("USD", LocalDate.of(2026, 6, 4));
    }

    @Test
    void refreshesConfiguredCurrenciesWithConfiguredBaseDateOffset() {
        ExchangeRateRefreshScheduler scheduler = new ExchangeRateRefreshScheduler(
                refreshService,
                new ExchangeRateRefreshProperties(true, 60_000L, 1, List.of("usd", "jpy")),
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

        scheduler.refreshConfiguredCurrencies();

        verify(refreshService).refresh("USD", LocalDate.of(2026, 6, 3));
        verify(refreshService).refresh("JPY", LocalDate.of(2026, 6, 3));
    }

    @Test
    void continuesAfterCurrencyRefreshFailure() {
        when(refreshService.refresh("USD", LocalDate.of(2026, 6, 4)))
                .thenThrow(new IllegalStateException("exchange provider unavailable"));
        when(refreshService.refresh("JPY", LocalDate.of(2026, 6, 4)))
                .thenReturn(Optional.of(new ExchangeRateSnapshot(
                        "KRW",
                        "JPY",
                        new BigDecimal("0.108"),
                        FIXED_NOW)));
        ExchangeRateRefreshScheduler scheduler = new ExchangeRateRefreshScheduler(
                refreshService,
                new ExchangeRateRefreshProperties(true, 60_000L, 0, List.of("USD", "JPY")),
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

        scheduler.refreshConfiguredCurrencies();

        verify(refreshService, times(1)).refresh("USD", LocalDate.of(2026, 6, 4));
        verify(refreshService, times(1)).refresh("JPY", LocalDate.of(2026, 6, 4));
    }
}
