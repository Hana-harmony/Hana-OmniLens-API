package com.hana.omniconnect.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.hana.omniconnect.provider.market.ExchangeRateProviderClient;
import com.hana.omniconnect.provider.market.ProviderExchangeRateSnapshot;

class ExchangeRateProviderRefreshServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-04T01:00:00Z");

    @Test
    void refreshStoresProviderRateInExchangeRateCache() {
        ExchangeRateProviderClient client = mock(ExchangeRateProviderClient.class);
        InMemoryExchangeRateCache cache = new InMemoryExchangeRateCache();
        ExchangeRateProviderRefreshService service = new ExchangeRateProviderRefreshService(
                client,
                cache,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

        when(client.findKrwToLocalRate("usd", LocalDate.of(2025, 6, 4)))
                .thenReturn(Optional.of(new ProviderExchangeRateSnapshot(
                        "USD",
                        new BigDecimal("0.0007407407407407407"),
                        LocalDate.of(2025, 6, 4),
                        FIXED_NOW,
                        "FRANKFURTER_DAILY")));

        Optional<ExchangeRateSnapshot> snapshot = service.refresh("usd", LocalDate.of(2025, 6, 4));

        assertThat(snapshot).isPresent();
        assertThat(snapshot.orElseThrow().baseCurrency()).isEqualTo("KRW");
        assertThat(snapshot.orElseThrow().localCurrency()).isEqualTo("USD");
        assertThat(snapshot.orElseThrow().fxRate()).isEqualByComparingTo("0.0007407407407407407");
        assertThat(snapshot.orElseThrow().updatedAt()).isEqualTo(FIXED_NOW);
        assertThat(cache.find("USD")).isPresent();
    }

    @Test
    void refreshDoesNotMutateCacheWhenProviderHasNoRate() {
        ExchangeRateProviderClient client = mock(ExchangeRateProviderClient.class);
        InMemoryExchangeRateCache cache = new InMemoryExchangeRateCache();
        ExchangeRateProviderRefreshService service = new ExchangeRateProviderRefreshService(
                client,
                cache,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

        when(client.findKrwToLocalRate("USD", LocalDate.of(2025, 6, 4))).thenReturn(Optional.empty());

        Optional<ExchangeRateSnapshot> snapshot = service.refresh("USD", LocalDate.of(2025, 6, 4));

        assertThat(snapshot).isEmpty();
        assertThat(cache.find("USD")).isEmpty();
    }
}
