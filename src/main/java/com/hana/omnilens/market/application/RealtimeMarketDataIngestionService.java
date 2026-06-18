package com.hana.omnilens.market.application;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.hana.omnilens.provider.market.KisRealtimeMessageParser;
import com.hana.omnilens.provider.market.KisRealtimeOrderBookSnapshot;
import com.hana.omnilens.provider.market.KisRealtimeTradeTick;
import com.hana.omnilens.market.stream.MarketQuoteStreamingService;

@Service
public class RealtimeMarketDataIngestionService {

    private final KisRealtimeMessageParser kisRealtimeMessageParser;
    private final RealtimeMarketDataCache realtimeMarketDataCache;
    private final MarketQuoteStreamingService marketQuoteStreamingService;

    public RealtimeMarketDataIngestionService(
            KisRealtimeMessageParser kisRealtimeMessageParser,
            RealtimeMarketDataCache realtimeMarketDataCache,
            MarketQuoteStreamingService marketQuoteStreamingService) {
        this.kisRealtimeMessageParser = kisRealtimeMessageParser;
        this.realtimeMarketDataCache = realtimeMarketDataCache;
        this.marketQuoteStreamingService = marketQuoteStreamingService;
    }

    public RealtimeMarketDataIngestionResult ingestKisMessage(String rawMessage) {
        Optional<KisRealtimeTradeTick> tradeTick = kisRealtimeMessageParser.parseTradeTick(rawMessage);
        if (tradeTick.isPresent()) {
            KisRealtimeTradeTick tick = tradeTick.orElseThrow();
            realtimeMarketDataCache.putTrade(tick);
            marketQuoteStreamingService.publishTick(tick.stockCode(), "USD");
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
