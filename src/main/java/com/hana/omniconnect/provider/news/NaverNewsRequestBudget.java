package com.hana.omniconnect.provider.news;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.hana.omniconnect.config.ExternalProviderProperties;

@Component
public class NaverNewsRequestBudget {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final String KEY_PREFIX = "omni-connect:provider:naver-news:daily:";
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            local limit = tonumber(ARGV[1])
            if current >= limit then
              return -1
            end
            current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIREAT', KEYS[1], ARGV[2])
            end
            return current
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ExternalProviderProperties.NaverNews properties;
    private final Clock clock;

    @Autowired
    public NaverNewsRequestBudget(
            StringRedisTemplate redisTemplate,
            ExternalProviderProperties properties) {
        this(redisTemplate, properties.naverNews(), Clock.systemUTC());
    }

    NaverNewsRequestBudget(
            StringRedisTemplate redisTemplate,
            ExternalProviderProperties.NaverNews properties,
            Clock clock) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    public void consumeOrThrow() {
        LocalDate koreaDate = LocalDate.now(clock.withZone(KOREA_ZONE));
        int dailyBudget = properties.effectiveDailyRequestBudget();
        Instant resetAt = koreaDate.plusDays(1).atStartOfDay(KOREA_ZONE).toInstant();
        Long consumed = redisTemplate.execute(
                CONSUME_SCRIPT,
                List.of(KEY_PREFIX + koreaDate),
                String.valueOf(dailyBudget),
                String.valueOf(resetAt.toEpochMilli()));
        if (consumed == null) {
            throw new IllegalStateException("Naver news request budget store is unavailable");
        }
        if (consumed < 0) {
            throw new NaverNewsDailyBudgetExceededException(dailyBudget);
        }
    }
}
