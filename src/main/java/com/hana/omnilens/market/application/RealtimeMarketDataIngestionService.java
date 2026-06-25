package com.hana.omnilens.market.application;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hana.omnilens.provider.market.KisRealtimeMessageParser;
import com.hana.omnilens.provider.market.KisRealtimeOrderBookSnapshot;
import com.hana.omnilens.provider.market.KisRealtimeIndexTick;
import com.hana.omnilens.provider.market.KisRealtimeTradeTick;
import com.hana.omnilens.market.domain.MarketIndexQuote;
import com.hana.omnilens.market.stream.MarketIndexStreamingService;
import com.hana.omnilens.market.stream.MarketQuoteStreamingService;

@Service
public class RealtimeMarketDataIngestionService {

    private final KisRealtimeMessageParser kisRealtimeMessageParser;
    private final RealtimeMarketDataCache realtimeMarketDataCache;
    private final MarketQuoteStreamingService marketQuoteStreamingService;
    private final MarketIndexStreamingService marketIndexStreamingService;

    RealtimeMarketDataIngestionService(
            KisRealtimeMessageParser kisRealtimeMessageParser,
            RealtimeMarketDataCache realtimeMarketDataCache,
            MarketQuoteStreamingService marketQuoteStreamingService) {
        this(kisRealtimeMessageParser, realtimeMarketDataCache, marketQuoteStreamingService, null);
    }

    @Autowired
    public RealtimeMarketDataIngestionService(
            KisRealtimeMessageParser kisRealtimeMessageParser,
            RealtimeMarketDataCache realtimeMarketDataCache,
            MarketQuoteStreamingService marketQuoteStreamingService,
            MarketIndexStreamingService marketIndexStreamingService) {
        this.kisRealtimeMessageParser = kisRealtimeMessageParser;
        this.realtimeMarketDataCache = realtimeMarketDataCache;
        this.marketQuoteStreamingService = marketQuoteStreamingService;
        this.marketIndexStreamingService = marketIndexStreamingService;
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

        Optional<KisRealtimeIndexTick> indexTick = kisRealtimeMessageParser.parseIndexTick(rawMessage);
        if (indexTick.isPresent()) {
            KisRealtimeIndexTick tick = indexTick.orElseThrow();
            realtimeMarketDataCache.putIndex(tick);
            if (marketIndexStreamingService != null) {
                marketIndexStreamingService.publish(new MarketIndexQuote(
                        tick.indexCode(),
                        tick.indexName(),
                        tick.market(),
                        tick.currentValue(),
                        tick.changeSign(),
                        tick.changeValue(),
                        tick.changeRate(),
                        tick.accumulatedVolume(),
                        tick.accumulatedTradingValue(),
                        tick.openValue(),
                        tick.highValue(),
                        tick.lowValue(),
                        tick.marketDataTime(),
                        tick.source()));
            }
            return RealtimeMarketDataIngestionResult.index(tick.indexCode());
        }

        return RealtimeMarketDataIngestionResult.ignored();
    }
}
