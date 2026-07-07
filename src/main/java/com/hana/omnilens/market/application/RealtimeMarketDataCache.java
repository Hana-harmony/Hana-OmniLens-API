package com.hana.omnilens.market.application;

import java.util.Optional;
import java.util.List;

import com.hana.omnilens.provider.market.KisRealtimeOrderBookSnapshot;
import com.hana.omnilens.provider.market.KisRealtimeIndexTick;
import com.hana.omnilens.provider.market.KisRealtimeTradeTick;

public interface RealtimeMarketDataCache {

    Optional<KisRealtimeTradeTick> latestTrade(String stockCode);

    List<KisRealtimeTradeTick> latestTrades();

    Optional<KisRealtimeOrderBookSnapshot> latestOrderBook(String stockCode);

    List<KisRealtimeIndexTick> latestIndices();

    void putTrade(KisRealtimeTradeTick tradeTick);

    void putOrderBook(KisRealtimeOrderBookSnapshot orderBookSnapshot);

    void putIndex(KisRealtimeIndexTick indexTick);

    void clear();
}
