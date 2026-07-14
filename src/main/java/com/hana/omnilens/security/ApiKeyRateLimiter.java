package com.hana.omnilens.security;

import java.util.List;
import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.hana.omnilens.config.ApiRateLimitProperties;

@Component
public class ApiKeyRateLimiter {

    private static final String KEY_PREFIX = "omnilens:security:rate-limit:";
    private static final long PACKING_FACTOR = 1_000_000_000L;
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('PTTL', KEYS[1])
            return current * 1000000000 + ttl
            """, Long.class);

    private final ApiRateLimitProperties properties;
    private final StringRedisTemplate redisTemplate;

    public ApiKeyRateLimiter(ApiRateLimitProperties properties, StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    public RateLimitDecision consume(String key) {
        return consume(key, properties.maxRequests(), properties.window());
    }

    public RateLimitDecision consume(String key, int maxRequests, Duration window) {
        if (!properties.enabled()) {
            return RateLimitDecision.accepted();
        }
        Long packed = redisTemplate.execute(
                CONSUME_SCRIPT,
                List.of(KEY_PREFIX + key),
                String.valueOf(window.toMillis()));
        if (packed == null) {
            throw new IllegalStateException("rate limit store is unavailable");
        }
        long count = packed / PACKING_FACTOR;
        long ttlMillis = Math.max(1, packed % PACKING_FACTOR);
        return count <= maxRequests
                ? RateLimitDecision.accepted()
                : RateLimitDecision.rejected((ttlMillis + 999) / 1000);
    }

    public void clear(String key) {
        if (!properties.enabled()) {
            return;
        }
        redisTemplate.delete(KEY_PREFIX + key);
    }

    public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {

        static RateLimitDecision accepted() {
            return new RateLimitDecision(true, 0);
        }

        static RateLimitDecision rejected(long retryAfterSeconds) {
            return new RateLimitDecision(false, Math.max(1, retryAfterSeconds));
        }
    }
}
