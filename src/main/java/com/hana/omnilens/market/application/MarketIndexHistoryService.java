package com.hana.omnilens.market.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hana.omnilens.market.domain.MarketIndexIntradayPrice;
import com.hana.omnilens.provider.market.KisIndexMinuteChartPrice;
import com.hana.omnilens.provider.market.KisIndexMinuteChartPriceClient;

@Service
public class MarketIndexHistoryService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final String SOURCE = "KIS_TIME_INDEX_CHART_PRICE";
    private static final LocalTime REGULAR_MARKET_OPEN = LocalTime.of(9, 0);

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
        List<MarketIndexIntradayPrice> saved = marketIndexSnapshotRepository.findIntraday(indexCode, resolvedDate, limit);
        if (!saved.isEmpty()) {
            return saved;
        }
        List<MarketIndexIntradayPrice> fetched = kisIndexMinuteChartPriceClient.findMinutePrices(indexCode, resolvedDate, limit)
                .stream()
                .map(price -> toIndexIntradayPrice(indexCode, price))
                .sorted(Comparator.comparing(MarketIndexIntradayPrice::bucketStart))
                .toList();
        marketIndexSnapshotRepository.upsertIntradayPrices(fetched);
        return fetched;
    }

    private LocalDate defaultTradingDate(String indexCode) {
        LocalDate today = LocalDate.now(clock);
        if (LocalTime.now(clock).isBefore(REGULAR_MARKET_OPEN)) {
            return marketIndexSnapshotRepository.latestTradeDate(indexCode).orElse(today);
        }
        return today;
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
