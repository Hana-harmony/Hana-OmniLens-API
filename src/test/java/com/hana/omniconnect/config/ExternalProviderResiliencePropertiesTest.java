package com.hana.omniconnect.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ExternalProviderResiliencePropertiesTest {

    @Test
    void constructorAppliesOperationalDefaults() {
        ExternalProviderResilienceProperties properties =
                new ExternalProviderResilienceProperties(null, null, null, null);

        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.retry().enabled()).isTrue();
        assertThat(properties.retry().maxAttempts()).isEqualTo(2);
        assertThat(properties.retry().backoff()).isEqualTo(Duration.ofMillis(150));
        assertThat(properties.circuitBreaker().enabled()).isTrue();
        assertThat(properties.circuitBreaker().failureThreshold()).isEqualTo(5);
        assertThat(properties.circuitBreaker().openDuration()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void constructorNormalizesInvalidNestedValues() {
        ExternalProviderResilienceProperties properties = new ExternalProviderResilienceProperties(
                Duration.ofMillis(500),
                Duration.ofSeconds(3),
                new ExternalProviderResilienceProperties.Retry(true, 0, null),
                new ExternalProviderResilienceProperties.CircuitBreaker(true, -1, null));

        assertThat(properties.retry().maxAttempts()).isEqualTo(2);
        assertThat(properties.retry().backoff()).isEqualTo(Duration.ofMillis(150));
        assertThat(properties.circuitBreaker().failureThreshold()).isEqualTo(5);
        assertThat(properties.circuitBreaker().openDuration()).isEqualTo(Duration.ofSeconds(30));
    }
}
