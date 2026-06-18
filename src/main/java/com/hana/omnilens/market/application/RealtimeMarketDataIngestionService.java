package com.hana.omnilens.market.application;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.hana.omnilens.provider.market.KisRealtimeMessageParser;
import com.hana.omnilens.provider.market.KisRealtimeOrderBookSnapshot;
import com.hana.omnilens.provider.market.KisRealtimeTradeTick;

@Service
public class RealtimeMarketDataIngestionService {

    private final KisRealtimeMessageParser kisRealtimeMessageParser;
    private final RealtimeMarketDataCache realtimeMarketDataCache;

    public RealtimeMarketDataIngestionService(
            KisRealtimeMessageParser kisRealtimeMessageParser,
            RealtimeMarketDataCache realtimeMarketDataCache) {
        this.kisRealtimeMessageParser = kisRealtimeMessageParser;
        this.realtimeMarketDataCache = realtimeMarketDataCache;
    }

    public RealtimeMarketDataIngestionResult ingestKisMessage(String rawMessage) {
        Optional<KisRealtimeTradeTick> tradeTick = kisRealtimeMessageParser.parseTradeTick(rawMessage);
        if (tradeTick.isPresent()) {
            KisRealtimeTradeTick tick = tradeTick.orElseThrow();
            realtimeMarketDataCache.putTrade(tick);
            return RealtimeMarketDataIngestionResult.trade(tick.stockCode());
        }

        Optional<KisRealtimeOrderBookSnapshot> orderBook = kisRealtimeMessageParser.parseOrderBook(rawMessage);
        if (orderBook.isPresent()) {
            KisRealtimeOrderBookSnapshot snapshot = orderBook.orElseThrow();
            realtimeMarketDataCache.putOrderBook(snapshot);
            return RealtimeMarketDataIngestionResult.orderBook(snapshot.stockCode());
        }

        return RealtimeMarketDataIngestionResult.ignored();
    }
}
