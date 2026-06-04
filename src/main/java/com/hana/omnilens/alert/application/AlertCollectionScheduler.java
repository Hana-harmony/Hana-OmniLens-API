package com.hana.omnilens.alert.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.hana.omnilens.alert.api.AlertCollectPublishRequest;
import com.hana.omnilens.config.AlertCollectionSchedulerProperties;

@Component
public class AlertCollectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertCollectionScheduler.class);

    private final AlertProviderCollectionService alertProviderCollectionService;
    private final AlertCollectionSchedulerProperties properties;
    private final PartnerWatchlistRepository partnerWatchlistRepository;

    public AlertCollectionScheduler(
            AlertProviderCollectionService alertProviderCollectionService,
            AlertCollectionSchedulerProperties properties,
            PartnerWatchlistRepository partnerWatchlistRepository) {
        this.alertProviderCollectionService = alertProviderCollectionService;
        this.properties = properties;
        this.partnerWatchlistRepository = partnerWatchlistRepository;
    }

    @Scheduled(fixedDelayString = "${omnilens.alert.scheduler.fixed-delay-ms:300000}")
    public void collectConfiguredWatchlists() {
        if (!properties.enabled()) {
            return;
        }

        for (PartnerWatchlist watchlist : mergedWatchlists()) {
            collectWatchlist(watchlist);
        }
    }

    private List<PartnerWatchlist> mergedWatchlists() {
        Map<String, Set<String>> stockCodesByPartner = new LinkedHashMap<>();
        for (AlertCollectionSchedulerProperties.PartnerWatchlist watchlist : properties.watchlists()) {
            addWatchlist(stockCodesByPartner, new PartnerWatchlist(watchlist.partnerId(), watchlist.stockCodes()));
        }
        for (PartnerWatchlist watchlist : partnerWatchlistRepository.findAll()) {
            addWatchlist(stockCodesByPartner, watchlist);
        }
        List<PartnerWatchlist> watchlists = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : stockCodesByPartner.entrySet()) {
            watchlists.add(new PartnerWatchlist(entry.getKey(), List.copyOf(entry.getValue())));
        }
        return watchlists;
    }

    private static void addWatchlist(Map<String, Set<String>> stockCodesByPartner, PartnerWatchlist watchlist) {
        if (!StringUtils.hasText(watchlist.partnerId())) {
            return;
        }
        stockCodesByPartner
                .computeIfAbsent(watchlist.partnerId(), ignored -> new LinkedHashSet<>())
                .addAll(watchlist.stockCodes());
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
