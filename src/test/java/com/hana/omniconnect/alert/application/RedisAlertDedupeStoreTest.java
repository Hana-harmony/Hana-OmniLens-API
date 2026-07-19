package com.hana.omniconnect.alert.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisAlertDedupeStoreTest {

    @Test
    void markIfFirstUsesRedisSetIfAbsentWithTtl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        InMemoryAlertDedupeStore fallbackStore = new InMemoryAlertDedupeStore(10);
        RedisAlertDedupeStore store = new RedisAlertDedupeStore(
                redisTemplate,
                fallbackStore,
                Duration.ofHours(24));

        when(redisTemplate.opsForValue()).thenReturn(operations);
        when(operations.setIfAbsent(
                eq("omni-connect:alert:dedupe:partner:NEWS:https://example.com/a"),
                eq("1"),
                eq(Duration.ofHours(24)))).thenReturn(Boolean.TRUE);

        assertThat(store.markIfFirst("partner:NEWS:https://example.com/a")).isTrue();
    }

    @Test
    void markIfFirstFallsBackToMemoryWhenRedisFails() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        InMemoryAlertDedupeStore fallbackStore = new InMemoryAlertDedupeStore(10);
        RedisAlertDedupeStore store = new RedisAlertDedupeStore(
                redisTemplate,
                fallbackStore,
                Duration.ofHours(24));

        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));

        assertThat(store.markIfFirst("source")).isTrue();
        assertThat(store.markIfFirst("source")).isFalse();
    }

    @Test
    void removeDeletesRedisAndFallbackKey() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        InMemoryAlertDedupeStore fallbackStore = new InMemoryAlertDedupeStore(10);
        RedisAlertDedupeStore store = new RedisAlertDedupeStore(
                redisTemplate,
                fallbackStore,
                Duration.ofHours(24));

        fallbackStore.markIfFirst("source");

        store.remove("source");

        verify(redisTemplate).delete("omni-connect:alert:dedupe:source");
        assertThat(fallbackStore.markIfFirst("source")).isTrue();
    }
}
