package com.hana.omnilens.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisApiSignatureNonceStore implements ApiSignatureNonceStore {

    private static final String KEY_PREFIX = "omnilens:security:signature:nonce:";

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    public RedisApiSignatureNonceStore(StringRedisTemplate redisTemplate) {
        this(redisTemplate, Clock.systemUTC());
    }

    RedisApiSignatureNonceStore(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    @Override
    public boolean remember(String apiKeyFingerprint, String nonce, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(clock), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            return false;
        }
        Boolean stored = redisTemplate.opsForValue()
                .setIfAbsent(redisKey(apiKeyFingerprint, nonce), "1", ttl);
        return Boolean.TRUE.equals(stored);
    }

    private String redisKey(String apiKeyFingerprint, String nonce) {
        return KEY_PREFIX + apiKeyFingerprint + ":" + nonce;
    }
}
