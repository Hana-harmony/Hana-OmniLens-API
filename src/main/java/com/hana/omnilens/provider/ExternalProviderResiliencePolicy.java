package com.hana.omnilens.provider;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import com.hana.omnilens.config.ExternalProviderResilienceProperties;

@Component
public class ExternalProviderResiliencePolicy {

    private final ExternalProviderResilienceProperties properties;
    private final Clock clock;
    private final Map<String, CircuitState> circuits = new ConcurrentHashMap<>();

    @Autowired
    public ExternalProviderResiliencePolicy(ExternalProviderResilienceProperties properties) {
        this(properties, Clock.systemUTC());
    }

    ExternalProviderResiliencePolicy(ExternalProviderResilienceProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public <T> T execute(String providerName, Supplier<T> supplier) {
        CircuitState circuit = circuits.computeIfAbsent(providerName, ignored -> new CircuitState());
        circuit.throwIfOpen(providerName);

        int maxAttempts = properties.retry().enabled() ? properties.retry().maxAttempts() : 1;
        RestClientException lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T result = supplier.get();
                circuit.recordSuccess();
                return result;
            } catch (RestClientException exception) {
                lastException = exception;
                if (attempt >= maxAttempts) {
                    circuit.recordFailure();
                    throw exception;
                }
                sleepBackoff();
            }
        }
        throw lastException == null ? new IllegalStateException("Provider execution failed") : lastException;
    }

    private void sleepBackoff() {
        if (properties.retry().backoff().isZero() || properties.retry().backoff().isNegative()) {
            return;
        }
        try {
            Thread.sleep(properties.retry().backoff().toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Provider retry interrupted", exception);
        }
    }

    private class CircuitState {

        private int failureCount;
        private Instant openUntil = Instant.EPOCH;

        private synchronized void throwIfOpen(String providerName) {
            if (!properties.circuitBreaker().enabled()) {
                return;
            }
            if (Instant.now(clock).isBefore(openUntil)) {
                throw new ProviderCircuitOpenException(providerName);
            }
        }

        private synchronized void recordSuccess() {
            failureCount = 0;
            openUntil = Instant.EPOCH;
        }

        private synchronized void recordFailure() {
            if (!properties.circuitBreaker().enabled()) {
                return;
            }
            failureCount++;
            if (failureCount >= properties.circuitBreaker().failureThreshold()) {
                openUntil = Instant.now(clock).plus(properties.circuitBreaker().openDuration());
            }
        }
    }
}
