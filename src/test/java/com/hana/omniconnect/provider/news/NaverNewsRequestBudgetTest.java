package com.hana.omniconnect.provider.news;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.hana.omniconnect.config.ExternalProviderProperties;

class NaverNewsRequestBudgetTest {

    private static final ExternalProviderProperties.NaverNews PROPERTIES =
            new ExternalProviderProperties.NaverNews(
                    URI.create("https://openapi.naver.com"),
                    "client",
                    "secret",
                    Duration.ofMinutes(10),
                    1_024,
                    25_000,
                    5_000);
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-24T03:00:00Z"), ZoneOffset.UTC);

    @Test
    void consumesDistributedBudgetWithKoreaDateKey() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), any(), any())).thenReturn(1L);
        NaverNewsRequestBudget budget = new NaverNewsRequestBudget(redisTemplate, PROPERTIES, CLOCK);

        budget.consumeOrThrow();

        verify(redisTemplate).execute(
                any(),
                eq(List.of("omni-connect:provider:naver-news:daily:2026-07-24")),
                eq("20000"),
                eq("1784905200000"));
    }

    @Test
    void rejectsRequestWithoutCallingProviderAfterBudgetIsExhausted() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), any(), any())).thenReturn(-1L);
        NaverNewsRequestBudget budget = new NaverNewsRequestBudget(redisTemplate, PROPERTIES, CLOCK);

        assertThatThrownBy(budget::consumeOrThrow)
                .isInstanceOf(NaverNewsDailyBudgetExceededException.class)
                .hasMessageContaining("20000");
    }

    @Test
    void failsClosedWhenDistributedBudgetStoreIsUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), any(), any())).thenReturn(null);
        NaverNewsRequestBudget budget = new NaverNewsRequestBudget(redisTemplate, PROPERTIES, CLOCK);

        assertThatThrownBy(budget::consumeOrThrow)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("budget store");
    }
}
