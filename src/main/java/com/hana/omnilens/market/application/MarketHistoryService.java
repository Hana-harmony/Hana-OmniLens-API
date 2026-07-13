package com.hana.omnilens.market.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.hana.omnilens.config.MarketChartWarmupProperties;
import com.hana.omnilens.config.MarketHistoryCollectionProperties;
import com.hana.omnilens.market.domain.MarketDailyPrice;
import com.hana.omnilens.market.domain.MarketIntradayPrice;
import com.hana.omnilens.market.domain.StockSummary;
import com.hana.omnilens.provider.market.KisDailyChartPrice;
import com.hana.omnilens.provider.market.KisDailyChartPriceClient;
import com.hana.omnilens.provider.market.KisMinuteChartPrice;
import com.hana.omnilens.provider.market.KisMinuteChartPriceClient;
import com.hana.omnilens.provider.market.KrxOpenApiDailyTrade;
import com.hana.omnilens.provider.market.KrxOpenApiDailyTradeClient;
import com.hana.omnilens.provider.market.YahooStockMinuteChartPriceClient;

@Service
public class MarketHistoryService {

    private static final Logger log = LoggerFactory.getLogger(MarketHistoryService.class);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final String SOURCE = "KRX_OPEN_API_DAILY_TRADE";
    private static final String KIS_SOURCE = "KIS_DAILY_ITEM_CHART_PRICE";
    private static final String KIS_FALLBACK_MARKET = "KIS_DAILY_CHART";
    private static final String YAHOO_INTRADAY_SOURCE = "YAHOO_FINANCE_CHART_PRICE";
    private static final int KIS_FALLBACK_STOCK_LIMIT = 2_000;
    private static final Duration KIS_FALLBACK_REQUEST_INTERVAL = Duration.ofMillis(2_200);
    private static final Duration KIS_RATE_LIMIT_RETRY_DELAY = Duration.ofMillis(3_000);
    private static final Duration INTRADAY_CACHE_FRESHNESS = Duration.ofSeconds(60);
    private static final LocalTime REGULAR_MARKET_OPEN = LocalTime.of(9, 0);
    private static final LocalTime REGULAR_MARKET_CLOSE = LocalTime.of(15, 30);
    private static final LocalTime REGULAR_MARKET_FIRST_MINUTE = LocalTime.of(9, 1);
    private static final int REGULAR_SESSION_EXPECTED_MINUTES = 390;
    private static final int REGULAR_SESSION_MINIMUM_COMPLETE_MINUTES = 380;
    private static final List<String> MARKETS = List.of("KOSPI", "KOSDAQ", "KONEX");

    private final KrxOpenApiDailyTradeClient krxOpenApiDailyTradeClient;
    private final KisDailyChartPriceClient kisDailyChartPriceClient;
    private final KisMinuteChartPriceClient kisMinuteChartPriceClient;
    private final YahooStockMinuteChartPriceClient yahooStockMinuteChartPriceClient;
    private final MarketDailyPriceRepository marketDailyPriceRepository;
    private final MarketIntradayPriceRepository marketIntradayPriceRepository;
    private final StockMasterRepository stockMasterRepository;
    private final MarketHistoryCollectionProperties collectionProperties;
    private final MarketChartWarmupProperties chartWarmupProperties;
    private final Clock clock;

    @Autowired
    public MarketHistoryService(
            KrxOpenApiDailyTradeClient krxOpenApiDailyTradeClient,
            KisDailyChartPriceClient kisDailyChartPriceClient,
            KisMinuteChartPriceClient kisMinuteChartPriceClient,
            YahooStockMinuteChartPriceClient yahooStockMinuteChartPriceClient,
            MarketDailyPriceRepository marketDailyPriceRepository,
            MarketIntradayPriceRepository marketIntradayPriceRepository,
            StockMasterRepository stockMasterRepository,
            MarketHistoryCollectionProperties collectionProperties,
            MarketChartWarmupProperties chartWarmupProperties) {
        this(
                krxOpenApiDailyTradeClient,
                kisDailyChartPriceClient,
                kisMinuteChartPriceClient,
                yahooStockMinuteChartPriceClient,
                marketDailyPriceRepository,
                marketIntradayPriceRepository,
                stockMasterRepository,
                collectionProperties,
                chartWarmupProperties,
                Clock.system(KOREA_ZONE));
    }

    MarketHistoryService(
            KrxOpenApiDailyTradeClient krxOpenApiDailyTradeClient,
            KisDailyChartPriceClient kisDailyChartPriceClient,
            MarketDailyPriceRepository marketDailyPriceRepository,
            StockMasterRepository stockMasterRepository,
            MarketHistoryCollectionProperties collectionProperties,
            Clock clock) {
        this(
                krxOpenApiDailyTradeClient,
                kisDailyChartPriceClient,
                null,
                null,
                marketDailyPriceRepository,
                null,
                stockMasterRepository,
                collectionProperties,
                null,
                clock);
    }

    MarketHistoryService(
            KrxOpenApiDailyTradeClient krxOpenApiDailyTradeClient,
            KisDailyChartPriceClient kisDailyChartPriceClient,
            KisMinuteChartPriceClient kisMinuteChartPriceClient,
            MarketDailyPriceRepository marketDailyPriceRepository,
            MarketIntradayPriceRepository marketIntradayPriceRepository,
            StockMasterRepository stockMasterRepository,
            MarketHistoryCollectionProperties collectionProperties,
            MarketChartWarmupProperties chartWarmupProperties,
            Clock clock) {
        this(
                krxOpenApiDailyTradeClient,
                kisDailyChartPriceClient,
                kisMinuteChartPriceClient,
                null,
                marketDailyPriceRepository,
                marketIntradayPriceRepository,
                stockMasterRepository,
                collectionProperties,
                chartWarmupProperties,
                clock);
    }

    MarketHistoryService(
            KrxOpenApiDailyTradeClient krxOpenApiDailyTradeClient,
            KisDailyChartPriceClient kisDailyChartPriceClient,
            KisMinuteChartPriceClient kisMinuteChartPriceClient,
            YahooStockMinuteChartPriceClient yahooStockMinuteChartPriceClient,
            MarketDailyPriceRepository marketDailyPriceRepository,
            MarketIntradayPriceRepository marketIntradayPriceRepository,
            StockMasterRepository stockMasterRepository,
            MarketHistoryCollectionProperties collectionProperties,
            MarketChartWarmupProperties chartWarmupProperties,
            Clock clock) {
        this.krxOpenApiDailyTradeClient = krxOpenApiDailyTradeClient;
        this.kisDailyChartPriceClient = kisDailyChartPriceClient;
        this.kisMinuteChartPriceClient = kisMinuteChartPriceClient;
        this.yahooStockMinuteChartPriceClient = yahooStockMinuteChartPriceClient;
        this.marketDailyPriceRepository = marketDailyPriceRepository;
        this.marketIntradayPriceRepository = marketIntradayPriceRepository;
        this.stockMasterRepository = stockMasterRepository;
        this.collectionProperties = collectionProperties == null
                ? new MarketHistoryCollectionProperties(false, 86_400_000L, 1, null)
                : collectionProperties;
        this.chartWarmupProperties = chartWarmupProperties == null
                ? new MarketChartWarmupProperties(true, 300_000L, 10_000L, 0, 30, 7, 30, List.of(), 1_200L, true, true)
                : chartWarmupProperties;
        this.clock = clock;
    }

    public List<MarketDailyPrice> getHistory(String stockCode, LocalDate from, LocalDate to, int limit) {
        StockSummary stock = stockMasterRepository.findByCode(stockCode)
                .orElseThrow(() -> new StockMasterNotFoundException(stockCode));
        LocalDate resolvedTo = to == null ? LocalDate.now(clock) : to;
        LocalDate resolvedFrom = from == null ? resolvedTo.minusYears(1) : from;
        if (resolvedFrom.isAfter(resolvedTo)) {
            return List.of();
        }
        List<MarketDailyPrice> savedHistory =
                marketDailyPriceRepository.findByStockCode(stockCode, resolvedFrom, resolvedTo, limit);
        if (!savedHistory.isEmpty() && coversRequestedStart(savedHistory, resolvedFrom)) {
            return savedHistory;
        }
        List<MarketDailyPrice> fetchedHistory = loadKisHistoryFallback(stock, resolvedFrom, resolvedTo, limit);
        return mergeHistory(savedHistory, fetchedHistory, limit);
    }

    private boolean coversRequestedStart(List<MarketDailyPrice> savedHistory, LocalDate requestedFrom) {
        LocalDate earliestSavedDate = savedHistory.stream()
                .map(MarketDailyPrice::tradeDate)
                .min(Comparator.naturalOrder())
                .orElse(requestedFrom);
        return !earliestSavedDate.isAfter(requestedFrom.plusDays(3));
    }

    private List<MarketDailyPrice> mergeHistory(
            List<MarketDailyPrice> savedHistory,
            List<MarketDailyPrice> fetchedHistory,
            int limit) {
        Map<LocalDate, MarketDailyPrice> pricesByDate = new LinkedHashMap<>();
        fetchedHistory.stream()
                .sorted(Comparator.comparing(MarketDailyPrice::tradeDate))
                .forEach(price -> pricesByDate.put(price.tradeDate(), price));
        savedHistory.stream()
                .sorted(Comparator.comparing(MarketDailyPrice::tradeDate))
                .forEach(price -> pricesByDate.put(price.tradeDate(), price));
        return pricesByDate.values().stream()
                .sorted(Comparator.comparing(MarketDailyPrice::tradeDate))
                .limit(limit)
                .toList();
    }

    public List<MarketIntradayPrice> getIntradayHistory(String stockCode, LocalDate date, int limit) {
        return getIntradayHistory(stockCode, date, limit, true);
    }

    public List<MarketIntradayPrice> getIntradayHistory(
            String stockCode,
            LocalDate date,
            int limit,
            boolean fetchMissing) {
        StockSummary stock = stockMasterRepository.findByCode(stockCode)
                .orElseThrow(() -> new StockMasterNotFoundException(stockCode));
        LocalDate resolvedDate = date == null ? LocalDate.now(clock) : date;
        List<MarketIntradayPrice> savedPrices = regularSessionPrices(
                findSavedIntradayPrices(stockCode, resolvedDate, limit));
        if (isReusableIntradayCache(savedPrices, resolvedDate)) {
            return savedPrices;
        }
        if (!fetchMissing) {
            return savedPrices;
        }
        List<MarketIntradayPrice> fetchedPrices = fetchIntradayBackfill(stock, resolvedDate, limit)
                .stream()
                .map(price -> toIntradayPrice(stock, price.price(), price.source()))
                .filter(price -> isRegularSessionPrice(price) && isMissingSavedBucket(savedPrices, price))
                .toList();
        if (marketIntradayPriceRepository != null && !fetchedPrices.isEmpty()) {
            try {
                marketIntradayPriceRepository.upsertAll(fetchedPrices);
                List<MarketIntradayPrice> reloadedPrices =
                        regularSessionPrices(findSavedIntradayPrices(stockCode, resolvedDate, limit));
                return reloadedPrices.isEmpty() ? fetchedPrices : reloadedPrices;
            } catch (RuntimeException exception) {
                // 저장 장애가 이미 확보한 provider 분봉 응답까지 제거하지 않게 한다.
                log.warn("Intraday backfill persistence failed stockCode={} date={}, returning provider data",
                        stockCode,
                        resolvedDate,
                        exception);
                return Stream.concat(savedPrices.stream(), fetchedPrices.stream())
                        .sorted(Comparator.comparing(MarketIntradayPrice::bucketStart))
                        .limit(limit)
                        .toList();
            }
        }
        return fetchedPrices.isEmpty() ? savedPrices : fetchedPrices;
    }

    private List<IntradayBackfillPrice> fetchIntradayBackfill(StockSummary stock, LocalDate resolvedDate, int limit) {
        if (yahooStockMinuteChartPriceClient != null) {
            try {
                List<IntradayBackfillPrice> yahooPrices = yahooStockMinuteChartPriceClient
                        .findMinutePrices(stock, resolvedDate, limit)
                        .stream()
                        .map(price -> new IntradayBackfillPrice(price, YAHOO_INTRADAY_SOURCE))
                        .toList();
                if (!yahooPrices.isEmpty()) {
                    return yahooPrices;
                }
            } catch (RuntimeException exception) {
                log.warn("Yahoo stock intraday history fetch failed stockCode={} date={}: {}",
                        stock.stockCode(), resolvedDate, exception.toString());
            }
        }
        if (kisMinuteChartPriceClient == null) {
            return List.of();
        }
        return kisMinuteChartPriceClient.findMinutePrices(stock.stockCode(), resolvedDate, limit)
                .stream()
                .map(price -> new IntradayBackfillPrice(price, kisIntradaySource(resolvedDate)))
                .toList();
    }

    private boolean isMissingSavedBucket(List<MarketIntradayPrice> savedPrices, MarketIntradayPrice fetchedPrice) {
        return savedPrices.stream()
                .noneMatch(savedPrice -> savedPrice.bucketStart().equals(fetchedPrice.bucketStart()));
    }

    private String kisIntradaySource(LocalDate requestedDate) {
        return requestedDate.equals(LocalDate.now(clock))
                ? "KIS_TIME_ITEM_CHART_PRICE"
                : "KIS_TIME_DAILY_CHART_PRICE";
    }

    private MarketIntradayPrice toIntradayPrice(
            StockSummary stock,
            KisMinuteChartPrice price,
            String source) {
        return new MarketIntradayPrice(
                stock.stockCode(),
                price.bucketStart(),
                stock.market(),
                price.openPriceKrw(),
                price.highPriceKrw(),
                price.lowPriceKrw(),
                price.closePriceKrw(),
                price.volume(),
                price.tradingValueKrw(),
                source,
                Instant.now(clock));
    }

    private List<MarketIntradayPrice> regularSessionPrices(List<MarketIntradayPrice> prices) {
        return prices.stream()
                .filter(this::isRegularSessionPrice)
                .toList();
    }

    private boolean isRegularSessionPrice(MarketIntradayPrice price) {
        LocalTime time = price.bucketStart().toLocalTime();
        return !time.isBefore(REGULAR_MARKET_OPEN) && !time.isAfter(REGULAR_MARKET_CLOSE);
    }

    private List<MarketIntradayPrice> findSavedIntradayPrices(String stockCode, LocalDate date, int limit) {
        if (marketIntradayPriceRepository == null) {
            return List.of();
        }
        return marketIntradayPriceRepository.findByStockCodeAndDate(stockCode, date, limit);
    }

    private boolean isReusableIntradayCache(List<MarketIntradayPrice> prices, LocalDate date) {
        if (prices.isEmpty()) {
            return false;
        }
        LocalDate today = LocalDate.now(clock);
        if (date.isBefore(today)) {
            return true;
        }
        if (date.equals(today) && isRegularSessionClosed() && hasCompleteRegularSession(prices)) {
            return true;
        }
        if (date.equals(today) && !coversRegularSessionStart(prices)) {
            return false;
        }
        Instant newestCollectionTime = prices.stream()
                .map(MarketIntradayPrice::collectedAt)
                .max(Comparator.naturalOrder())
                .orElse(Instant.EPOCH);
        return newestCollectionTime.plus(INTRADAY_CACHE_FRESHNESS).isAfter(Instant.now(clock));
    }

    private boolean coversRegularSessionStart(List<MarketIntradayPrice> prices) {
        LocalTime earliestTime = prices.stream()
                .map(price -> price.bucketStart().toLocalTime())
                .min(Comparator.naturalOrder())
                .orElse(REGULAR_MARKET_CLOSE);
        return !earliestTime.isAfter(REGULAR_MARKET_FIRST_MINUTE);
    }

    private boolean isRegularSessionClosed() {
        return LocalTime.now(clock).isAfter(REGULAR_MARKET_CLOSE.plusMinutes(5));
    }

    private boolean hasCompleteRegularSession(List<MarketIntradayPrice> prices) {
        LocalTime earliestTime = prices.stream()
                .map(price -> price.bucketStart().toLocalTime())
                .min(Comparator.naturalOrder())
                .orElse(REGULAR_MARKET_CLOSE);
        LocalTime latestTime = prices.stream()
                .map(price -> price.bucketStart().toLocalTime())
                .max(Comparator.naturalOrder())
                .orElse(REGULAR_MARKET_OPEN);
        long distinctMinuteCount = prices.stream()
                .map(MarketIntradayPrice::bucketStart)
                .distinct()
                .count();
        return !earliestTime.isAfter(REGULAR_MARKET_FIRST_MINUTE)
                && !latestTime.isBefore(REGULAR_MARKET_CLOSE)
                && distinctMinuteCount >= Math.min(REGULAR_SESSION_EXPECTED_MINUTES, REGULAR_SESSION_MINIMUM_COMPLETE_MINUTES);
    }

    public MarketHistoryCollectionResult collectDailyHistory(LocalDate baseDate) {
        LocalDate resolvedBaseDate = baseDate == null ? LocalDate.now(clock).minusDays(1) : baseDate;
        if (collectionProperties.provider() == MarketHistoryCollectionProperties.Provider.KIS_DAILY_CHART) {
            CollectionFallbackResult kisResult = collectKisDailyHistoryFallback(resolvedBaseDate);
            MarketHistoryCollectionResult.MarketResult marketResult = kisResult.marketResult();
            return new MarketHistoryCollectionResult(
                    resolvedBaseDate,
                    kisResult.requestedCount(),
                    kisResult.savedCount(),
                    KIS_SOURCE,
                    marketResult.status(),
                    List.of(marketResult));
        }
        List<MarketHistoryCollectionResult.MarketResult> marketResults = new ArrayList<>();
        int requestedCount = 0;
        int savedCount = 0;
        for (String market : MARKETS) {
            try {
                List<KrxOpenApiDailyTrade> trades =
                        krxOpenApiDailyTradeClient.findDailyTrades(market, resolvedBaseDate);
                List<MarketDailyPrice> prices = trades.stream()
                        .map(this::toDailyPrice)
                        .filter(price -> stockMasterRepository.findByCode(price.stockCode()).isPresent())
                        .toList();
                int marketSavedCount = prices.isEmpty() ? 0 : marketDailyPriceRepository.upsertAll(prices);
                requestedCount += trades.size();
                savedCount += marketSavedCount;
                marketResults.add(new MarketHistoryCollectionResult.MarketResult(
                        market,
                        trades.size(),
                        marketSavedCount,
                        "SUCCESS",
                        null));
            } catch (RuntimeException exception) {
                log.warn("KRX market history collection failed market={} baseDate={}",
                        market, resolvedBaseDate, exception);
                marketResults.add(new MarketHistoryCollectionResult.MarketResult(
                        market,
                        0,
                        0,
                        "FAILED",
                        providerErrorMessage(exception)));
            }
        }
        if (hasFailedMarket(marketResults)
                && collectionProperties.provider() == MarketHistoryCollectionProperties.Provider.KRX_OPEN_API_WITH_KIS_BACKUP) {
            CollectionFallbackResult fallbackResult = collectKisDailyHistoryFallback(resolvedBaseDate);
            requestedCount += fallbackResult.requestedCount();
            savedCount += fallbackResult.savedCount();
            marketResults.add(fallbackResult.marketResult());
        }
        return new MarketHistoryCollectionResult(
                resolvedBaseDate,
                requestedCount,
                savedCount,
                source(marketResults),
                collectionStatus(marketResults),
                List.copyOf(marketResults));
    }

    public MarketChartWarmupResult warmupChartHistory(LocalDate baseDate) {
        LocalDate resolvedBaseDate = baseDate == null ? LocalDate.now(clock) : baseDate;
        LocalDate dailyFrom = resolvedBaseDate.minusDays(chartWarmupProperties.dailyLookbackDays());
        List<StockSummary> stocks = chartWarmupStocks();
        List<MarketChartWarmupResult.StockResult> stockResults = new ArrayList<>();
        int dailyPointCount = 0;
        int intradayPointCount = 0;
        for (int index = 0; index < stocks.size(); index++) {
            StockSummary stock = stocks.get(index);
            if (index > 0) {
                pause(Duration.ofMillis(chartWarmupProperties.requestDelayMs()));
            }
            try {
                int stockDailyPointCount = 0;
                int stockIntradayPointCount = 0;
                if (chartWarmupProperties.isDailyEnabled()) {
                    stockDailyPointCount = getHistory(
                            stock.stockCode(),
                            dailyFrom,
                            resolvedBaseDate,
                            chartWarmupProperties.dailyLookbackDays() + 10)
                            .size();
                }
                if (chartWarmupProperties.isIntradayEnabled()) {
                    LocalDate intradayFrom = resolvedBaseDate.minusDays(chartWarmupProperties.intradayLookbackDays());
                    LocalDate intradayDate = intradayFrom;
                    while (!intradayDate.isAfter(resolvedBaseDate)) {
                        if (isWeekday(intradayDate)) {
                            stockIntradayPointCount += getIntradayHistory(stock.stockCode(), intradayDate, 390).size();
                            pause(Duration.ofMillis(chartWarmupProperties.requestDelayMs()));
                        }
                        intradayDate = intradayDate.plusDays(1);
                    }
                }
                dailyPointCount += stockDailyPointCount;
                intradayPointCount += stockIntradayPointCount;
                stockResults.add(new MarketChartWarmupResult.StockResult(
                        stock.stockCode(),
                        stockDailyPointCount,
                        stockIntradayPointCount,
                        "SUCCESS",
                        null));
            } catch (RuntimeException exception) {
                log.warn("Market chart warmup failed stockCode={} baseDate={}",
                        stock.stockCode(), resolvedBaseDate, exception);
                stockResults.add(new MarketChartWarmupResult.StockResult(
                        stock.stockCode(),
                        0,
                        0,
                        "FAILED",
                        providerErrorMessage(exception)));
            }
        }
        return new MarketChartWarmupResult(
                resolvedBaseDate,
                dailyFrom,
                resolvedBaseDate,
                stocks.size(),
                dailyPointCount,
                intradayPointCount,
                warmupStatus(stockResults),
                List.copyOf(stockResults));
    }

    private List<StockSummary> chartWarmupStocks() {
        if (!chartWarmupProperties.stockCodes().isEmpty()) {
            return chartWarmupProperties.stockCodes().stream()
                    .map(stockMasterRepository::findByCode)
                    .flatMap(Optional::stream)
                    .toList();
        }
        return stockMasterRepository.findAll(chartWarmupProperties.stockLimit());
    }

    private static boolean isWeekday(LocalDate date) {
        return date.getDayOfWeek().getValue() < 6;
    }

    private static String warmupStatus(List<MarketChartWarmupResult.StockResult> stockResults) {
        long failedCount = stockResults.stream()
                .filter(result -> "FAILED".equals(result.status()))
                .count();
        if (failedCount == 0) {
            return "SUCCESS";
        }
        if (failedCount == stockResults.size()) {
            return "FAILED";
        }
        return "PARTIAL_FAILED";
    }

    private static boolean hasFailedMarket(List<MarketHistoryCollectionResult.MarketResult> marketResults) {
        return marketResults.stream().anyMatch(result -> "FAILED".equals(result.status()));
    }

    private CollectionFallbackResult collectKisDailyHistoryFallback(LocalDate baseDate) {
        if (kisDailyChartPriceClient == null) {
            return new CollectionFallbackResult(0, 0, new MarketHistoryCollectionResult.MarketResult(
                    KIS_FALLBACK_MARKET,
                    0,
                    0,
                    "FAILED",
                    "KIS daily chart client is not configured"));
        }
        List<StockSummary> stocks = stockMasterRepository.findAll(KIS_FALLBACK_STOCK_LIMIT);
        int requestedCount = stocks.size();
        List<MarketDailyPrice> prices = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (int index = 0; index < stocks.size(); index++) {
            StockSummary stock = stocks.get(index);
            if (index > 0) {
                pause(KIS_FALLBACK_REQUEST_INTERVAL);
            }
            try {
                prices.addAll(findDailyPricesForCollection(stock, baseDate)
                        .stream()
                        .filter(price -> baseDate.equals(price.tradeDate()))
                        .map(price -> toDailyPrice(stock, price))
                        .toList());
            } catch (RuntimeException exception) {
                log.warn("KIS daily chart collection fallback failed stockCode={} baseDate={}",
                        stock.stockCode(), baseDate, exception);
                failures.add(stock.stockCode() + " " + providerErrorMessage(exception));
            }
        }
        int savedCount = prices.isEmpty() ? 0 : marketDailyPriceRepository.upsertAll(prices);
        String status = failures.isEmpty()
                ? "SUCCESS"
                : savedCount > 0 ? "PARTIAL_FAILED" : "FAILED";
        String errorMessage = failures.isEmpty() ? null : String.join("; ", failures);
        return new CollectionFallbackResult(requestedCount, savedCount, new MarketHistoryCollectionResult.MarketResult(
                KIS_FALLBACK_MARKET,
                requestedCount,
                savedCount,
                status,
                errorMessage));
    }

    private List<KisDailyChartPrice> findDailyPricesForCollection(StockSummary stock, LocalDate baseDate) {
        try {
            return kisDailyChartPriceClient.findDailyPrices(stock.stockCode(), baseDate, baseDate);
        } catch (RuntimeException exception) {
            if (!isKisRateLimitError(exception)) {
                throw exception;
            }
            log.warn("KIS daily chart collection fallback rate limited stockCode={} baseDate={}, retrying once",
                    stock.stockCode(), baseDate);
            pause(KIS_RATE_LIMIT_RETRY_DELAY);
            return kisDailyChartPriceClient.findDailyPrices(stock.stockCode(), baseDate, baseDate);
        }
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
            throw new IllegalStateException("KIS fallback pacing interrupted", exception);
        }
    }

    private static String collectionStatus(List<MarketHistoryCollectionResult.MarketResult> marketResults) {
        long failedCount = marketResults.stream()
                .filter(result -> "FAILED".equals(result.status()))
                .count();
        if (failedCount == 0) {
            return "SUCCESS";
        }
        if (failedCount == marketResults.size()) {
            return "FAILED";
        }
        return "PARTIAL_FAILED";
    }

    private static String source(List<MarketHistoryCollectionResult.MarketResult> marketResults) {
        boolean kisFallbackSucceeded = marketResults.stream()
                .anyMatch(result -> KIS_FALLBACK_MARKET.equals(result.market()) && result.savedCount() > 0);
        return kisFallbackSucceeded ? SOURCE + "+" + KIS_SOURCE : SOURCE;
    }

    private static String providerErrorMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getClass().getSimpleName() + ": " + message;
    }

    private List<MarketDailyPrice> loadKisHistoryFallback(
            StockSummary stock,
            LocalDate from,
            LocalDate to,
            int limit) {
        if (kisDailyChartPriceClient == null) {
            return List.of();
        }
        try {
            List<MarketDailyPrice> prices = findDailyPricesWithRateLimitRetry(stock.stockCode(), from, to)
                    .stream()
                    .map(price -> toDailyPrice(stock, price))
                    .sorted(Comparator.comparing(MarketDailyPrice::tradeDate))
                    .limit(limit)
                    .toList();
            if (!prices.isEmpty()) {
                marketDailyPriceRepository.upsertAll(prices);
            }
            return prices;
        } catch (RuntimeException exception) {
            log.warn("KIS daily chart fallback failed stockCode={} from={} to={}",
                    stock.stockCode(), from, to, exception);
            return List.of();
        }
    }

    private List<KisDailyChartPrice> findDailyPricesWithRateLimitRetry(String stockCode, LocalDate from, LocalDate to) {
        try {
            return kisDailyChartPriceClient.findDailyPrices(stockCode, from, to);
        } catch (RuntimeException exception) {
            if (!isKisRateLimitError(exception)) {
                throw exception;
            }
            log.warn("KIS daily chart fallback rate limited stockCode={} from={} to={}, retrying once",
                    stockCode, from, to);
            pause(KIS_RATE_LIMIT_RETRY_DELAY);
            return kisDailyChartPriceClient.findDailyPrices(stockCode, from, to);
        }
    }

    private MarketDailyPrice toDailyPrice(KrxOpenApiDailyTrade trade) {
        return new MarketDailyPrice(
                trade.stockCode(),
                trade.baseDate(),
                trade.market(),
                trade.openingPriceKrw(),
                trade.highPriceKrw(),
                trade.lowPriceKrw(),
                trade.closingPriceKrw(),
                trade.changeRate(),
                trade.tradingVolume(),
                trade.tradingValueKrw(),
                trade.closingPriceKrw(),
                SOURCE,
                Instant.now(clock));
    }

    private MarketDailyPrice toDailyPrice(StockSummary stock, KisDailyChartPrice price) {
        return new MarketDailyPrice(
                stock.stockCode(),
                price.tradeDate(),
                stock.market(),
                price.openPriceKrw(),
                price.highPriceKrw(),
                price.lowPriceKrw(),
                price.closePriceKrw(),
                price.changeRate(),
                price.tradingVolume(),
                price.tradingValueKrw(),
                price.closePriceKrw(),
                KIS_SOURCE,
                Instant.now(clock));
    }

    private record CollectionFallbackResult(
            int requestedCount,
            int savedCount,
            MarketHistoryCollectionResult.MarketResult marketResult
    ) {
    }

    private record IntradayBackfillPrice(
            KisMinuteChartPrice price,
            String source
    ) {
    }
}
