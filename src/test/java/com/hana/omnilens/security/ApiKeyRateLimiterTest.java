package com.hana.omnilens.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.hana.omnilens.config.ApiRateLimitProperties;

class ApiKeyRateLimiterTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void rejectsWhenRedisWindowLimitIsExceeded() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), (Object[]) any()))
                .thenReturn(1_000_010_000L, 2_000_010_000L);
        ApiKeyRateLimiter limiter = new ApiKeyRateLimiter(
                new ApiRateLimitProperties(true, 1, Duration.ofSeconds(10)),
                redisTemplate);

        assertThat(limiter.consume("api-key-fingerprint").allowed()).isTrue();
        assertThat(limiter.consume("api-key-fingerprint").allowed()).isFalse();
    }

    @Test
    void disabledLimiterDoesNotUseRedis() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ApiKeyRateLimiter limiter = new ApiKeyRateLimiter(
                new ApiRateLimitProperties(false, 1, Duration.ofSeconds(10)),
                redisTemplate);

        assertThat(limiter.consume("api-key-fingerprint").allowed()).isTrue();
        verifyNoInteractions(redisTemplate);
    }
}
