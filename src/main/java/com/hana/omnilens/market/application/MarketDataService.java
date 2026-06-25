package com.hana.omnilens.market.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import com.hana.omnilens.market.domain.ForeignOwnershipPrediction;
import com.hana.omnilens.market.domain.ForeignOwnershipDailySnapshot;
import com.hana.omnilens.market.domain.MarketIndexQuote;
import com.hana.omnilens.market.domain.MarketQuote;
import com.hana.omnilens.market.domain.Orderability;
import com.hana.omnilens.market.domain.OrderBook;
import com.hana.omnilens.market.domain.StockDetail;
import com.hana.omnilens.market.domain.StockSummary;
import com.hana.omnilens.provider.ProviderCircuitOpenException;
import com.hana.omnilens.provider.ai.HannahAiForeignOwnershipHistoryPoint;
import com.hana.omnilens.provider.ai.HannahAiForeignOwnershipPredictionClient;
import com.hana.omnilens.provider.ai.HannahAiForeignOwnershipPredictionRequest;
import com.hana.omnilens.provider.ai.HannahAiForeignOwnershipPredictionResponse;
import com.hana.omnilens.provider.market.ForeignOwnershipSnapshot;
import com.hana.omnilens.provider.market.KisCurrentPriceClient;
import com.hana.omnilens.provider.market.KisCurrentPriceSnapshot;
import com.hana.omnilens.provider.market.KisRealtimeOrderBookSnapshot;
import com.hana.omnilens.provider.market.KisRealtimeTradeTick;
import com.hana.omnilens.provider.market.KisRestOrderBookClient;
import com.hana.omnilens.provider.market.KisRestOrderBookSnapshot;
import com.hana.omnilens.provider.market.PublicDataStockPriceSnapshot;
import com.hana.omnilens.provider.market.PublicDataStockSecuritiesClient;

@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);
    private static final BigDecimal FOREIGN_LIMIT_BLOCK_RATE = new BigDecimal("100.0000");
    private static final Duration PRICE_CACHE_TTL = Duration.ofSeconds(2);
    private static final Duration PRICE_CACHE_STALE_TTL = Duration.ofSeconds(30);
    private static final Duration KIS_RATE_LIMIT_RETRY_DELAY = Duration.ofMillis(1_200);
    private static final int KIS_RATE_LIMIT_RETRY_MAX_ATTEMPTS = 3;
    private static final int FOREIGN_OWNERSHIP_HISTORY_LIMIT = 30;
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final PublicDataStockSecuritiesClient publicDataStockSecuritiesClient;
    private final KisCurrentPriceClient kisCurrentPriceClient;
    private final KisRestOrderBookClient kisRestOrderBookClient;
    private final StockMasterRepository stockMasterRepository;
    private final ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache;
    private final ForeignOwnershipDailySnapshotRepository foreignOwnershipDailySnapshotRepository;
    private final ExchangeRateCache exchangeRateCache;
    private final RealtimeMarketDataCache realtimeMarketDataCache;
    private final HannahAiForeignOwnershipPredictionClient hannahAiForeignOwnershipPredictionClient;
    private final ForeignOwnershipPredictionEngine foreignOwnershipPredictionEngine;
    private final Clock clock;
    private final Map<String, CachedPriceLookup> priceLookupCache = new ConcurrentHashMap<>();

    @Autowired
    public MarketDataService(
            PublicDataStockSecuritiesClient publicDataStockSecuritiesClient,
            KisCurrentPriceClient kisCurrentPriceClient,
            KisRestOrderBookClient kisRestOrderBookClient,
            StockMasterRepository stockMasterRepository,
            ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache,
            ForeignOwnershipDailySnapshotRepository foreignOwnershipDailySnapshotRepository,
            ExchangeRateCache exchangeRateCache,
            RealtimeMarketDataCache realtimeMarketDataCache,
            HannahAiForeignOwnershipPredictionClient hannahAiForeignOwnershipPredictionClient,
            ForeignOwnershipPredictionEngine foreignOwnershipPredictionEngine) {
        this(
                publicDataStockSecuritiesClient,
                kisCurrentPriceClient,
                kisRestOrderBookClient,
                stockMasterRepository,
                foreignOwnershipSnapshotCache,
                foreignOwnershipDailySnapshotRepository,
                exchangeRateCache,
                realtimeMarketDataCache,
                hannahAiForeignOwnershipPredictionClient,
                foreignOwnershipPredictionEngine,
                Clock.system(KOREA_ZONE));
    }

    MarketDataService(
            PublicDataStockSecuritiesClient publicDataStockSecuritiesClient,
            KisCurrentPriceClient kisCurrentPriceClient,
            StockMasterRepository stockMasterRepository,
            ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache,
            ExchangeRateCache exchangeRateCache,
            RealtimeMarketDataCache realtimeMarketDataCache,
            Clock clock) {
        this(
                publicDataStockSecuritiesClient,
                kisCurrentPriceClient,
                null,
                stockMasterRepository,
                foreignOwnershipSnapshotCache,
                new InMemoryForeignOwnershipDailySnapshotRepository(),
                exchangeRateCache,
                realtimeMarketDataCache,
                null,
                new ForeignOwnershipPredictionEngine(clock),
                clock);
    }

    MarketDataService(
            PublicDataStockSecuritiesClient publicDataStockSecuritiesClient,
            KisCurrentPriceClient kisCurrentPriceClient,
            KisRestOrderBookClient kisRestOrderBookClient,
            StockMasterRepository stockMasterRepository,
            ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache,
            ExchangeRateCache exchangeRateCache,
            RealtimeMarketDataCache realtimeMarketDataCache,
            Clock clock) {
        this(
                publicDataStockSecuritiesClient,
                kisCurrentPriceClient,
                kisRestOrderBookClient,
                stockMasterRepository,
                foreignOwnershipSnapshotCache,
                new InMemoryForeignOwnershipDailySnapshotRepository(),
                exchangeRateCache,
                realtimeMarketDataCache,
                null,
                new ForeignOwnershipPredictionEngine(clock),
                clock);
    }

    MarketDataService(
            PublicDataStockSecuritiesClient publicDataStockSecuritiesClient,
            KisCurrentPriceClient kisCurrentPriceClient,
            StockMasterRepository stockMasterRepository,
            ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache,
            ExchangeRateCache exchangeRateCache,
            RealtimeMarketDataCache realtimeMarketDataCache,
            ForeignOwnershipPredictionEngine foreignOwnershipPredictionEngine,
            Clock clock) {
        this(
                publicDataStockSecuritiesClient,
                kisCurrentPriceClient,
                null,
                stockMasterRepository,
                foreignOwnershipSnapshotCache,
                new InMemoryForeignOwnershipDailySnapshotRepository(),
                exchangeRateCache,
                realtimeMarketDataCache,
                null,
                foreignOwnershipPredictionEngine,
                clock);
    }

    MarketDataService(
            PublicDataStockSecuritiesClient publicDataStockSecuritiesClient,
            KisCurrentPriceClient kisCurrentPriceClient,
            KisRestOrderBookClient kisRestOrderBookClient,
            StockMasterRepository stockMasterRepository,
            ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache,
            ForeignOwnershipDailySnapshotRepository foreignOwnershipDailySnapshotRepository,
            ExchangeRateCache exchangeRateCache,
            RealtimeMarketDataCache realtimeMarketDataCache,
            HannahAiForeignOwnershipPredictionClient hannahAiForeignOwnershipPredictionClient,
            ForeignOwnershipPredictionEngine foreignOwnershipPredictionEngine,
            Clock clock) {
        this.publicDataStockSecuritiesClient = publicDataStockSecuritiesClient;
        this.kisCurrentPriceClient = kisCurrentPriceClient;
        this.kisRestOrderBookClient = kisRestOrderBookClient;
        this.stockMasterRepository = stockMasterRepository;
        this.foreignOwnershipSnapshotCache = foreignOwnershipSnapshotCache;
        this.foreignOwnershipDailySnapshotRepository = foreignOwnershipDailySnapshotRepository;
        this.exchangeRateCache = exchangeRateCache;
        this.realtimeMarketDataCache = realtimeMarketDataCache;
        this.hannahAiForeignOwnershipPredictionClient = hannahAiForeignOwnershipPredictionClient;
        this.foreignOwnershipPredictionEngine = foreignOwnershipPredictionEngine;
        this.clock = clock;
    }

    public MarketQuote getQuote(String stockCode, String localCurrency, BigDecimal fxRate) {
        PriceLookup priceLookup = latestPriceSnapshot(stockCode);
        StockSummary stock = getStock(stockCode);
        ForeignOwnershipLookup foreignOwnership = latestForeignOwnershipSnapshot(stock);
        Optional<KisRealtimeTradeTick> afterHoursTrade = realtimeMarketDataCache.latestTrade(stockCode)
                .filter(KisRealtimeTradeTick::afterHours);

        BigDecimal currentPrice = priceLookup.currentPriceKrw()
                .orElseThrow(() -> new MarketDataUnavailableException(
                        "No live provider price is available for stockCode=" + stockCode));
        ForeignOwnershipSnapshot ownership = foreignOwnership.snapshot()
                .orElseThrow(() -> new MarketDataUnavailableException(
                        "No KIS foreign ownership snapshot is available for stockCode=" + stockCode));
        FxLookup fxLookup = resolveFxRate(localCurrency, fxRate);
        BigDecimal localPrice = currentPrice.multiply(fxLookup.fxRate()).setScale(4, RoundingMode.HALF_UP);
        BigDecimal afterHoursPriceKrw = afterHoursTrade.map(KisRealtimeTradeTick::currentPriceKrw).orElse(null);
        BigDecimal afterHoursLocalCurrencyPrice = afterHoursPriceKrw == null
                ? null
                : afterHoursPriceKrw.multiply(fxLookup.fxRate()).setScale(4, RoundingMode.HALF_UP);

        return new MarketQuote(
                stockCode,
                priceLookup.stockName().orElse(stock.stockName()),
                stock.stockNameEn(),
                priceLookup.market().orElse(stock.market()),
                currentPrice,
                priceLookup.changeRate().orElse(new BigDecimal("1.42")),
                priceLookup.volume().orElse(12193000L),
                currentPrice,
                afterHoursTrade.map(KisRealtimeTradeTick::marketSession).orElse(priceLookup.marketSession()),
                afterHoursPriceKrw,
                afterHoursLocalCurrencyPrice,
                afterHoursTrade.map(KisRealtimeTradeTick::changeRate).orElse(null),
                afterHoursTrade.map(KisRealtimeTradeTick::accumulatedVolume).orElse(null),
                afterHoursTrade.map(tick -> Instant.now(clock)).orElse(null),
                "KRW",
                localPrice,
                localCurrency,
                fxLookup.fxRate(),
                fxLookup.fxRateTime(),
                fxLookup.fxRateSource(),
                fxLookup.stale(),
                ownership.foreignOwnedQuantity(),
                ownership.foreignOwnershipRate(),
                ownership.foreignLimitExhaustionRate(),
                ownership.baseDate(),
                Instant.now(clock),
                source(priceLookup.source(), foreignOwnership.source()))
        ;
    }

    public StockDetail getStockDetail(String stockCode, String localCurrency, BigDecimal fxRate) {
        MarketQuote quote = getQuote(stockCode, localCurrency, fxRate);
        Orderability orderability = getOrderability(stockCode, "BUY", 1);
        ForeignOwnershipPrediction prediction = orderability.foreignOwnershipPrediction();
        return new StockDetail(
                quote.stockCode(),
                quote.stockName(),
                quote.stockNameEn(),
                quote.market(),
                null,
                quote.currentPriceKrw(),
                quote.changeRate(),
                quote.volume(),
                quote.localCurrency(),
                quote.localCurrencyPrice(),
                quote.marketDataTime(),
                quote.foreignOwnedQuantity(),
                quote.foreignOwnershipRate(),
                quote.foreignLimitExhaustionRate(),
                quote.foreignOwnershipRate(),
                quote.foreignOwnershipRate(),
                prediction.minForeignLimitExhaustionRate(),
                prediction.maxForeignLimitExhaustionRate(),
                prediction.confidenceLevel(),
                prediction.confidenceScore(),
                prediction.modelVersion(),
                quote.foreignOwnershipBaseDate(),
                orderability.viActive(),
                orderability.singlePriceTrading(),
                orderability.priceLimitState(),
                orderability.tradingHalted(),
                orderability.orderable(),
                quote.source() + "+" + orderability.source());
    }

    public List<MarketQuote> getQuotes(
            List<String> stockCodes,
            String market,
            String localCurrency,
            BigDecimal fxRate,
            int limit) {
        List<String> resolvedStockCodes = resolveStockCodes(stockCodes);
        String normalizedMarket = normalizeMarket(market);
        if (resolvedStockCodes.isEmpty()) {
            return stockMasterRepository.findAll(limit).stream()
                    .filter(stock -> normalizedMarket == null || normalizedMarket.equals(stock.market()))
                    .map(stock -> getQuote(stock.stockCode(), localCurrency, fxRate))
                    .toList();
        }
        return resolvedStockCodes.stream()
                .map(stockCode -> getQuote(stockCode, localCurrency, fxRate))
                .filter(quote -> normalizedMarket == null || normalizedMarket.equals(quote.market()))
                .toList();
    }

    public List<MarketIndexQuote> getIndices() {
        return realtimeMarketDataCache.latestIndices().stream()
                .map(tick -> new MarketIndexQuote(
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
                        tick.source()))
                .toList();
    }

    public OrderBook getOrderBook(String stockCode) {
        Optional<KisRealtimeOrderBookSnapshot> realtimeOrderBook =
                realtimeMarketDataCache.latestOrderBook(stockCode);
        if (realtimeOrderBook.isPresent()) {
            KisRealtimeOrderBookSnapshot snapshot = realtimeOrderBook.orElseThrow();
            return new OrderBook(
                    stockCode,
                    snapshot.asks().stream()
                            .map(level -> new OrderBook.OrderBookLevel(level.priceKrw(), level.quantity()))
                            .toList(),
                    snapshot.bids().stream()
                            .map(level -> new OrderBook.OrderBookLevel(level.priceKrw(), level.quantity()))
                            .toList(),
                    Instant.now(clock),
                    "KIS_WEBSOCKET_ORDERBOOK");
        }
        Optional<OrderBook> restOrderBook = latestRestOrderBook(stockCode);
        if (restOrderBook.isPresent()) {
            return restOrderBook.orElseThrow();
        }
        throw new MarketDataUnavailableException("No KIS order book provider data is available for stockCode=" + stockCode);
    }

    private Optional<OrderBook> latestRestOrderBook(String stockCode) {
        if (kisRestOrderBookClient == null) {
            return Optional.empty();
        }
        try {
            return findKisRestOrderBook(stockCode)
                    .map(snapshot -> toOrderBook(stockCode, snapshot, "KIS_REST_ORDERBOOK"));
        } catch (RuntimeException exception) {
            // 실시간 cache가 없는 장외 상황에서도 호가 REST 장애만으로 시장 API 전체를 중단하지 않는다.
            log.warn("KIS REST orderbook lookup failed for stockCode={}: {}", stockCode, exception.toString());
            return Optional.empty();
        }
    }

    private Optional<KisRestOrderBookSnapshot> findKisRestOrderBook(String stockCode) {
        RuntimeException lastRateLimitException = null;
        for (int attempt = 1; attempt <= KIS_RATE_LIMIT_RETRY_MAX_ATTEMPTS; attempt++) {
            try {
                return kisRestOrderBookClient.findOrderBook(stockCode);
            } catch (RuntimeException exception) {
                if (!isKisRateLimitError(exception)) {
                    throw exception;
                }
                lastRateLimitException = exception;
                if (attempt == KIS_RATE_LIMIT_RETRY_MAX_ATTEMPTS) {
                    break;
                }
                log.warn("KIS REST orderbook lookup rate limited for stockCode={}, retrying attempt={}/{}",
                        stockCode, attempt + 1, KIS_RATE_LIMIT_RETRY_MAX_ATTEMPTS);
                pause(KIS_RATE_LIMIT_RETRY_DELAY.multipliedBy(attempt));
            }
        }
        throw lastRateLimitException == null
                ? new IllegalStateException("KIS REST orderbook lookup failed")
                : lastRateLimitException;
    }

    private OrderBook toOrderBook(String stockCode, KisRestOrderBookSnapshot snapshot, String source) {
        return new OrderBook(
                stockCode,
                snapshot.asks().stream()
                        .map(level -> new OrderBook.OrderBookLevel(level.priceKrw(), level.quantity()))
                        .toList(),
                snapshot.bids().stream()
                        .map(level -> new OrderBook.OrderBookLevel(level.priceKrw(), level.quantity()))
                        .toList(),
                Instant.now(clock),
                source);
    }

    public Orderability getOrderability(String stockCode, String side, long quantity) {
        StockSummary stock = getStock(stockCode);
        PriceLookup priceLookup = latestPriceSnapshot(stockCode);
        ForeignOwnershipLookup foreignOwnership = latestForeignOwnershipSnapshot(stock);
        Optional<ForeignOwnershipSnapshot> snapshot = foreignOwnership.snapshot();
        BigDecimal currentForeignLimitExhaustionRate = snapshot
                .map(ForeignOwnershipSnapshot::foreignLimitExhaustionRate)
                .orElse(BigDecimal.ZERO);
        BigDecimal predictedForeignLimitExhaustionRate = predictedForeignLimitExhaustionRate(
                side,
                quantity,
                snapshot);
        List<ForeignOwnershipDailySnapshot> history = foreignOwnershipHistory(stockCode, snapshot);
        Optional<KisRealtimeTradeTick> realtimeTradeTick = realtimeMarketDataCache.latestTrade(stockCode);
        ForeignOwnershipPrediction foreignOwnershipPrediction = foreignOwnershipPrediction(
                side,
                quantity,
                snapshot,
                realtimeTradeTick,
                history);
        boolean foreignLimitExceeded = "BUY".equals(side)
                && predictedForeignLimitExhaustionRate.compareTo(FOREIGN_LIMIT_BLOCK_RATE) >= 0;
        MarketStatus marketStatus = latestMarketStatus(stockCode);
        String blockedReason = blockedReason(foreignLimitExceeded, marketStatus.tradingHalted());

        return new Orderability(
                stock.stockCode(),
                stock.market(),
                side,
                quantity,
                blockedReason == null,
                blockedReason,
                foreignLimitExceeded,
                currentForeignLimitExhaustionRate,
                predictedForeignLimitExhaustionRate,
                foreignOwnershipPrediction,
                snapshot.map(ForeignOwnershipSnapshot::baseDate).orElse(null),
                marketStatus.viActive(),
                marketStatus.singlePriceTrading(),
                marketStatus.priceLimitState(),
                marketStatus.tradingHalted(),
                Instant.now(clock),
                "ORDERABILITY_" + source(priceLookup.source(), foreignOwnership.source())
                        + "+" + marketStatus.source());
    }

    public List<StockSummary> searchStocks(String query) {
        return stockMasterRepository.search(query);
    }

    public StockSummary getStock(String stockCode) {
        return stockMasterRepository.findByCode(stockCode)
                .orElseThrow(() -> new StockMasterNotFoundException(stockCode));
    }

    public ExchangeRateSnapshot updateExchangeRate(String localCurrency, BigDecimal fxRate) {
        return exchangeRateCache.put(localCurrency, fxRate, Instant.now(clock));
    }

    private FxLookup resolveFxRate(String localCurrency, BigDecimal requestFxRate) {
        if (requestFxRate != null) {
            return new FxLookup(requestFxRate, Instant.now(clock), "PARTNER_REQUEST", false);
        }
        return exchangeRateCache.find(localCurrency)
                .map(snapshot -> new FxLookup(
                        snapshot.fxRate(),
                        snapshot.updatedAt(),
                        "EXCHANGE_RATE_CACHE",
                        false))
                .orElseThrow(() -> new MarketDataUnavailableException(
                        "No FX provider or partner exchange rate is available for currency=" + localCurrency));
    }

    private List<String> resolveStockCodes(List<String> stockCodes) {
        if (stockCodes == null || stockCodes.isEmpty()) {
            return List.of();
        }
        return new LinkedHashSet<>(stockCodes).stream()
                .filter(stockCode -> stockCode != null && !stockCode.isBlank())
                .toList();
    }

    private String normalizeMarket(String market) {
        if (market == null || market.isBlank()) {
            return null;
        }
        return market.toUpperCase(Locale.ROOT);
    }

    private PriceLookup latestPriceSnapshot(String stockCode) {
        Optional<KisRealtimeTradeTick> realtimeTrade = realtimeMarketDataCache.latestTrade(stockCode);
        if (realtimeTrade.isPresent() && !realtimeTrade.orElseThrow().afterHours()) {
            return PriceLookup.realtime(realtimeTrade.orElseThrow());
        }
        Optional<PriceLookup> freshCachedPrice = cachedPrice(stockCode, PRICE_CACHE_TTL);
        if (freshCachedPrice.isPresent()) {
            return freshCachedPrice.orElseThrow();
        }
        try {
            Optional<KisCurrentPriceSnapshot> kisSnapshot = findKisCurrentPrice(stockCode);
            if (kisSnapshot.isPresent()) {
                LocalDate baseDate = LocalDate.now(clock);
                KisCurrentPriceSnapshot snapshot = kisSnapshot.orElseThrow();
                snapshot.foreignOwnershipSnapshot(baseDate).ifPresent(this::storeForeignOwnershipSnapshot);
                PriceLookup priceLookup = PriceLookup.kis(snapshot, baseDate);
                priceLookupCache.put(stockCode, new CachedPriceLookup(priceLookup, Instant.now(clock)));
                return priceLookup;
            }
        } catch (RuntimeException exception) {
            // KIS 인증 또는 일시 장애가 있어도 공공데이터 snapshot으로 시세 응답을 유지한다.
            log.warn("KIS current price lookup failed for stockCode={}: {}", stockCode, exception.toString());
            Optional<PriceLookup> staleCachedPrice = cachedPrice(stockCode, PRICE_CACHE_STALE_TTL);
            if (staleCachedPrice.isPresent()) {
                return staleCachedPrice.orElseThrow();
            }
        }
        return latestPublicDataSnapshot(stockCode)
                .map(PriceLookup::publicData)
                .orElseGet(PriceLookup::empty);
    }

    private Optional<KisCurrentPriceSnapshot> findKisCurrentPrice(String stockCode) {
        try {
            return kisCurrentPriceClient.findCurrentPrice(stockCode);
        } catch (RuntimeException exception) {
            if (!isKisRateLimitError(exception)) {
                throw exception;
            }
            log.warn("KIS current price lookup rate limited for stockCode={}, retrying once", stockCode);
            pause(KIS_RATE_LIMIT_RETRY_DELAY);
            return kisCurrentPriceClient.findCurrentPrice(stockCode);
        }
    }

    private Optional<PriceLookup> cachedPrice(String stockCode, Duration ttl) {
        CachedPriceLookup cached = priceLookupCache.get(stockCode);
        if (cached == null) {
            return Optional.empty();
        }
        if (cached.cachedAt().plus(ttl).isBefore(Instant.now(clock))) {
            return Optional.empty();
        }
        return Optional.of(cached.priceLookup());
    }

    private Optional<PublicDataStockPriceSnapshot> latestPublicDataSnapshot(String stockCode) {
        LocalDate baseDate = LocalDate.now(clock).minusDays(1);
        for (int daysBack = 0; daysBack < 7; daysBack++) {
            try {
                Optional<PublicDataStockPriceSnapshot> snapshot =
                        publicDataStockSecuritiesClient.findPrice(stockCode, baseDate.minusDays(daysBack));
                if (snapshot.isPresent()) {
                    return snapshot;
                }
            } catch (RuntimeException exception) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private ForeignOwnershipPrediction foreignOwnershipPrediction(
            String side,
            long quantity,
            Optional<ForeignOwnershipSnapshot> snapshot,
            Optional<KisRealtimeTradeTick> realtimeTradeTick,
            List<ForeignOwnershipDailySnapshot> history) {
        if (hannahAiForeignOwnershipPredictionClient == null || snapshot.isEmpty()) {
            return fallbackForeignOwnershipPrediction(side, quantity, snapshot, realtimeTradeTick, history);
        }
        try {
            return toForeignOwnershipPrediction(hannahAiForeignOwnershipPredictionClient.predict(
                    toHannahAiForeignOwnershipPredictionRequest(
                            side,
                            quantity,
                            snapshot.orElseThrow(),
                            realtimeTradeTick,
                            history)));
        } catch (ProviderCircuitOpenException | RestClientException | IllegalStateException exception) {
            log.warn("Hannah AI foreign ownership prediction failed for stockCode={}, falling back: {}",
                    snapshot.orElseThrow().stockCode(),
                    exception.toString());
            return fallbackForeignOwnershipPrediction(side, quantity, snapshot, realtimeTradeTick, history);
        }
    }

    private ForeignOwnershipPrediction fallbackForeignOwnershipPrediction(
            String side,
            long quantity,
            Optional<ForeignOwnershipSnapshot> snapshot,
            Optional<KisRealtimeTradeTick> realtimeTradeTick,
            List<ForeignOwnershipDailySnapshot> history) {
        return foreignOwnershipPredictionEngine.predict(side, quantity, snapshot, realtimeTradeTick, history);
    }

    private HannahAiForeignOwnershipPredictionRequest toHannahAiForeignOwnershipPredictionRequest(
            String side,
            long quantity,
            ForeignOwnershipSnapshot snapshot,
            Optional<KisRealtimeTradeTick> realtimeTradeTick,
            List<ForeignOwnershipDailySnapshot> history) {
        return new HannahAiForeignOwnershipPredictionRequest(
                snapshot.stockCode(),
                side,
                quantity,
                snapshot.foreignOwnedQuantity(),
                snapshot.foreignOwnershipRate(),
                snapshot.foreignLimitQuantity(),
                snapshot.foreignLimitExhaustionRate(),
                snapshot.baseDate(),
                realtimeTradeTick.map(KisRealtimeTradeTick::accumulatedVolume).orElse(0L),
                history.stream()
                        .map(this::toHannahAiForeignOwnershipHistoryPoint)
                        .toList());
    }

    private HannahAiForeignOwnershipHistoryPoint toHannahAiForeignOwnershipHistoryPoint(
            ForeignOwnershipDailySnapshot snapshot) {
        return new HannahAiForeignOwnershipHistoryPoint(
                snapshot.baseDate(),
                snapshot.foreignOwnedQuantity(),
                snapshot.foreignOwnershipRate(),
                snapshot.foreignLimitQuantity(),
                snapshot.foreignLimitExhaustionRate());
    }

    private ForeignOwnershipPrediction toForeignOwnershipPrediction(
            HannahAiForeignOwnershipPredictionResponse response) {
        return new ForeignOwnershipPrediction(
                response.minForeignLimitExhaustionRate(),
                response.baseForeignLimitExhaustionRate(),
                response.maxForeignLimitExhaustionRate(),
                response.orderImpactRate(),
                response.intradayUncertaintyRate(),
                response.observedIntradayVolume(),
                response.trendDailyChangeRate(),
                response.historyObservationCount(),
                response.historyWindowDays(),
                response.baseDate(),
                response.calculatedAt(),
                response.confidenceLevel(),
                response.confidenceScore(),
                response.modelVersion(),
                response.source());
    }

    private ForeignOwnershipLookup latestForeignOwnershipSnapshot(StockSummary stock) {
        return foreignOwnershipSnapshotCache.find(stock.stockCode())
                .map(ForeignOwnershipLookup::cache)
                .orElseGet(ForeignOwnershipLookup::empty);
    }

    private List<ForeignOwnershipDailySnapshot> foreignOwnershipHistory(
            String stockCode,
            Optional<ForeignOwnershipSnapshot> snapshot) {
        LocalDate to = snapshot.map(ForeignOwnershipSnapshot::baseDate).orElse(LocalDate.now(clock));
        return foreignOwnershipDailySnapshotRepository.findRecent(stockCode, to, FOREIGN_OWNERSHIP_HISTORY_LIMIT);
    }

    private void storeForeignOwnershipSnapshot(ForeignOwnershipSnapshot snapshot) {
        foreignOwnershipSnapshotCache.put(snapshot);
        foreignOwnershipDailySnapshotRepository.upsert(new ForeignOwnershipDailySnapshot(
                snapshot.stockCode(),
                snapshot.baseDate(),
                snapshot.foreignOwnedQuantity(),
                snapshot.foreignOwnershipRate(),
                snapshot.foreignLimitQuantity(),
                snapshot.foreignLimitExhaustionRate(),
                "KIS_CURRENT_PRICE_FOREIGN_OWNERSHIP",
                Instant.now(clock)));
    }

    private BigDecimal predictedForeignLimitExhaustionRate(
            String side,
            long quantity,
            Optional<ForeignOwnershipSnapshot> snapshot) {
        if (!"BUY".equals(side) || snapshot.isEmpty() || snapshot.orElseThrow().foreignLimitQuantity() <= 0) {
            return snapshot.map(ForeignOwnershipSnapshot::foreignLimitExhaustionRate).orElse(BigDecimal.ZERO);
        }
        ForeignOwnershipSnapshot ownership = snapshot.orElseThrow();
        BigDecimal quantityRate = BigDecimal.valueOf(quantity)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(ownership.foreignLimitQuantity()), 6, RoundingMode.HALF_UP);
        return ownership.foreignLimitExhaustionRate()
                .add(quantityRate)
                .setScale(6, RoundingMode.HALF_UP);
    }

    private String blockedReason(boolean foreignLimitExceeded, boolean tradingHalted) {
        if (tradingHalted) {
            return "TRADING_HALTED";
        }
        if (foreignLimitExceeded) {
            return "FOREIGN_LIMIT_EXCEEDED";
        }
        return null;
    }

    private MarketStatus latestMarketStatus(String stockCode) {
        Optional<KisRealtimeTradeTick> realtimeTrade = realtimeMarketDataCache.latestTrade(stockCode);
        if (realtimeTrade.isPresent()) {
            KisRealtimeTradeTick tick = realtimeTrade.orElseThrow();
            return new MarketStatus(
                        activeStatusCode(tick.viStatusCode()),
                        activeStatusCode(tick.singlePriceTradingCode()),
                        priceLimitState(tick),
                        activeStatusCode(tick.tradingHaltCode()),
                        "KIS_WEBSOCKET_TRADE_STATUS");
        }
        return latestRestOrderBook(stockCode)
                .map(orderBook -> new MarketStatus(
                        false,
                        false,
                        priceLimitState(orderBook),
                        false,
                        "KIS_REST_ORDERBOOK_STATUS_FALLBACK"))
                .orElse(MarketStatus.unavailable());
    }

    private boolean activeStatusCode(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toUpperCase();
        return !List.of("0", "00", "N", "NO", "NORMAL", "NONE", "FALSE", "INACTIVE", "OFF").contains(normalized);
    }

    private String priceLimitState(KisRealtimeTradeTick tick) {
        if (tick.currentPriceKrw().signum() <= 0) {
            return "NORMAL";
        }
        if (tick.askPrice1Krw().signum() == 0 && tick.bidPrice1Krw().signum() > 0) {
            return "UPPER_LIMIT";
        }
        if (tick.bidPrice1Krw().signum() == 0 && tick.askPrice1Krw().signum() > 0) {
            return "LOWER_LIMIT";
        }
        return "NORMAL";
    }

    private String priceLimitState(OrderBook orderBook) {
        boolean hasAsk = hasPositivePriceAndQuantity(orderBook.asks());
        boolean hasBid = hasPositivePriceAndQuantity(orderBook.bids());
        if (!hasAsk && hasBid) {
            return "UPPER_LIMIT";
        }
        if (!hasBid && hasAsk) {
            return "LOWER_LIMIT";
        }
        return "NORMAL";
    }

    private boolean hasPositivePriceAndQuantity(List<OrderBook.OrderBookLevel> levels) {
        return levels.stream()
                .anyMatch(level -> level.priceKrw().signum() > 0 && level.quantity() > 0);
    }

    private String source(PriceSource priceSource, ForeignOwnershipSource foreignOwnershipSource) {
        if (priceSource == PriceSource.KIS_WEBSOCKET_TRADE && foreignOwnershipSource == ForeignOwnershipSource.CACHE) {
            return "KIS_WEBSOCKET_TRADE+KIS_FOREIGN_OWNERSHIP_CACHE";
        }
        if (priceSource == PriceSource.KIS_WEBSOCKET_TRADE) {
            return "KIS_WEBSOCKET_TRADE";
        }
        if (priceSource == PriceSource.KIS_OPEN_API && foreignOwnershipSource == ForeignOwnershipSource.CACHE) {
            return "KIS_OPEN_API+KIS_FOREIGN_OWNERSHIP_CACHE";
        }
        if (priceSource == PriceSource.KIS_OPEN_API) {
            return "KIS_OPEN_API";
        }
        if (priceSource == PriceSource.PUBLIC_DATA && foreignOwnershipSource == ForeignOwnershipSource.CACHE) {
            return "PUBLIC_DATA_STOCK_SECURITIES+KIS_FOREIGN_OWNERSHIP_CACHE";
        }
        if (priceSource == PriceSource.PUBLIC_DATA) {
            return "PUBLIC_DATA_STOCK_SECURITIES";
        }
        return "MARKET_DATA_UNAVAILABLE";
    }

    private static boolean isKisRateLimitError(RuntimeException exception) {
        String message = exception.getMessage();
        return message != null && message.contains("EGW00201");
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("KIS current price retry interrupted", exception);
        }
    }

    private record PriceLookup(
            Optional<String> stockName,
            Optional<String> market,
            Optional<BigDecimal> currentPriceKrw,
            Optional<BigDecimal> changeRate,
            Optional<Long> volume,
            Optional<LocalDate> baseDate,
            String marketSession,
            PriceSource source
    ) {
        private static PriceLookup realtime(KisRealtimeTradeTick tick) {
            return new PriceLookup(
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(tick.currentPriceKrw()),
                    Optional.of(tick.changeRate()),
                    Optional.of(tick.accumulatedVolume()),
                    Optional.of(tick.businessDate()),
                    tick.marketSession(),
                    PriceSource.KIS_WEBSOCKET_TRADE);
        }

        private static PriceLookup kis(KisCurrentPriceSnapshot snapshot, LocalDate baseDate) {
            return new PriceLookup(
                    Optional.ofNullable(snapshot.stockName()).filter(name -> !name.isBlank()),
                    Optional.empty(),
                    Optional.of(snapshot.currentPriceKrw()),
                    Optional.of(snapshot.changeRate()),
                    Optional.of(snapshot.volume()),
                    Optional.of(baseDate),
                    KisRealtimeTradeTick.REGULAR_SESSION,
                    PriceSource.KIS_OPEN_API);
        }

        private static PriceLookup publicData(PublicDataStockPriceSnapshot snapshot) {
            return new PriceLookup(
                    Optional.of(snapshot.stockName()),
                    Optional.of(snapshot.market()),
                    Optional.of(snapshot.closingPriceKrw()),
                    Optional.of(snapshot.changeRate()),
                    Optional.of(snapshot.volume()),
                    Optional.of(snapshot.baseDate()),
                    KisRealtimeTradeTick.REGULAR_SESSION,
                    PriceSource.PUBLIC_DATA);
        }

        private static PriceLookup empty() {
            return new PriceLookup(
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    KisRealtimeTradeTick.REGULAR_SESSION,
                    PriceSource.NONE);
        }
    }

    private record CachedPriceLookup(
            PriceLookup priceLookup,
            Instant cachedAt
    ) {
    }

    private enum PriceSource {
        KIS_WEBSOCKET_TRADE,
        KIS_OPEN_API,
        PUBLIC_DATA,
        NONE
    }

    private record ForeignOwnershipLookup(
            Optional<ForeignOwnershipSnapshot> snapshot,
            ForeignOwnershipSource source
    ) {
        private static ForeignOwnershipLookup cache(ForeignOwnershipSnapshot snapshot) {
            return new ForeignOwnershipLookup(Optional.of(snapshot), ForeignOwnershipSource.CACHE);
        }

        private static ForeignOwnershipLookup empty() {
            return new ForeignOwnershipLookup(Optional.empty(), ForeignOwnershipSource.NONE);
        }
    }

    private enum ForeignOwnershipSource {
        CACHE,
        NONE
    }

    private record FxLookup(
            BigDecimal fxRate,
            Instant fxRateTime,
            String fxRateSource,
            boolean stale
    ) {
    }

    private record MarketStatus(
            boolean viActive,
            boolean singlePriceTrading,
            String priceLimitState,
            boolean tradingHalted,
            String source
    ) {
        private static MarketStatus unavailable() {
            return new MarketStatus(false, false, "UNKNOWN", false, "MARKET_STATUS_UNAVAILABLE");
        }
    }
}
