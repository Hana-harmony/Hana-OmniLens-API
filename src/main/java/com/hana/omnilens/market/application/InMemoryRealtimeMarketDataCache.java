package com.hana.omnilens.market.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.hana.omnilens.provider.market.KisRealtimeOrderBookSnapshot;
import com.hana.omnilens.provider.market.KisRealtimeTradeTick;

@Component
public class InMemoryRealtimeMarketDataCache implements RealtimeMarketDataCache {

    private final Map<String, CachedTradeTick> tradeTicks = new ConcurrentHashMap<>();
    private final Map<String, KisRealtimeOrderBookSnapshot> orderBooks = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRealtimeMarketDataCache() {
        this(Clock.systemUTC());
    }

    InMemoryRealtimeMarketDataCache(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<KisRealtimeTradeTick> latestTrade(String stockCode) {
        CachedTradeTick cached = tradeTicks.get(stockCode);
        if (cached == null) {
            return Optional.empty();
        }
        return Optional.of(cached.tick());
    }

    @Override
    public Optional<KisRealtimeOrderBookSnapshot> latestOrderBook(String stockCode) {
        return Optional.ofNullable(orderBooks.get(stockCode));
    }

    @Override
    public void putTrade(KisRealtimeTradeTick tradeTick) {
        tradeTicks.compute(tradeTick.stockCode(), (stockCode, cached) -> {
            if (cached != null && sameTrade(cached.tick(), tradeTick)) {
                // 반복 tick도 화면 표시용 최신 시세로 유지한다.
                return cached;
            }
            return new CachedTradeTick(tradeTick, Instant.now(clock));
        });
    }

    @Override
    public void putOrderBook(KisRealtimeOrderBookSnapshot orderBookSnapshot) {
        orderBooks.put(orderBookSnapshot.stockCode(), orderBookSnapshot);
    }

    private boolean sameTrade(KisRealtimeTradeTick left, KisRealtimeTradeTick right) {
        return left.currentPriceKrw().compareTo(right.currentPriceKrw()) == 0
                && left.accumulatedVolume() == right.accumulatedVolume()
                && left.executionVolume() == right.executionVolume();
    }

    private record CachedTradeTick(KisRealtimeTradeTick tick, Instant updatedAt) {
    }
}
