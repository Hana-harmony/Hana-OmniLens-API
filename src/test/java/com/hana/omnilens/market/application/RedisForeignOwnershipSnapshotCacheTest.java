package com.hana.omnilens.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.hana.omnilens.provider.market.KrxForeignOwnershipSnapshot;

class RedisForeignOwnershipSnapshotCacheTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void putStoresSnapshotJsonWithTtlAndUpdatesFallback() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        InMemoryForeignOwnershipSnapshotCache fallbackCache = new InMemoryForeignOwnershipSnapshotCache();
        RedisForeignOwnershipSnapshotCache cache = new RedisForeignOwnershipSnapshotCache(
                redisTemplate,
                objectMapper,
                fallbackCache,
                Duration.ofHours(24));

        when(redisTemplate.opsForValue()).thenReturn(operations);

        cache.put(snapshot());

        assertThat(fallbackCache.find("005930")).isPresent();
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(operations).set(
                eq("omnilens:market:foreign-ownership:005930"),
                payloadCaptor.capture(),
                eq(Duration.ofHours(24)));
        assertThat(payloadCaptor.getValue()).contains("\"stockCode\":\"005930\"");
        assertThat(payloadCaptor.getValue()).contains("\"foreignOwnedQuantity\":3642091300");
        assertThat(payloadCaptor.getValue()).contains("\"foreignOwnershipRate\":54.19");
    }

    @Test
    void findReadsRedisSnapshot() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        RedisForeignOwnershipSnapshotCache cache = new RedisForeignOwnershipSnapshotCache(
                redisTemplate,
                objectMapper,
                new InMemoryForeignOwnershipSnapshotCache(),
                Duration.ofHours(24));

        when(redisTemplate.opsForValue()).thenReturn(operations);
        when(operations.get("omnilens:market:foreign-ownership:005930"))
                .thenReturn("""
                        {
                          "stockCode": "005930",
                          "foreignOwnedQuantity": 3642091300,
                          "foreignOwnershipRate": 54.19,
                          "foreignLimitQuantity": 6720000000,
                          "foreignLimitExhaustionRate": 54.21,
                          "baseDate": "2026-06-03"
                        }
                        """);

        assertThat(cache.find("005930")).isPresent();
        KrxForeignOwnershipSnapshot snapshot = cache.find("005930").orElseThrow();
        assertThat(snapshot.foreignOwnedQuantity()).isEqualTo(3_642_091_300L);
        assertThat(snapshot.foreignOwnershipRate()).isEqualByComparingTo("54.19");
        assertThat(snapshot.baseDate()).isEqualTo(LocalDate.of(2026, 6, 3));
    }

    @Test
    void putAndFindFallBackToMemoryWhenRedisFails() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisForeignOwnershipSnapshotCache cache = new RedisForeignOwnershipSnapshotCache(
                redisTemplate,
                objectMapper,
                new InMemoryForeignOwnershipSnapshotCache(),
                Duration.ofHours(24));

        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));

        cache.put(snapshot());

        assertThat(cache.find("005930")).isPresent();
        assertThat(cache.find("005930").orElseThrow().foreignLimitExhaustionRate())
                .isEqualByComparingTo("54.21");
    }

    private KrxForeignOwnershipSnapshot snapshot() {
        return new KrxForeignOwnershipSnapshot(
                "005930",
                3_642_091_300L,
                new BigDecimal("54.19"),
                6_720_000_000L,
                new BigDecimal("54.21"),
                LocalDate.of(2026, 6, 3));
    }
}
