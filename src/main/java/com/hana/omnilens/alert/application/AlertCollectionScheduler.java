package com.hana.omnilens.alert.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.hana.omnilens.alert.api.AlertCollectPublishRequest;
import com.hana.omnilens.config.AlertCollectionSchedulerProperties;
import com.hana.omnilens.config.AlertCollectionSchedulerProperties.PartnerWatchlist;

@Component
public class AlertCollectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertCollectionScheduler.class);

    private final AlertProviderCollectionService alertProviderCollectionService;
    private final AlertCollectionSchedulerProperties properties;

    public AlertCollectionScheduler(
            AlertProviderCollectionService alertProviderCollectionService,
            AlertCollectionSchedulerProperties properties) {
        this.alertProviderCollectionService = alertProviderCollectionService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${omnilens.alert.scheduler.fixed-delay-ms:300000}")
    public void collectConfiguredWatchlists() {
        if (!properties.enabled() || properties.watchlists().isEmpty()) {
            return;
        }

        for (PartnerWatchlist watchlist : properties.watchlists()) {
            collectWatchlist(watchlist);
        }
    }

    private void collectWatchlist(PartnerWatchlist watchlist) {
        if (!StringUtils.hasText(watchlist.partnerId()) || watchlist.stockCodes().isEmpty()) {
            log.warn("Skip alert collection schedule because partner id or stock codes are empty");
            return;
        }

        try {
            alertProviderCollectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                    watchlist.partnerId(),
                    List.copyOf(watchlist.stockCodes()),
                    properties.newsDisplay(),
                    properties.disclosureLookbackDays()));
        } catch (RuntimeException exception) {
            // 장애가 한 협력사의 수집 주기를 전체 스케줄러 중단으로 전파하지 않도록 격리한다.
            log.warn(
                    "Scheduled alert collection failed for partnerId={} stockCount={}",
                    watchlist.partnerId(),
                    watchlist.stockCodes().size(),
                    exception);
        }
    }
}
