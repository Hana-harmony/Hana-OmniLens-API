package com.hana.omnilens.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.hana.omnilens.config.ApiRateLimitProperties;
import com.hana.omnilens.security.ApiKeyRateLimiter.RateLimitDecision;

class ApiKeyRateLimiterTest {

    private final AtomicLong nowNanos = new AtomicLong();

    @Test
    void rejectsUntilRefillPeriodPasses() {
        ApiKeyRateLimiter limiter = new ApiKeyRateLimiter(
                new ApiRateLimitProperties(true, 1, 1, Duration.ofSeconds(10), 100),
                nowNanos::get);

        assertThat(limiter.consume("api-key-fingerprint").allowed()).isTrue();
        RateLimitDecision rejected = limiter.consume("api-key-fingerprint");
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(10);

        nowNanos.addAndGet(Duration.ofSeconds(10).toNanos());

        assertThat(limiter.consume("api-key-fingerprint").allowed()).isTrue();
    }

    @Test
    void disabledLimiterAlwaysAllowsRequests() {
        ApiKeyRateLimiter limiter = new ApiKeyRateLimiter(
                new ApiRateLimitProperties(false, 1, 1, Duration.ofSeconds(10), 100),
                nowNanos::get);

        assertThat(limiter.consume("api-key-fingerprint").allowed()).isTrue();
        assertThat(limiter.consume("api-key-fingerprint").allowed()).isTrue();
    }
}
