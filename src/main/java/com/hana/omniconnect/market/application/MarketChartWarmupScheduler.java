package com.hana.omniconnect.market.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.hana.omniconnect.config.MarketChartWarmupProperties;

@Component
public class MarketChartWarmupScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketChartWarmupScheduler.class);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final MarketHistoryService marketHistoryService;
    private final MarketChartWarmupProperties properties;
    private final Clock clock;

    @Autowired
    public MarketChartWarmupScheduler(
            MarketHistoryService marketHistoryService,
            MarketChartWarmupProperties properties) {
        this(marketHistoryService, properties, Clock.system(KOREA_ZONE));
    }

    MarketChartWarmupScheduler(
            MarketHistoryService marketHistoryService,
            MarketChartWarmupProperties properties,
            Clock clock) {
        this.marketHistoryService = marketHistoryService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${omni-connect.market.chart-warmup.fixed-delay-ms:300000}",
            initialDelayString = "${omni-connect.market.chart-warmup.initial-delay-ms:10000}")
    public void warmupConfiguredCharts() {
        if (!properties.isEnabled()) {
            return;
        }
        LocalDate baseDate = LocalDate.now(clock).minusDays(properties.baseDateOffsetDays());
        try {
            MarketChartWarmupResult result = marketHistoryService.warmupChartHistory(baseDate);
            log.info("Market chart warmup completed baseDate={} stockCount={} dailyPoints={} intradayPoints={} status={}",
                    result.baseDate(),
                    result.requestedStockCount(),
                    result.dailyPointCount(),
                    result.intradayPointCount(),
                    result.status());
        } catch (RuntimeException exception) {
            log.warn("Market chart warmup failed baseDate={}", baseDate, exception);
        }
    }
}
