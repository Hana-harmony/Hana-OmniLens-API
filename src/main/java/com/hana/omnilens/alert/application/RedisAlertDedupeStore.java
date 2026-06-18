package com.hana.omnilens.alert.application;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisAlertDedupeStore implements AlertDedupeStore {

    private static final String KEY_PREFIX = "omnilens:alert:dedupe:";

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

    private String redisKey(String key) {
        return KEY_PREFIX + key;
    }
}
