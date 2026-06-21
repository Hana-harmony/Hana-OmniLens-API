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

import com.hana.omnilens.market.domain.ForeignOwnershipPrediction;
import com.hana.omnilens.market.domain.MarketQuote;
import com.hana.omnilens.market.domain.Orderability;
import com.hana.omnilens.market.domain.OrderBook;
import com.hana.omnilens.market.domain.StockDetail;
import com.hana.omnilens.market.domain.StockSummary;
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
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final PublicDataStockSecuritiesClient publicDataStockSecuritiesClient;
    private final KisCurrentPriceClient kisCurrentPriceClient;
    private final KisRestOrderBookClient kisRestOrderBookClient;
    private final StockMasterRepository stockMasterRepository;
    private final ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache;
    private final ExchangeRateCache exchangeRateCache;
    private final RealtimeMarketDataCache realtimeMarketDataCache;
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
            ExchangeRateCache exchangeRateCache,
            RealtimeMarketDataCache realtimeMarketDataCache,
            ForeignOwnershipPredictionEngine foreignOwnershipPredictionEngine) {
        this(
                publicDataStockSecuritiesClient,
                kisCurrentPriceClient,
                kisRestOrderBookClient,
                stockMasterRepository,
                foreignOwnershipSnapshotCache,
                exchangeRateCache,
                realtimeMarketDataCache,
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
                exchangeRateCache,
                realtimeMarketDataCache,
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
                exchangeRateCache,
                realtimeMarketDataCache,
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
                exchangeRateCache,
                realtimeMarketDataCache,
                foreignOwnershipPredictionEngine,
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
            ForeignOwnershipPredictionEngine foreignOwnershipPredictionEngine,
            Clock clock) {
        this.publicDataStockSecuritiesClient = publicDataStockSecuritiesClient;
        this.kisCurrentPriceClient = kisCurrentPriceClient;
        this.kisRestOrderBookClient = kisRestOrderBookClient;
        this.stockMasterRepository = stockMasterRepository;
        this.foreignOwnershipSnapshotCache = foreignOwnershipSnapshotCache;
        this.exchangeRateCache = exchangeRateCache;
        this.realtimeMarketDataCache = realtimeMarketDataCache;
        this.foreignOwnershipPredictionEngine = foreignOwnershipPredictionEngine;
        this.clock = clock;
    }

    public MarketQuote getQuote(String stockCode, String localCurrency, BigDecimal fxRate) {
        PriceLookup priceLookup = latestPriceSnapshot(stockCode);
        StockSummary stock = getStock(stockCode);
        ForeignOwnershipLookup foreignOwnership = latestForeignOwnershipSnapshot(stock);

        BigDecimal currentPrice = priceLookup.currentPriceKrw()
                .orElseThrow(() -> new MarketDataUnavailableException(
                        "No live provider price is available for stockCode=" + stockCode));
        ForeignOwnershipSnapshot ownership = foreignOwnership.snapshot()
                .orElseThrow(() -> new MarketDataUnavailableException(
                        "No KIS foreign ownership snapshot is available for stockCode=" + stockCode));
        FxLookup fxLookup = resolveFxRate(localCurrency, fxRate);
        BigDecimal localPrice = currentPrice.multiply(fxLookup.fxRate()).setScale(4, RoundingMode.HALF_UP);

        return new MarketQuote(
                stockCode,
                priceLookup.stockName().orElse(stock.stockName()),
                stock.stockNameEn(),
                priceLookup.market().orElse(stock.market()),
                currentPrice,
                priceLookup.changeRate().orElse(new BigDecimal("1.42")),
                priceLookup.volume().orElse(12193000L),
                currentPrice,
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
        ForeignOwnershipPrediction foreignOwnershipPrediction = foreignOwnershipPredictionEngine.predict(
                side,
                quantity,
                snapshot,
                realtimeMarketDataCache.latestTrade(stockCode));
        boolean foreignLimitExceeded = "BUY".equals(side)
                && foreignOwnershipPrediction.maxForeignLimitExhaustionRate().compareTo(FOREIGN_LIMIT_BLOCK_RATE) >= 0;
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
        if (realtimeTrade.isPresent()) {
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
                snapshot.foreignOwnershipSnapshot(baseDate).ifPresent(foreignOwnershipSnapshotCache::put);
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

    private ForeignOwnershipLookup latestForeignOwnershipSnapshot(StockSummary stock) {
        return foreignOwnershipSnapshotCache.find(stock.stockCode())
                .map(ForeignOwnershipLookup::cache)
                .orElseGet(ForeignOwnershipLookup::empty);
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
        return realtimeMarketDataCache.latestTrade(stockCode)
                .map(tick -> new MarketStatus(
                        activeStatusCode(tick.viStatusCode()),
                        activeStatusCode(tick.singlePriceTradingCode()),
                        priceLimitState(tick),
                        activeStatusCode(tick.tradingHaltCode()),
                        "KIS_WEBSOCKET_TRADE_STATUS"))
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
