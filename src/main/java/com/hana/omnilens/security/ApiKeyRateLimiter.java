package com.hana.omnilens.security;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.hana.omnilens.config.ApiRateLimitProperties;

@Component
public class ApiKeyRateLimiter {

    private final ApiRateLimitProperties properties;
    private final LongSupplier nanoTime;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Autowired
    public ApiKeyRateLimiter(ApiRateLimitProperties properties) {
        this(properties, System::nanoTime);
    }

    ApiKeyRateLimiter(ApiRateLimitProperties properties, LongSupplier nanoTime) {
        this.properties = properties;
        this.nanoTime = nanoTime;
    }

    public RateLimitDecision consume(String apiKeyFingerprint) {
        if (!properties.enabled()) {
            return RateLimitDecision.accepted();
        }

        evictIfNeeded();
        TokenBucket bucket = buckets.computeIfAbsent(
                apiKeyFingerprint,
                ignored -> new TokenBucket(properties.capacity(), nanoTime.getAsLong()));
        return bucket.consume(properties, nanoTime.getAsLong());
    }

    private void evictIfNeeded() {
        int overflow = buckets.size() - properties.maxBuckets();
        if (overflow < 0) {
            return;
        }

        Iterator<String> iterator = buckets.keySet().iterator();
        for (int removed = 0; iterator.hasNext() && removed <= overflow; removed++) {
            iterator.next();
            iterator.remove();
        }
    }

    public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {

        static RateLimitDecision accepted() {
            return new RateLimitDecision(true, 0);
        }

        static RateLimitDecision rejected(long retryAfterSeconds) {
            return new RateLimitDecision(false, Math.max(1, retryAfterSeconds));
        }
    }

    private static final class TokenBucket {

        private int tokens;
        private long lastRefillNanos;

        private TokenBucket(int capacity, long nowNanos) {
            this.tokens = capacity;
            this.lastRefillNanos = nowNanos;
        }

        private synchronized RateLimitDecision consume(ApiRateLimitProperties properties, long nowNanos) {
            refill(properties, nowNanos);
            if (tokens > 0) {
                tokens--;
                return RateLimitDecision.accepted();
            }
            return RateLimitDecision.rejected(retryAfterSeconds(properties.refillPeriod()));
        }

        private void refill(ApiRateLimitProperties properties, long nowNanos) {
            long refillPeriodNanos = properties.refillPeriod().toNanos();
            if (refillPeriodNanos <= 0) {
                tokens = properties.capacity();
                lastRefillNanos = nowNanos;
                return;
            }

            long elapsedPeriods = (nowNanos - lastRefillNanos) / refillPeriodNanos;
            if (elapsedPeriods <= 0) {
                return;
            }

            long refilledTokens = elapsedPeriods * properties.refillTokens();
            tokens = (int) Math.min(properties.capacity(), tokens + refilledTokens);
            lastRefillNanos += elapsedPeriods * refillPeriodNanos;
        }

        private long retryAfterSeconds(Duration refillPeriod) {
            return Math.max(1, refillPeriod.toSeconds());
        }
    }
}
