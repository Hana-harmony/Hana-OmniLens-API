package com.hana.omnilens.market.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.hana.omnilens.provider.market.KisRealtimeMessageParser;
import com.hana.omnilens.provider.market.KisRealtimeOrderBookSnapshot;
import com.hana.omnilens.provider.market.KisRealtimeIndexTick;
import com.hana.omnilens.provider.market.KisRealtimeTradeTick;
import com.hana.omnilens.market.domain.MarketIndexQuote;
import com.hana.omnilens.market.domain.MarketIntradayRealtimeTick;
import com.hana.omnilens.market.stream.MarketIndexStreamingService;
import com.hana.omnilens.market.stream.MarketQuoteStreamingService;

@Service
public class RealtimeMarketDataIngestionService {

    private static final Logger log = LoggerFactory.getLogger(RealtimeMarketDataIngestionService.class);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter KIS_TIME = DateTimeFormatter.ofPattern("HHmmss");
    private static final String REALTIME_TRADE_SOURCE = "KIS_REALTIME_TRADE";

    private final KisRealtimeMessageParser kisRealtimeMessageParser;
    private final RealtimeMarketDataCache realtimeMarketDataCache;
    private final MarketQuoteStreamingService marketQuoteStreamingService;
    private final MarketIndexStreamingService marketIndexStreamingService;
    private final MarketIntradayPriceRepository marketIntradayPriceRepository;
    private final StockMasterRepository stockMasterRepository;
    private final Clock clock;

    RealtimeMarketDataIngestionService(
            KisRealtimeMessageParser kisRealtimeMessageParser,
            RealtimeMarketDataCache realtimeMarketDataCache,
            MarketQuoteStreamingService marketQuoteStreamingService) {
        this(
                kisRealtimeMessageParser,
                realtimeMarketDataCache,
                marketQuoteStreamingService,
                null,
                null,
                null,
                Clock.system(KOREA_ZONE));
    }

    @Autowired
    public RealtimeMarketDataIngestionService(
            KisRealtimeMessageParser kisRealtimeMessageParser,
            RealtimeMarketDataCache realtimeMarketDataCache,
            MarketQuoteStreamingService marketQuoteStreamingService,
            MarketIndexStreamingService marketIndexStreamingService,
            MarketIntradayPriceRepository marketIntradayPriceRepository,
            StockMasterRepository stockMasterRepository) {
        this(
                kisRealtimeMessageParser,
                realtimeMarketDataCache,
                marketQuoteStreamingService,
                marketIndexStreamingService,
                marketIntradayPriceRepository,
                stockMasterRepository,
                Clock.system(KOREA_ZONE));
    }

    RealtimeMarketDataIngestionService(
            KisRealtimeMessageParser kisRealtimeMessageParser,
            RealtimeMarketDataCache realtimeMarketDataCache,
            MarketQuoteStreamingService marketQuoteStreamingService,
            MarketIndexStreamingService marketIndexStreamingService,
            MarketIntradayPriceRepository marketIntradayPriceRepository,
            StockMasterRepository stockMasterRepository,
            Clock clock) {
        this.kisRealtimeMessageParser = kisRealtimeMessageParser;
        this.realtimeMarketDataCache = realtimeMarketDataCache;
        this.marketQuoteStreamingService = marketQuoteStreamingService;
        this.marketIndexStreamingService = marketIndexStreamingService;
        this.marketIntradayPriceRepository = marketIntradayPriceRepository;
        this.stockMasterRepository = stockMasterRepository;
        this.clock = clock;
    }

    public RealtimeMarketDataIngestionResult ingestKisMessage(String rawMessage) {
        Optional<KisRealtimeTradeTick> tradeTick = kisRealtimeMessageParser.parseTradeTick(rawMessage);
        if (tradeTick.isPresent()) {
            KisRealtimeTradeTick tick = tradeTick.orElseThrow();
            realtimeMarketDataCache.putTrade(tick);
            recordRealtimeMinutePrice(tick);
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

    private void recordRealtimeMinutePrice(KisRealtimeTradeTick tick) {
        if (marketIntradayPriceRepository == null || stockMasterRepository == null || tick.afterHours()) {
            return;
        }
        try {
            long executionVolume = Math.abs(tick.executionVolume());
            if (executionVolume == 0 || tick.currentPriceKrw() == null) {
                return;
            }
            String market = stockMasterRepository.findByCode(tick.stockCode())
                    .map(com.hana.omnilens.market.domain.StockSummary::market)
                    .orElse("UNKNOWN");
            marketIntradayPriceRepository.recordRealtimeTick(new MarketIntradayRealtimeTick(
                    tick.stockCode(),
                    bucketStart(tick),
                    market,
                    tick.currentPriceKrw(),
                    executionVolume,
                    tick.currentPriceKrw().multiply(BigDecimal.valueOf(executionVolume)),
                    REALTIME_TRADE_SOURCE,
                    Instant.now(clock)));
        } catch (RuntimeException exception) {
            // 실시간 표시가 DB 저장 장애 때문에 중단되지 않게 한다.
            log.warn("Realtime minute candle persistence failed stockCode={}", tick.stockCode(), exception);
        }
    }

    private LocalDateTime bucketStart(KisRealtimeTradeTick tick) {
        LocalDate businessDate = tick.businessDate() == null ? LocalDate.now(clock) : tick.businessDate();
        String normalizedTime = normalizeTime(tick.tradeTime());
        LocalTime tradeTime = LocalTime.parse(normalizedTime, KIS_TIME).withSecond(0);
        return LocalDateTime.of(businessDate, tradeTime);
    }

    private String normalizeTime(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\D", "");
        if (normalized.length() >= 6) {
            return normalized.substring(0, 6);
        }
        return KIS_TIME.format(LocalTime.now(clock));
    }
}
