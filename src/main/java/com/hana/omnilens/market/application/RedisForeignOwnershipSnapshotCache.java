package com.hana.omnilens.market.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.hana.omnilens.provider.market.KrxForeignOwnershipSnapshot;

public class RedisForeignOwnershipSnapshotCache implements ForeignOwnershipSnapshotCache {

    private static final String KEY_PREFIX = "omnilens:market:foreign-ownership:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ForeignOwnershipSnapshotCache fallbackCache;
    private final Duration ttl;

    public RedisForeignOwnershipSnapshotCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ForeignOwnershipSnapshotCache fallbackCache,
            Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.fallbackCache = fallbackCache;
        this.ttl = ttl;
    }

    @Override
    public Optional<KrxForeignOwnershipSnapshot> find(String stockCode) {
        try {
            String payload = redisTemplate.opsForValue().get(redisKey(stockCode));
            if (payload == null) {
                return fallbackCache.find(stockCode);
            }
            return Optional.of(toSnapshot(payload));
        } catch (RuntimeException | JsonProcessingException exception) {
            return fallbackCache.find(stockCode);
        }
    }

    @Override
    public void put(KrxForeignOwnershipSnapshot snapshot) {
        try {
            redisTemplate.opsForValue().set(
                    redisKey(snapshot.stockCode()),
                    objectMapper.writeValueAsString(RedisForeignOwnershipSnapshot.from(snapshot)),
                    ttl);
        } catch (RuntimeException | JsonProcessingException exception) {
            fallbackCache.put(snapshot);
            return;
        }
        fallbackCache.put(snapshot);
    }

    private KrxForeignOwnershipSnapshot toSnapshot(String payload) throws JsonProcessingException {
        RedisForeignOwnershipSnapshot snapshot =
                objectMapper.readValue(payload, RedisForeignOwnershipSnapshot.class);
        return new KrxForeignOwnershipSnapshot(
                snapshot.stockCode(),
                snapshot.foreignOwnedQuantity(),
                snapshot.foreignOwnershipRate(),
                snapshot.foreignLimitQuantity(),
                snapshot.foreignLimitExhaustionRate(),
                snapshot.baseDate());
    }

    private String redisKey(String stockCode) {
        return KEY_PREFIX + stockCode;
    }

    private record RedisForeignOwnershipSnapshot(
            String stockCode,
            long foreignOwnedQuantity,
            BigDecimal foreignOwnershipRate,
            long foreignLimitQuantity,
            BigDecimal foreignLimitExhaustionRate,
            LocalDate baseDate
    ) {

        private static RedisForeignOwnershipSnapshot from(KrxForeignOwnershipSnapshot snapshot) {
            return new RedisForeignOwnershipSnapshot(
                    snapshot.stockCode(),
                    snapshot.foreignOwnedQuantity(),
                    snapshot.foreignOwnershipRate(),
                    snapshot.foreignLimitQuantity(),
                    snapshot.foreignLimitExhaustionRate(),
                    snapshot.baseDate());
        }
    }
}
