package com.hana.omnilens.market.application;

import java.util.Optional;

import com.hana.omnilens.provider.market.KisRealtimeOrderBookSnapshot;
import com.hana.omnilens.provider.market.KisRealtimeTradeTick;

public interface RealtimeMarketDataCache {

    Optional<KisRealtimeTradeTick> latestTrade(String stockCode);

    Optional<KisRealtimeOrderBookSnapshot> latestOrderBook(String stockCode);

    void putTrade(KisRealtimeTradeTick tradeTick);

    void putOrderBook(KisRealtimeOrderBookSnapshot orderBookSnapshot);
}
