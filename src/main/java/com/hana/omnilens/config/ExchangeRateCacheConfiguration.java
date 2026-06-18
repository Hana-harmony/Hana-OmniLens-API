package com.hana.omnilens.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.hana.omnilens.market.application.ExchangeRateCache;
import com.hana.omnilens.market.application.InMemoryExchangeRateCache;
import com.hana.omnilens.market.application.RedisExchangeRateCache;

@Configuration
public class ExchangeRateCacheConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ExchangeRateCache exchangeRateCache(
            ExchangeRateCacheProperties properties,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectMapper objectMapper) {
        ExchangeRateCache fallbackCache = new InMemoryExchangeRateCache();
        if (properties.mode() == ExchangeRateCacheProperties.Mode.IN_MEMORY) {
            return fallbackCache;
        }

        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return fallbackCache;
        }

        return new RedisExchangeRateCache(redisTemplate, objectMapper, fallbackCache, properties.ttl());
    }
}
