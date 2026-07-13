package com.hana.omnilens.market.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
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
import com.hana.omnilens.provider.market.KisRealtimeMarketStatus;
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
    private static final String REALTIME_INDEX_SOURCE = "KIS_REAL_INDEX_REALTIME";
    private static final LocalTime REGULAR_MARKET_OPEN = LocalTime.of(9, 0);
    private static final LocalTime REGULAR_MARKET_CLOSE = LocalTime.of(15, 30);
    private static final Duration INDEX_TICK_FUTURE_TOLERANCE = Duration.ofMinutes(2);

    private final KisRealtimeMessageParser kisRealtimeMessageParser;
    private final RealtimeMarketDataCache realtimeMarketDataCache;
    private final MarketQuoteStreamingService marketQuoteStreamingService;
    private final MarketIndexStreamingService marketIndexStreamingService;
    private final MarketIntradayPriceRepository marketIntradayPriceRepository;
    private final MarketIndexSnapshotRepository marketIndexSnapshotRepository;
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
            MarketIndexSnapshotRepository marketIndexSnapshotRepository,
            StockMasterRepository stockMasterRepository) {
        this(
                kisRealtimeMessageParser,
                realtimeMarketDataCache,
                marketQuoteStreamingService,
                marketIndexStreamingService,
                marketIntradayPriceRepository,
                marketIndexSnapshotRepository,
                stockMasterRepository,
                Clock.system(KOREA_ZONE));
    }

    RealtimeMarketDataIngestionService(
            KisRealtimeMessageParser kisRealtimeMessageParser,
            RealtimeMarketDataCache realtimeMarketDataCache,
            MarketQuoteStreamingService marketQuoteStreamingService,
            MarketIndexStreamingService marketIndexStreamingService,
            MarketIntradayPriceRepository marketIntradayPriceRepository,
            MarketIndexSnapshotRepository marketIndexSnapshotRepository,
            StockMasterRepository stockMasterRepository,
            Clock clock) {
        this.kisRealtimeMessageParser = kisRealtimeMessageParser;
        this.realtimeMarketDataCache = realtimeMarketDataCache;
        this.marketQuoteStreamingService = marketQuoteStreamingService;
        this.marketIndexStreamingService = marketIndexStreamingService;
        this.marketIntradayPriceRepository = marketIntradayPriceRepository;
        this.marketIndexSnapshotRepository = marketIndexSnapshotRepository;
        this.stockMasterRepository = stockMasterRepository;
        this.clock = clock;
    }

    RealtimeMarketDataIngestionService(
            KisRealtimeMessageParser kisRealtimeMessageParser,
            RealtimeMarketDataCache realtimeMarketDataCache,
            MarketQuoteStreamingService marketQuoteStreamingService,
            MarketIndexStreamingService marketIndexStreamingService,
            MarketIntradayPriceRepository marketIntradayPriceRepository,
            StockMasterRepository stockMasterRepository,
            Clock clock) {
        this(
                kisRealtimeMessageParser,
                realtimeMarketDataCache,
                marketQuoteStreamingService,
                marketIndexStreamingService,
                marketIntradayPriceRepository,
                null,
                stockMasterRepository,
                clock);
    }

    public RealtimeMarketDataIngestionResult ingestKisMessage(String rawMessage) {
        Optional<KisRealtimeMarketStatus> marketStatus = kisRealtimeMessageParser.parseMarketStatus(rawMessage);
        if (marketStatus.isPresent()) {
            KisRealtimeMarketStatus status = marketStatus.orElseThrow();
            realtimeMarketDataCache.putMarketStatus(status);
            // 거래가 멈춘 동안에도 상태 변경을 즉시 화면에 전달한다.
            marketQuoteStreamingService.publishTick(status.stockCode(), "USD");
            return RealtimeMarketDataIngestionResult.marketStatus(status.stockCode());
        }

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
            if (!isUsableRegularSessionIndexTick(tick)) {
                log.debug(
                        "Realtime index tick ignored indexCode={} tradeTime={} marketDataTime={}",
                        tick.indexCode(),
                        tick.tradeTime(),
                        tick.marketDataTime());
                return RealtimeMarketDataIngestionResult.ignored();
            }
            realtimeMarketDataCache.putIndex(tick);
            MarketIndexQuote quote = new MarketIndexQuote(
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
                    tick.source());
            recordRealtimeIndex(quote);
            if (marketIndexStreamingService != null) {
                marketIndexStreamingService.publish(quote);
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

    private void recordRealtimeIndex(MarketIndexQuote quote) {
        if (marketIndexSnapshotRepository == null || quote.marketDataTime() == null) {
            return;
        }
        try {
            marketIndexSnapshotRepository.recordLatest(quote);
            marketIndexSnapshotRepository.recordRealtimeMinute(new com.hana.omnilens.market.domain.MarketIndexIntradayPrice(
                    quote.indexCode(),
                    quote.indexName(),
                    quote.market(),
                    indexBucketStart(quote),
                    quote.openValue(),
                    quote.highValue(),
                    quote.lowValue(),
                    quote.currentValue(),
                    quote.accumulatedVolume(),
                    BigDecimal.valueOf(quote.accumulatedTradingValue()),
                    REALTIME_INDEX_SOURCE,
                    Instant.now(clock)));
        } catch (RuntimeException exception) {
            // 지수 화면은 실시간 전송을 우선하고 저장 장애는 다음 tick에서 복구한다.
            log.warn("Realtime index persistence failed indexCode={}", quote.indexCode(), exception);
        }
    }

    private LocalDateTime indexBucketStart(MarketIndexQuote quote) {
        return LocalDateTime.ofInstant(quote.marketDataTime(), KOREA_ZONE)
                .withSecond(0)
                .withNano(0);
    }

    private boolean isUsableRegularSessionIndexTick(KisRealtimeIndexTick tick) {
        if (tick.marketDataTime() == null || tick.currentValue() == null) {
            return false;
        }
        if (!MarketIndexSanityPolicy.isPlausibleCurrentValue(tick.indexCode(), tick.currentValue())) {
            return false;
        }
        Instant receivedAt = Instant.now(clock);
        if (tick.marketDataTime().isAfter(receivedAt.plus(INDEX_TICK_FUTURE_TOLERANCE))) {
            return false;
        }
        LocalTime tradeTime = LocalDateTime.ofInstant(tick.marketDataTime(), KOREA_ZONE).toLocalTime();
        return !tradeTime.isBefore(REGULAR_MARKET_OPEN) && !tradeTime.isAfter(REGULAR_MARKET_CLOSE);
    }

    private String normalizeTime(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\D", "");
        if (normalized.length() >= 6) {
            return normalized.substring(0, 6);
        }
        return KIS_TIME.format(LocalTime.now(clock));
    }
}
