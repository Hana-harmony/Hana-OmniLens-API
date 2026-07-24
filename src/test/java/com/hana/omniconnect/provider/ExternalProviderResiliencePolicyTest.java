package com.hana.omniconnect.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import com.hana.omniconnect.config.ExternalProviderResilienceProperties;

class ExternalProviderResiliencePolicyTest {

    @Test
    void executeRetriesRestClientExceptionUntilSuccess() {
        ExternalProviderResiliencePolicy policy = new ExternalProviderResiliencePolicy(
                properties(true, 2, Duration.ZERO, true, 5, Duration.ofSeconds(30)),
                Clock.systemUTC());
        AtomicInteger attempts = new AtomicInteger();

        String result = policy.execute("naver-news", () -> {
            if (attempts.incrementAndGet() == 1) {
                throw new ResourceAccessException("temporary network failure");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(2);
    }

    @Test
    void executeOnceDoesNotSpendProviderQuotaOnRetry() {
        ExternalProviderResiliencePolicy policy = new ExternalProviderResiliencePolicy(
                properties(true, 3, Duration.ZERO, true, 5, Duration.ofSeconds(30)),
                Clock.systemUTC());
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> policy.executeOnce("naver-news", () -> {
            attempts.incrementAndGet();
            throw new ResourceAccessException("temporary network failure");
        })).isInstanceOf(ResourceAccessException.class);

        assertThat(attempts).hasValue(1);
    }

    @Test
    void executeOpensCircuitAfterFailureThreshold() {
        ExternalProviderResiliencePolicy policy = new ExternalProviderResiliencePolicy(
                properties(false, 1, Duration.ZERO, true, 2, Duration.ofSeconds(30)),
                Clock.fixed(Instant.parse("2026-06-04T00:00:00Z"), ZoneOffset.UTC));
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> policy.execute("open-dart", () -> {
            attempts.incrementAndGet();
            throw new ResourceAccessException("first failure");
        })).isInstanceOf(ResourceAccessException.class);
        assertThatThrownBy(() -> policy.execute("open-dart", () -> {
            attempts.incrementAndGet();
            throw new ResourceAccessException("second failure");
        })).isInstanceOf(ResourceAccessException.class);
        assertThatThrownBy(() -> policy.execute("open-dart", () -> {
            attempts.incrementAndGet();
            return "should-not-run";
        })).isInstanceOf(ProviderCircuitOpenException.class)
                .hasMessageContaining("open-dart");

        assertThat(attempts).hasValue(2);
    }

    @Test
    void executeDoesNotRetryNonRestClientException() {
        ExternalProviderResiliencePolicy policy = new ExternalProviderResiliencePolicy(
                properties(true, 3, Duration.ZERO, true, 5, Duration.ofSeconds(30)),
                Clock.systemUTC());
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> policy.execute("hannah-ai", () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("invalid provider response");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid provider response");

        assertThat(attempts).hasValue(1);
    }

    private ExternalProviderResilienceProperties properties(
            boolean retryEnabled,
            int maxAttempts,
            Duration backoff,
            boolean circuitEnabled,
            int failureThreshold,
            Duration openDuration) {
        return new ExternalProviderResilienceProperties(
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                new ExternalProviderResilienceProperties.Retry(retryEnabled, maxAttempts, backoff),
                new ExternalProviderResilienceProperties.CircuitBreaker(
                        circuitEnabled,
                        failureThreshold,
                        openDuration));
    }
}
