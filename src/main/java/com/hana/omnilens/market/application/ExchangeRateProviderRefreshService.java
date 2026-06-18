package com.hana.omnilens.market.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hana.omnilens.provider.market.KoreaEximExchangeRateClient;
import com.hana.omnilens.provider.market.KoreaEximExchangeRateSnapshot;

@Service
public class ExchangeRateProviderRefreshService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final KoreaEximExchangeRateClient koreaEximExchangeRateClient;
    private final ExchangeRateCache exchangeRateCache;
    private final Clock clock;

    @Autowired
    public ExchangeRateProviderRefreshService(
            KoreaEximExchangeRateClient koreaEximExchangeRateClient,
            ExchangeRateCache exchangeRateCache) {
        this(koreaEximExchangeRateClient, exchangeRateCache, Clock.system(KOREA_ZONE));
    }

    ExchangeRateProviderRefreshService(
            KoreaEximExchangeRateClient koreaEximExchangeRateClient,
            ExchangeRateCache exchangeRateCache,
            Clock clock) {
        this.koreaEximExchangeRateClient = koreaEximExchangeRateClient;
        this.exchangeRateCache = exchangeRateCache;
        this.clock = clock;
    }

    public Optional<ExchangeRateSnapshot> refresh(String localCurrency, LocalDate baseDate) {
        Optional<KoreaEximExchangeRateSnapshot> providerSnapshot =
                koreaEximExchangeRateClient.findKrwToLocalRate(localCurrency, baseDate);
        if (providerSnapshot.isEmpty()) {
            return Optional.empty();
        }
        KoreaEximExchangeRateSnapshot snapshot = providerSnapshot.orElseThrow();
        return Optional.of(exchangeRateCache.put(
                snapshot.localCurrency(),
                snapshot.krwToLocalRate(),
                Instant.now(clock)));
    }
}
