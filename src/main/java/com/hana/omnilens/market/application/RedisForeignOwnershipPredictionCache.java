package com.hana.omnilens.market.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.hana.omnilens.market.domain.ForeignOwnershipPrediction;

public class RedisForeignOwnershipPredictionCache implements ForeignOwnershipPredictionCache {

    private static final String KEY_PREFIX = "omnilens:market:foreign-ownership-prediction:v2:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ForeignOwnershipPredictionCache fallbackCache;
    private final Duration ttl;

    public RedisForeignOwnershipPredictionCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ForeignOwnershipPredictionCache fallbackCache,
            Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.fallbackCache = fallbackCache;
        this.ttl = ttl;
    }

    @Override
    public Optional<ForeignOwnershipPrediction> find(String stockCode, LocalDate baseDate) {
        try {
            String payload = redisTemplate.opsForValue().get(redisKey(stockCode, baseDate));
            if (payload == null) {
                return fallbackCache.find(stockCode, baseDate);
            }
            return Optional.of(toPrediction(payload));
        } catch (RuntimeException | JsonProcessingException exception) {
            return fallbackCache.find(stockCode, baseDate);
        }
    }

    @Override
    public void put(String stockCode, ForeignOwnershipPrediction prediction) {
        if (prediction.baseDate() == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    redisKey(stockCode, prediction.baseDate()),
                    objectMapper.writeValueAsString(RedisForeignOwnershipPrediction.from(prediction)),
                    ttl);
        } catch (RuntimeException | JsonProcessingException exception) {
            fallbackCache.put(stockCode, prediction);
            return;
        }
        fallbackCache.put(stockCode, prediction);
    }

    private ForeignOwnershipPrediction toPrediction(String payload) throws JsonProcessingException {
        RedisForeignOwnershipPrediction prediction =
                objectMapper.readValue(payload, RedisForeignOwnershipPrediction.class);
        return new ForeignOwnershipPrediction(
                prediction.minForeignLimitExhaustionRate(),
                prediction.baseForeignLimitExhaustionRate(),
                prediction.maxForeignLimitExhaustionRate(),
                prediction.orderImpactRate(),
                prediction.intradayUncertaintyRate(),
                prediction.observedIntradayVolume(),
                prediction.trendDailyChangeRate(),
                prediction.historyObservationCount(),
                prediction.historyWindowDays(),
                prediction.baseDate(),
                prediction.calculatedAt(),
                prediction.confidenceLevel(),
                prediction.confidenceScore(),
                prediction.modelVersion(),
                prediction.source());
    }

    private String redisKey(String stockCode, LocalDate baseDate) {
        return KEY_PREFIX + stockCode + ":" + baseDate;
    }

    private record RedisForeignOwnershipPrediction(
            BigDecimal minForeignLimitExhaustionRate,
            BigDecimal baseForeignLimitExhaustionRate,
            BigDecimal maxForeignLimitExhaustionRate,
            BigDecimal orderImpactRate,
            BigDecimal intradayUncertaintyRate,
            long observedIntradayVolume,
            BigDecimal trendDailyChangeRate,
            int historyObservationCount,
            int historyWindowDays,
            LocalDate baseDate,
            Instant calculatedAt,
            String confidenceLevel,
            BigDecimal confidenceScore,
            String modelVersion,
            String source
    ) {

        private static RedisForeignOwnershipPrediction from(ForeignOwnershipPrediction prediction) {
            return new RedisForeignOwnershipPrediction(
                    prediction.minForeignLimitExhaustionRate(),
                    prediction.baseForeignLimitExhaustionRate(),
                    prediction.maxForeignLimitExhaustionRate(),
                    prediction.orderImpactRate(),
                    prediction.intradayUncertaintyRate(),
                    prediction.observedIntradayVolume(),
                    prediction.trendDailyChangeRate(),
                    prediction.historyObservationCount(),
                    prediction.historyWindowDays(),
                    prediction.baseDate(),
                    prediction.calculatedAt(),
                    prediction.confidenceLevel(),
                    prediction.confidenceScore(),
                    prediction.modelVersion(),
                    prediction.source());
        }
    }
}
