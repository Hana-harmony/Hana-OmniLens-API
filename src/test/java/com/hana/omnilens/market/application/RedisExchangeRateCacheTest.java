package com.hana.omnilens.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisExchangeRateCacheTest {

    private static final Instant UPDATED_AT = Instant.parse("2026-06-04T01:00:00Z");
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void putStoresSnapshotJsonWithTtlAndUpdatesFallback() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        InMemoryExchangeRateCache fallbackCache = new InMemoryExchangeRateCache();
        RedisExchangeRateCache cache = new RedisExchangeRateCache(
                redisTemplate,
                objectMapper,
                fallbackCache,
                Duration.ofHours(24));

        when(redisTemplate.opsForValue()).thenReturn(operations);

        ExchangeRateSnapshot snapshot = cache.put("usd", new BigDecimal("0.00074"), UPDATED_AT);

        assertThat(snapshot.localCurrency()).isEqualTo("USD");
        assertThat(fallbackCache.find("USD")).isPresent();
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(operations).set(
                eq("omnilens:market:exchange-rate:USD"),
                payloadCaptor.capture(),
                eq(Duration.ofHours(24)));
        assertThat(payloadCaptor.getValue()).contains("\"baseCurrency\":\"KRW\"");
        assertThat(payloadCaptor.getValue()).contains("\"localCurrency\":\"USD\"");
        assertThat(payloadCaptor.getValue()).contains("\"fxRate\":0.00074");
    }

    @Test
    void findReadsRedisSnapshot() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        RedisExchangeRateCache cache = new RedisExchangeRateCache(
                redisTemplate,
                objectMapper,
                new InMemoryExchangeRateCache(),
                Duration.ofHours(24));

        when(redisTemplate.opsForValue()).thenReturn(operations);
        when(operations.get("omnilens:market:exchange-rate:JPY"))
                .thenReturn("""
                        {"baseCurrency":"KRW","localCurrency":"JPY","fxRate":0.108,"updatedAt":"2026-06-04T01:00:00Z"}
                        """);

        assertThat(cache.find("jpy")).isPresent();
        assertThat(cache.find("jpy").orElseThrow().fxRate()).isEqualByComparingTo("0.108");
        assertThat(cache.find("jpy").orElseThrow().updatedAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    void putAndFindFallBackToMemoryWhenRedisFails() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisExchangeRateCache cache = new RedisExchangeRateCache(
                redisTemplate,
                objectMapper,
                new InMemoryExchangeRateCache(),
                Duration.ofHours(24));

        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));

        cache.put("USD", new BigDecimal("0.00074"), UPDATED_AT);

        assertThat(cache.find("USD")).isPresent();
        assertThat(cache.find("USD").orElseThrow().fxRate()).isEqualByComparingTo("0.00074");
    }
}
