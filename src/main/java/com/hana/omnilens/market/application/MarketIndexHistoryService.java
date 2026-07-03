package com.hana.omnilens.market.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.hana.omnilens.market.domain.MarketIndexIntradayPrice;
import com.hana.omnilens.provider.market.KisIndexMinuteChartPrice;
import com.hana.omnilens.provider.market.KisIndexMinuteChartPriceClient;

@Service
public class MarketIndexHistoryService {

    private static final Logger log = LoggerFactory.getLogger(MarketIndexHistoryService.class);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final String SOURCE = "KIS_TIME_INDEX_CHART_PRICE";
    private static final LocalTime REGULAR_MARKET_OPEN = LocalTime.of(9, 0);
    private static final LocalTime REGULAR_MARKET_CLOSE = LocalTime.of(15, 30);

    private final KisIndexMinuteChartPriceClient kisIndexMinuteChartPriceClient;
    private final MarketIndexSnapshotRepository marketIndexSnapshotRepository;
    private final Clock clock;

    @Autowired
    public MarketIndexHistoryService(
            KisIndexMinuteChartPriceClient kisIndexMinuteChartPriceClient,
            MarketIndexSnapshotRepository marketIndexSnapshotRepository) {
        this(kisIndexMinuteChartPriceClient, marketIndexSnapshotRepository, Clock.system(KOREA_ZONE));
    }

    MarketIndexHistoryService(KisIndexMinuteChartPriceClient kisIndexMinuteChartPriceClient, Clock clock) {
        this(kisIndexMinuteChartPriceClient, new InMemoryMarketIndexSnapshotRepository(), clock);
    }

    MarketIndexHistoryService(
            KisIndexMinuteChartPriceClient kisIndexMinuteChartPriceClient,
            MarketIndexSnapshotRepository marketIndexSnapshotRepository,
            Clock clock) {
        this.kisIndexMinuteChartPriceClient = kisIndexMinuteChartPriceClient;
        this.marketIndexSnapshotRepository = marketIndexSnapshotRepository;
        this.clock = clock;
    }

    public List<MarketIndexIntradayPrice> getIntradayHistory(String indexCode, LocalDate date, int limit) {
        LocalDate resolvedDate = date == null ? defaultTradingDate(indexCode) : date;
        List<MarketIndexIntradayPrice> saved = marketIndexSnapshotRepository.findIntraday(indexCode, resolvedDate, limit)
                .stream()
                .filter(this::isRegularSessionPrice)
                .filter(this::isPlausiblePrice)
                .toList();
        if (!saved.isEmpty()) {
            return saved;
        }
        try {
            List<MarketIndexIntradayPrice> fetched =
                    kisIndexMinuteChartPriceClient.findMinutePrices(indexCode, resolvedDate, limit)
                            .stream()
                            .map(price -> toIndexIntradayPrice(indexCode, price))
                            .filter(this::isRegularSessionPrice)
                            .filter(this::isPlausiblePrice)
                            .sorted(Comparator.comparing(MarketIndexIntradayPrice::bucketStart))
                            .toList();
            marketIndexSnapshotRepository.upsertIntradayPrices(fetched);
            return fetched;
        } catch (RuntimeException exception) {
            // 지수 차트 provider 장애는 시장 화면 전체 장애로 전파하지 않는다.
            log.warn("KIS index intraday history fetch failed indexCode={}: {}", indexCode, exception.toString());
            return List.of();
        }
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

    private MarketIndexIntradayPrice toIndexIntradayPrice(String indexCode, KisIndexMinuteChartPrice price) {
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
                SOURCE,
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
