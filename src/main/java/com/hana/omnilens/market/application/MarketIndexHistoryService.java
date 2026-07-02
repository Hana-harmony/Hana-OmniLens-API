package com.hana.omnilens.market.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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

    private final KisIndexMinuteChartPriceClient kisIndexMinuteChartPriceClient;
    private final Clock clock;

    @Autowired
    public MarketIndexHistoryService(KisIndexMinuteChartPriceClient kisIndexMinuteChartPriceClient) {
        this(kisIndexMinuteChartPriceClient, Clock.system(KOREA_ZONE));
    }

    MarketIndexHistoryService(KisIndexMinuteChartPriceClient kisIndexMinuteChartPriceClient, Clock clock) {
        this.kisIndexMinuteChartPriceClient = kisIndexMinuteChartPriceClient;
        this.clock = clock;
    }

    public List<MarketIndexIntradayPrice> getIntradayHistory(String indexCode, LocalDate date, int limit) {
        LocalDate resolvedDate = date == null ? LocalDate.now(clock) : date;
        return kisIndexMinuteChartPriceClient.findMinutePrices(indexCode, resolvedDate, limit)
                .stream()
                .map(price -> toIndexIntradayPrice(indexCode, price))
                .sorted(Comparator.comparing(MarketIndexIntradayPrice::bucketStart))
                .toList();
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
