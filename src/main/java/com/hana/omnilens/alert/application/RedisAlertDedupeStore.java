package com.hana.omnilens.alert.application;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public class RedisAlertDedupeStore implements AlertDedupeStore {

    private static final String KEY_PREFIX = "omnilens:alert:dedupe:";
    private static final DefaultRedisScript<Long> RELEASE_LEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AlertDedupeStore fallbackStore;
    private final Duration ttl;

    public RedisAlertDedupeStore(
            StringRedisTemplate redisTemplate,
            AlertDedupeStore fallbackStore,
            Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.fallbackStore = fallbackStore;
        this.ttl = ttl;
    }

    @Override
    public boolean markIfFirst(String key) {
        try {
            Boolean stored = redisTemplate.opsForValue().setIfAbsent(redisKey(key), "1", ttl);
            return Boolean.TRUE.equals(stored);
        } catch (RuntimeException exception) {
            return fallbackStore.markIfFirst(key);
        }
    }

    @Override
    public void remove(String key) {
        try {
            redisTemplate.delete(redisKey(key));
        } catch (RuntimeException exception) {
            fallbackStore.remove(key);
            return;
        }
        fallbackStore.remove(key);
    }

    @Override
    public Optional<String> acquireLease(String key, Duration leaseDuration) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean stored = redisTemplate.opsForValue()
                    .setIfAbsent(redisKey(key), token, leaseDuration);
            return Boolean.TRUE.equals(stored) ? Optional.of(token) : Optional.empty();
        } catch (RuntimeException exception) {
            return fallbackStore.acquireLease(key, leaseDuration);
        }
    }

    @Override
    public void releaseLease(String key, String leaseToken) {
        try {
            redisTemplate.execute(RELEASE_LEASE_SCRIPT, List.of(redisKey(key)), leaseToken);
        } catch (RuntimeException exception) {
            fallbackStore.releaseLease(key, leaseToken);
            return;
        }
        fallbackStore.releaseLease(key, leaseToken);
    }

    private String redisKey(String key) {
        return KEY_PREFIX + key;
    }
}
