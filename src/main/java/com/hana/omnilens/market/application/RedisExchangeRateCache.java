package com.hana.omnilens.market.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisExchangeRateCache implements ExchangeRateCache {

    private static final String KEY_PREFIX = "omnilens:market:exchange-rate:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ExchangeRateCache fallbackCache;
    private final Duration ttl;

    public RedisExchangeRateCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ExchangeRateCache fallbackCache,
            Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.fallbackCache = fallbackCache;
        this.ttl = ttl;
    }

    @Override
    public Optional<ExchangeRateSnapshot> find(String localCurrency) {
        try {
            String payload = redisTemplate.opsForValue().get(redisKey(localCurrency));
            if (payload == null) {
                return fallbackCache.find(localCurrency);
            }
            return Optional.of(toSnapshot(payload));
        } catch (RuntimeException | JsonProcessingException exception) {
            return fallbackCache.find(localCurrency);
        }
    }

    @Override
    public ExchangeRateSnapshot put(String localCurrency, BigDecimal fxRate, Instant updatedAt) {
        ExchangeRateSnapshot snapshot = ExchangeRateSnapshot.krwToLocal(localCurrency, fxRate, updatedAt);
        try {
            redisTemplate.opsForValue().set(
                    redisKey(snapshot.localCurrency()),
                    objectMapper.writeValueAsString(RedisExchangeRateSnapshot.from(snapshot)),
                    ttl);
        } catch (RuntimeException | JsonProcessingException exception) {
            return fallbackCache.put(localCurrency, fxRate, updatedAt);
        }
        fallbackCache.put(localCurrency, fxRate, updatedAt);
        return snapshot;
    }

    private ExchangeRateSnapshot toSnapshot(String payload) throws JsonProcessingException {
        RedisExchangeRateSnapshot snapshot = objectMapper.readValue(payload, RedisExchangeRateSnapshot.class);
        return new ExchangeRateSnapshot(
                snapshot.baseCurrency(),
                snapshot.localCurrency(),
                snapshot.fxRate(),
                snapshot.updatedAt());
    }

    private String redisKey(String localCurrency) {
        return KEY_PREFIX + localCurrency.toUpperCase(Locale.ROOT);
    }

    private record RedisExchangeRateSnapshot(
            String baseCurrency,
            String localCurrency,
            BigDecimal fxRate,
            Instant updatedAt
    ) {

        private static RedisExchangeRateSnapshot from(ExchangeRateSnapshot snapshot) {
            return new RedisExchangeRateSnapshot(
                    snapshot.baseCurrency(),
                    snapshot.localCurrency(),
                    snapshot.fxRate(),
                    snapshot.updatedAt());
        }
    }
}
