package com.hana.omnilens.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omnilens.providers.resilience")
public record ExternalProviderResilienceProperties(
        Duration connectTimeout,
        Duration readTimeout,
        Retry retry,
        CircuitBreaker circuitBreaker
) {

    public ExternalProviderResilienceProperties {
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
        retry = retry == null ? Retry.defaults() : retry.withDefaults();
        circuitBreaker = circuitBreaker == null ? CircuitBreaker.defaults() : circuitBreaker.withDefaults();
    }

    public record Retry(boolean enabled, int maxAttempts, Duration backoff) {

        private static Retry defaults() {
            return new Retry(true, 2, Duration.ofMillis(150));
        }

        private Retry withDefaults() {
            return new Retry(enabled, maxAttempts <= 0 ? 2 : maxAttempts, backoff == null ? Duration.ofMillis(150) : backoff);
        }
    }

    public record CircuitBreaker(boolean enabled, int failureThreshold, Duration openDuration) {

        private static CircuitBreaker defaults() {
            return new CircuitBreaker(true, 5, Duration.ofSeconds(30));
        }

        private CircuitBreaker withDefaults() {
            return new CircuitBreaker(
                    enabled,
                    failureThreshold <= 0 ? 5 : failureThreshold,
                    openDuration == null ? Duration.ofSeconds(30) : openDuration);
        }
    }
}
