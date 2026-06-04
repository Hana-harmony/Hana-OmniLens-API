package com.hana.omnilens.provider;

import java.time.Duration;

import com.hana.omnilens.config.ExternalProviderResilienceProperties;

public final class ProviderTestResilience {

    private ProviderTestResilience() {
    }

    public static ExternalProviderResiliencePolicy disabled() {
        return new ExternalProviderResiliencePolicy(new ExternalProviderResilienceProperties(
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                new ExternalProviderResilienceProperties.Retry(false, 1, Duration.ZERO),
                new ExternalProviderResilienceProperties.CircuitBreaker(false, 1, Duration.ZERO)));
    }
}
