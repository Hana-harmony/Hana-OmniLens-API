package com.hana.omnilens.marketnews.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.hana.omnilens.config.MarketNewsCollectionProperties;

@Component
public class MarketNewsCollectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketNewsCollectionScheduler.class);

    private final MarketNewsCollectionService marketNewsCollectionService;
    private final MarketNewsCollectionProperties properties;

    public MarketNewsCollectionScheduler(
            MarketNewsCollectionService marketNewsCollectionService,
            MarketNewsCollectionProperties properties) {
        this.marketNewsCollectionService = marketNewsCollectionService;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${omnilens.market-news.scheduler.fixed-delay-ms:300000}",
            initialDelayString = "${omnilens.market-news.scheduler.initial-delay-ms:60000}")
    public void collectMarketNews() {
        if (!properties.enabled()) {
            return;
        }
        try {
            marketNewsCollectionService.collectConfiguredQueries();
        } catch (RuntimeException exception) {
            // 시장뉴스 수집 장애가 API 프로세스 전체로 전파되지 않게 격리한다.
            log.warn("Scheduled market news collection failed: {}", exception.toString());
        }
    }
}
