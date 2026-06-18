package com.hana.omnilens.market.application;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.hana.omnilens.provider.market.KisRealtimeOrderBookSnapshot;
import com.hana.omnilens.provider.market.KisRealtimeTradeTick;

@Component
public class InMemoryRealtimeMarketDataCache implements RealtimeMarketDataCache {

    private final Map<String, KisRealtimeTradeTick> tradeTicks = new ConcurrentHashMap<>();
    private final Map<String, KisRealtimeOrderBookSnapshot> orderBooks = new ConcurrentHashMap<>();

    @Override
    public Optional<KisRealtimeTradeTick> latestTrade(String stockCode) {
        return Optional.ofNullable(tradeTicks.get(stockCode));
    }

    @Override
    public Optional<KisRealtimeOrderBookSnapshot> latestOrderBook(String stockCode) {
        return Optional.ofNullable(orderBooks.get(stockCode));
    }

    @Override
    public void putTrade(KisRealtimeTradeTick tradeTick) {
        tradeTicks.put(tradeTick.stockCode(), tradeTick);
    }

    @Override
    public void putOrderBook(KisRealtimeOrderBookSnapshot orderBookSnapshot) {
        orderBooks.put(orderBookSnapshot.stockCode(), orderBookSnapshot);
    }
}
