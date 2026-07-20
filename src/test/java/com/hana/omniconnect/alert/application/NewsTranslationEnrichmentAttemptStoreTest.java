package com.hana.omniconnect.alert.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class NewsTranslationEnrichmentAttemptStoreTest {

    @Test
    void claimUsesAtomicRedisCooldown() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        NewsTranslationEnrichmentAttemptStore store = new NewsTranslationEnrichmentAttemptStore(redisTemplate);

        when(redisTemplate.opsForValue()).thenReturn(operations);
        when(operations.setIfAbsent(
                eq("omni-connect:news-translation:attempt:alert:alert-id"),
                eq("1"),
                eq(Duration.ofHours(6)))).thenReturn(Boolean.TRUE);

        assertThat(store.claim("alert", "alert-id")).isTrue();
    }

    @Test
    void claimRejectsArticleDuringCooldown() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        NewsTranslationEnrichmentAttemptStore store = new NewsTranslationEnrichmentAttemptStore(redisTemplate);

        when(redisTemplate.opsForValue()).thenReturn(operations);
        when(operations.setIfAbsent(
                eq("omni-connect:news-translation:attempt:market:news-id"),
                eq("1"),
                eq(Duration.ofHours(6)))).thenReturn(Boolean.FALSE);

        assertThat(store.claim("market", "news-id")).isFalse();
    }
}
