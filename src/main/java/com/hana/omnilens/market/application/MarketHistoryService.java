package com.hana.omnilens.market.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

@Service
public class MarketHistoryService {

    private static final Logger log = LoggerFactory.getLogger(MarketHistoryService.class);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final String SOURCE = "KRX_OPEN_API_DAILY_TRADE";
    private static final String KIS_SOURCE = "KIS_DAILY_ITEM_CHART_PRICE";
    private static final String KIS_FALLBACK_MARKET = "KIS_DAILY_CHART";
    private static final int KIS_FALLBACK_STOCK_LIMIT = 2_000;
    private static final Duration KIS_FALLBACK_REQUEST_INTERVAL = Duration.ofMillis(1_200);
    private static final Duration KIS_RATE_LIMIT_RETRY_DELAY = Duration.ofMillis(1_200);
    private static final Duration INTRADAY_CACHE_FRESHNESS = Duration.ofSeconds(60);
    private static final List<String> MARKETS = List.of("KOSPI", "KOSDAQ", "KONEX");

    private final KrxOpenApiDailyTradeClient krxOpenApiDailyTradeClient;
    private final KisDailyChartPriceClient kisDailyChartPriceClient;
    private final KisMinuteChartPriceClient kisMinuteChartPriceClient;
    private final MarketDailyPriceRepository marketDailyPriceRepository;
    private final MarketIntradayPriceRepository marketIntradayPriceRepository;
    private final StockMasterRepository stockMasterRepository;
    private final MarketHistoryCollectionProperties collectionProperties;
    private final Clock clock;

    @Autowired
    public MarketHistoryService(
            KrxOpenApiDailyTradeClient krxOpenApiDailyTradeClient,
            KisDailyChartPriceClient kisDailyChartPriceClient,
            KisMinuteChartPriceClient kisMinuteChartPriceClient,
            MarketDailyPriceRepository marketDailyPriceRepository,
            MarketIntradayPriceRepository marketIntradayPriceRepository,
            StockMasterRepository stockMasterRepository,
            MarketHistoryCollectionProperties collectionProperties) {
        this(
                krxOpenApiDailyTradeClient,
                kisDailyChartPriceClient,
                kisMinuteChartPriceClient,
                marketDailyPriceRepository,
                marketIntradayPriceRepository,
                stockMasterRepository,
                collectionProperties,
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
                marketDailyPriceRepository,
                null,
                stockMasterRepository,
                collectionProperties,
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
            Clock clock) {
        this.krxOpenApiDailyTradeClient = krxOpenApiDailyTradeClient;
        this.kisDailyChartPriceClient = kisDailyChartPriceClient;
        this.kisMinuteChartPriceClient = kisMinuteChartPriceClient;
        this.marketDailyPriceRepository = marketDailyPriceRepository;
        this.marketIntradayPriceRepository = marketIntradayPriceRepository;
        this.stockMasterRepository = stockMasterRepository;
        this.collectionProperties = collectionProperties == null
                ? new MarketHistoryCollectionProperties(false, 86_400_000L, 1, null)
                : collectionProperties;
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
        if (!savedHistory.isEmpty()) {
            return savedHistory;
        }
        return loadKisHistoryFallback(stock, resolvedFrom, resolvedTo, limit);
    }

    public List<MarketIntradayPrice> getIntradayHistory(String stockCode, LocalDate date, int limit) {
        StockSummary stock = stockMasterRepository.findByCode(stockCode)
                .orElseThrow(() -> new StockMasterNotFoundException(stockCode));
        LocalDate resolvedDate = date == null ? LocalDate.now(clock) : date;
        List<MarketIntradayPrice> savedPrices = findSavedIntradayPrices(stockCode, resolvedDate, limit);
        if (isReusableIntradayCache(savedPrices, resolvedDate)) {
            return savedPrices;
        }
        if (kisMinuteChartPriceClient == null) {
            return savedPrices;
        }
        List<MarketIntradayPrice> fetchedPrices = kisMinuteChartPriceClient.findMinutePrices(stockCode, resolvedDate, limit)
                .stream()
                .map(price -> toIntradayPrice(stock, price))
                .toList();
        if (marketIntradayPriceRepository != null && !fetchedPrices.isEmpty()) {
            marketIntradayPriceRepository.upsertAll(fetchedPrices);
            return findSavedIntradayPrices(stockCode, resolvedDate, limit);
        }
        return fetchedPrices.isEmpty() ? savedPrices : fetchedPrices;
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
        Instant newestCollectionTime = prices.stream()
                .map(MarketIntradayPrice::collectedAt)
                .max(Comparator.naturalOrder())
                .orElse(Instant.EPOCH);
        return newestCollectionTime.plus(INTRADAY_CACHE_FRESHNESS).isAfter(Instant.now(clock));
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
            List<MarketDailyPrice> prices = kisDailyChartPriceClient
                    .findDailyPrices(stock.stockCode(), from, to)
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

    private MarketIntradayPrice toIntradayPrice(StockSummary stock, KisMinuteChartPrice price) {
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
                "KIS_TIME_ITEM_CHART_PRICE",
                Instant.now(clock));
    }

    private record CollectionFallbackResult(
            int requestedCount,
            int savedCount,
            MarketHistoryCollectionResult.MarketResult marketResult
    ) {
    }
}
