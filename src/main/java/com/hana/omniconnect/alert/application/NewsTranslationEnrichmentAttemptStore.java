package com.hana.omniconnect.alert.application;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class NewsTranslationEnrichmentAttemptStore {

    private static final String KEY_PREFIX = "omni-connect:news-translation:attempt:";
    private static final Duration RETRY_COOLDOWN = Duration.ofHours(6);

    private final StringRedisTemplate redisTemplate;

    public NewsTranslationEnrichmentAttemptStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean claim(String source, String eventId) {
        Boolean claimed = redisTemplate.opsForValue()
                .setIfAbsent(redisKey(source, eventId), "1", RETRY_COOLDOWN);
        return Boolean.TRUE.equals(claimed);
    }

    private String redisKey(String source, String eventId) {
        return KEY_PREFIX + source + ":" + eventId;
    }
}
