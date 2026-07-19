package com.hana.omniconnect.market.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hana.omniconnect.provider.market.ExchangeRateProviderClient;
import com.hana.omniconnect.provider.market.ProviderExchangeRateSnapshot;

@Service
public class ExchangeRateProviderRefreshService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final ExchangeRateProviderClient exchangeRateProviderClient;
    private final ExchangeRateCache exchangeRateCache;
    private final Clock clock;

    @Autowired
    public ExchangeRateProviderRefreshService(
            ExchangeRateProviderClient exchangeRateProviderClient,
            ExchangeRateCache exchangeRateCache) {
        this(exchangeRateProviderClient, exchangeRateCache, Clock.system(KOREA_ZONE));
    }

    ExchangeRateProviderRefreshService(
            ExchangeRateProviderClient exchangeRateProviderClient,
            ExchangeRateCache exchangeRateCache,
            Clock clock) {
        this.exchangeRateProviderClient = exchangeRateProviderClient;
        this.exchangeRateCache = exchangeRateCache;
        this.clock = clock;
    }

    public Optional<ExchangeRateSnapshot> refresh(String localCurrency, LocalDate baseDate) {
        Optional<ProviderExchangeRateSnapshot> providerSnapshot =
                exchangeRateProviderClient.findKrwToLocalRate(localCurrency, baseDate);
        if (providerSnapshot.isEmpty()) {
            return Optional.empty();
        }
        ProviderExchangeRateSnapshot snapshot = providerSnapshot.orElseThrow();
        return Optional.of(exchangeRateCache.put(
                snapshot.localCurrency(),
                snapshot.krwToLocalRate(),
                snapshot.providerTimestamp() == null ? clock.instant() : snapshot.providerTimestamp()));
    }
}
