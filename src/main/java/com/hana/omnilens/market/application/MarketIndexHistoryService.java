package com.hana.omnilens.market.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.hana.omnilens.market.domain.MarketIndexIntradayPrice;
import com.hana.omnilens.provider.market.KisIndexMinuteChartPrice;
import com.hana.omnilens.provider.market.KisIndexMinuteChartPriceClient;
import com.hana.omnilens.provider.market.YahooIndexMinuteChartPriceClient;

@Service
public class MarketIndexHistoryService {

    private static final Logger log = LoggerFactory.getLogger(MarketIndexHistoryService.class);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final String SOURCE = "KIS_TIME_INDEX_CHART_PRICE";
    private static final String FALLBACK_SOURCE = "YAHOO_FINANCE_INDEX_CHART";
    private static final LocalTime REGULAR_MARKET_OPEN = LocalTime.of(9, 0);
    private static final LocalTime REGULAR_MARKET_CLOSE = LocalTime.of(15, 30);
    private static final int FULL_SESSION_LOOKUP_LIMIT = 600;

    private final KisIndexMinuteChartPriceClient kisIndexMinuteChartPriceClient;
    private final YahooIndexMinuteChartPriceClient yahooIndexMinuteChartPriceClient;
    private final MarketIndexSnapshotRepository marketIndexSnapshotRepository;
    private final Clock clock;

    @Autowired
    public MarketIndexHistoryService(
            KisIndexMinuteChartPriceClient kisIndexMinuteChartPriceClient,
            YahooIndexMinuteChartPriceClient yahooIndexMinuteChartPriceClient,
            MarketIndexSnapshotRepository marketIndexSnapshotRepository) {
        this(kisIndexMinuteChartPriceClient, yahooIndexMinuteChartPriceClient, marketIndexSnapshotRepository, Clock.system(KOREA_ZONE));
    }

    MarketIndexHistoryService(KisIndexMinuteChartPriceClient kisIndexMinuteChartPriceClient, Clock clock) {
        this(kisIndexMinuteChartPriceClient, null, new InMemoryMarketIndexSnapshotRepository(), clock);
    }

    MarketIndexHistoryService(
            KisIndexMinuteChartPriceClient kisIndexMinuteChartPriceClient,
            MarketIndexSnapshotRepository marketIndexSnapshotRepository,
            Clock clock) {
        this(kisIndexMinuteChartPriceClient, null, marketIndexSnapshotRepository, clock);
    }

    MarketIndexHistoryService(
            KisIndexMinuteChartPriceClient kisIndexMinuteChartPriceClient,
            YahooIndexMinuteChartPriceClient yahooIndexMinuteChartPriceClient,
            MarketIndexSnapshotRepository marketIndexSnapshotRepository,
            Clock clock) {
        this.kisIndexMinuteChartPriceClient = kisIndexMinuteChartPriceClient;
        this.yahooIndexMinuteChartPriceClient = yahooIndexMinuteChartPriceClient;
        this.marketIndexSnapshotRepository = marketIndexSnapshotRepository;
        this.clock = clock;
    }

    public List<MarketIndexIntradayPrice> getIntradayHistory(String indexCode, LocalDate date, int limit) {
        LocalDate resolvedDate = date == null ? defaultTradingDate(indexCode) : date;
        int lookupLimit = Math.max(Math.max(1, limit), FULL_SESSION_LOOKUP_LIMIT);
        List<MarketIndexIntradayPrice> saved = marketIndexSnapshotRepository.findIntraday(indexCode, resolvedDate, lookupLimit)
                .stream()
                .filter(this::isRegularSessionPrice)
                .filter(this::isPlausiblePrice)
                .toList();
        if (!saved.isEmpty() && !shouldBackfillTodayPartialSeries(resolvedDate, saved, limit)) {
            return mergeByBucket(List.of(), saved, limit);
        }
        List<MarketIndexIntradayPrice> merged = saved;
        try {
            List<MarketIndexIntradayPrice> fetched =
                    kisIndexMinuteChartPriceClient.findMinutePrices(indexCode, resolvedDate, limit)
                            .stream()
                            .map(price -> toIndexIntradayPrice(indexCode, price, SOURCE))
                            .filter(this::isRegularSessionPrice)
                            .filter(this::isPlausiblePrice)
                            .sorted(Comparator.comparing(MarketIndexIntradayPrice::bucketStart))
                            .toList();
            marketIndexSnapshotRepository.upsertIntradayPrices(fetched);
            merged = mergeByBucket(saved, fetched, limit);
        } catch (RuntimeException exception) {
            // 지수 차트 provider 장애는 시장 화면 전체 장애로 전파하지 않는다.
            log.warn("KIS index intraday history fetch failed indexCode={}: {}", indexCode, exception.toString());
        }
        if (hasEnoughTodayPoints(resolvedDate, merged, limit)) {
            return merged;
        }
        return backfillWithYahooIndex(indexCode, resolvedDate, limit, merged);
    }

    private LocalDate defaultTradingDate(String indexCode) {
        LocalDate today = LocalDate.now(clock);
        if (LocalTime.now(clock).isBefore(REGULAR_MARKET_OPEN)) {
            return marketIndexSnapshotRepository.latestTradeDate(indexCode).orElse(today);
        }
        return today;
    }

    private boolean isRegularSessionPrice(MarketIndexIntradayPrice price) {
        LocalTime bucketTime = price.bucketStart().toLocalTime();
        return !bucketTime.isBefore(REGULAR_MARKET_OPEN) && !bucketTime.isAfter(REGULAR_MARKET_CLOSE);
    }

    private boolean isPlausiblePrice(MarketIndexIntradayPrice price) {
        return MarketIndexSanityPolicy.isPlausibleCurrentValue(price.indexCode(), price.closeValue());
    }

    private boolean shouldBackfillTodayPartialSeries(
            LocalDate resolvedDate,
            List<MarketIndexIntradayPrice> saved,
            int limit) {
        if (!resolvedDate.equals(LocalDate.now(clock)) || saved.isEmpty()) {
            return false;
        }
        int expectedPoints = Math.min(Math.max(1, limit), expectedTodayPointCount());
        int minimumPoints = Math.max(2, (int) Math.floor(expectedPoints * 0.75));
        return saved.size() < minimumPoints;
    }

    private int expectedTodayPointCount() {
        LocalTime now = LocalTime.now(clock);
        if (now.isBefore(REGULAR_MARKET_OPEN)) {
            return 0;
        }
        LocalTime cutoff = now.isAfter(REGULAR_MARKET_CLOSE) ? REGULAR_MARKET_CLOSE : now;
        return (int) java.time.Duration.between(REGULAR_MARKET_OPEN, cutoff).toMinutes() + 1;
    }

    private boolean hasEnoughTodayPoints(LocalDate resolvedDate, List<MarketIndexIntradayPrice> prices, int limit) {
        if (!resolvedDate.equals(LocalDate.now(clock))) {
            return !prices.isEmpty();
        }
        int expectedPoints = Math.min(Math.max(1, limit), expectedTodayPointCount());
        int minimumPoints = Math.max(2, (int) Math.floor(expectedPoints * 0.75));
        return prices.size() >= minimumPoints;
    }

    private List<MarketIndexIntradayPrice> backfillWithYahooIndex(
            String indexCode,
            LocalDate resolvedDate,
            int limit,
            List<MarketIndexIntradayPrice> existing) {
        if (yahooIndexMinuteChartPriceClient == null) {
            return existing;
        }
        try {
            List<MarketIndexIntradayPrice> fetched =
                    yahooIndexMinuteChartPriceClient.findMinutePrices(indexCode, resolvedDate, limit)
                            .stream()
                            .map(price -> toIndexIntradayPrice(indexCode, price, FALLBACK_SOURCE))
                            .filter(this::isRegularSessionPrice)
                            .filter(this::isPlausiblePrice)
                            .sorted(Comparator.comparing(MarketIndexIntradayPrice::bucketStart))
                            .toList();
            marketIndexSnapshotRepository.upsertIntradayPrices(fetched);
            return mergeByBucket(existing, fetched, limit);
        } catch (RuntimeException exception) {
            // 보조 지수 차트 provider 장애도 저장된 실제 데이터까지 버리지 않는다.
            log.warn("Yahoo index intraday history fetch failed indexCode={}: {}", indexCode, exception.toString());
            return existing;
        }
    }

    private List<MarketIndexIntradayPrice> mergeByBucket(
            List<MarketIndexIntradayPrice> saved,
            List<MarketIndexIntradayPrice> fetched,
            int limit) {
        Map<java.time.LocalDateTime, MarketIndexIntradayPrice> byBucket = new LinkedHashMap<>();
        List<MarketIndexIntradayPrice> combined = new ArrayList<>(saved);
        combined.addAll(fetched);
        combined.stream()
                .sorted(Comparator.comparing(MarketIndexIntradayPrice::bucketStart))
                .forEach(price -> byBucket.put(price.bucketStart(), price));
        return byBucket.values().stream()
                .sorted(Comparator.comparing(MarketIndexIntradayPrice::bucketStart).reversed())
                .limit(Math.max(1, limit))
                .sorted(Comparator.comparing(MarketIndexIntradayPrice::bucketStart))
                .toList();
    }

    private MarketIndexIntradayPrice toIndexIntradayPrice(
            String indexCode,
            KisIndexMinuteChartPrice price,
            String source) {
        return new MarketIndexIntradayPrice(
                indexCode,
                indexName(indexCode),
                indexMarket(indexCode),
                price.bucketStart(),
                price.openValue(),
                price.highValue(),
                price.lowValue(),
                price.closeValue(),
                price.volume(),
                price.tradingValueKrw(),
                source,
                Instant.now(clock));
    }

    private String indexName(String indexCode) {
        return switch (indexCode) {
            case "0001" -> "KOSPI";
            case "1001" -> "KOSDAQ";
            case "2001" -> "KOSPI 200";
            default -> "Korea Index " + indexCode;
        };
    }

    private String indexMarket(String indexCode) {
        return switch (indexCode) {
            case "1001" -> "KOSDAQ";
            case "2001" -> "KOSPI200";
            default -> "KOSPI";
        };
    }
}
