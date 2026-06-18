package com.hana.omnilens.market.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.hana.omnilens.config.MarketHistoryCollectionProperties;

@Component
public class MarketHistoryCollectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketHistoryCollectionScheduler.class);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final MarketHistoryService marketHistoryService;
    private final MarketHistoryCollectionProperties properties;
    private final Clock clock;

    @Autowired
    public MarketHistoryCollectionScheduler(
            MarketHistoryService marketHistoryService,
            MarketHistoryCollectionProperties properties) {
        this(marketHistoryService, properties, Clock.system(KOREA_ZONE));
    }

    MarketHistoryCollectionScheduler(
            MarketHistoryService marketHistoryService,
            MarketHistoryCollectionProperties properties,
            Clock clock) {
        this.marketHistoryService = marketHistoryService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${omnilens.market.history-collection.fixed-delay-ms:86400000}")
    public void collectConfiguredHistory() {
        if (!properties.enabled()) {
            return;
        }

        LocalDate baseDate = LocalDate.now(clock).minusDays(properties.baseDateOffsetDays());
        try {
            marketHistoryService.collectDailyHistory(baseDate);
        } catch (RuntimeException exception) {
            log.warn("Scheduled KRX market history collection failed baseDate={}", baseDate, exception);
        }
    }
}
