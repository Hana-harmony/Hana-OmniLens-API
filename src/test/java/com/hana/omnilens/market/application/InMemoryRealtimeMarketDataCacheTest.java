package com.hana.omnilens.market.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.hana.omnilens.provider.market.KisRealtimeTradeTick;

class InMemoryRealtimeMarketDataCacheTest {

    @Test
    void latestTradeKeepsRepeatedTickAvailableForDisplayFallback() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-24T04:00:00Z"));
        InMemoryRealtimeMarketDataCache cache = new InMemoryRealtimeMarketDataCache(clock);

        cache.putTrade(tick("005930", "322750", 100, 1_000_000));
        clock.now = clock.now.plusSeconds(3);
        cache.putTrade(tick("005930", "322750", 100, 1_000_000));
        clock.now = clock.now.plusSeconds(120);

        assertThat(cache.latestTrade("005930")).isPresent();
        assertThat(cache.latestTrade("005930").orElseThrow().currentPriceKrw()).isEqualByComparingTo("322750");
    }

    @Test
    void latestTradeStaysFreshWhenPriceChanges() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-24T04:00:00Z"));
        InMemoryRealtimeMarketDataCache cache = new InMemoryRealtimeMarketDataCache(clock);

        cache.putTrade(tick("005930", "322750", 100, 1_000_000));
        clock.now = clock.now.plusSeconds(3);
        cache.putTrade(tick("005930", "323000", 100, 1_000_000));
        clock.now = clock.now.plusSeconds(3);

        assertThat(cache.latestTrade("005930")).isPresent();
        assertThat(cache.latestTrade("005930").orElseThrow().currentPriceKrw()).isEqualByComparingTo("323000");
    }

    @Test
    void latestTradesReturnsRecentlyUpdatedTicksFirst() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-24T04:00:00Z"));
        InMemoryRealtimeMarketDataCache cache = new InMemoryRealtimeMarketDataCache(clock);

        cache.putTrade(tick("005930", "322750", 100, 1_000_000));
        clock.now = clock.now.plusSeconds(1);
        cache.putTrade(tick("000660", "510000", 100, 1_000_000));

        assertThat(cache.latestTrades())
                .extracting(KisRealtimeTradeTick::stockCode)
                .containsExactly("000660", "005930");
    }

    private KisRealtimeTradeTick tick(String stockCode, String price, long executionVolume, long accumulatedVolume) {
        return new KisRealtimeTradeTick(
                stockCode,
                "130000",
                new BigDecimal(price),
                new BigDecimal("1.23"),
                new BigDecimal("323500"),
                new BigDecimal("322500"),
                executionVolume,
                accumulatedVolume,
                LocalDate.parse("2026-06-24"));
    }

    private static class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
