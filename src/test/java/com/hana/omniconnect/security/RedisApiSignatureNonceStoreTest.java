package com.hana.omniconnect.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisApiSignatureNonceStoreTest {

    private static final Instant NOW = Instant.parse("2026-06-04T00:00:00Z");

    @Test
    void rememberUsesRedisSetIfAbsentWithTtl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        RedisApiSignatureNonceStore store = new RedisApiSignatureNonceStore(
                redisTemplate,
                Clock.fixed(NOW, ZoneOffset.UTC));

        when(redisTemplate.opsForValue()).thenReturn(operations);
        when(operations.setIfAbsent(
                eq("omni-connect:security:signature:nonce:fingerprint:nonce-a"),
                eq("1"),
                eq(Duration.ofMinutes(5)))).thenReturn(Boolean.TRUE);

        assertThat(store.remember("fingerprint", "nonce-a", NOW.plus(Duration.ofMinutes(5)))).isTrue();
    }

    @Test
    void rememberRejectsAlreadyStoredNonce() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        RedisApiSignatureNonceStore store = new RedisApiSignatureNonceStore(
                redisTemplate,
                Clock.fixed(NOW, ZoneOffset.UTC));

        when(redisTemplate.opsForValue()).thenReturn(operations);
        when(operations.setIfAbsent(
                eq("omni-connect:security:signature:nonce:fingerprint:nonce-a"),
                eq("1"),
                eq(Duration.ofMinutes(5)))).thenReturn(Boolean.FALSE);

        assertThat(store.remember("fingerprint", "nonce-a", NOW.plus(Duration.ofMinutes(5)))).isFalse();
    }

    @Test
    void rememberRejectsExpiredNonceWindow() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisApiSignatureNonceStore store = new RedisApiSignatureNonceStore(
                redisTemplate,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(store.remember("fingerprint", "nonce-a", NOW.minusSeconds(1))).isFalse();
    }
}
