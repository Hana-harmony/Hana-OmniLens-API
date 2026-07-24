package com.hana.omniconnect.alert.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.hana.omniconnect.alert.api.AlertCollectPublishRequest;
import com.hana.omniconnect.config.AlertCollectionSchedulerProperties;

@Component
public class AlertCollectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertCollectionScheduler.class);

    private final AlertProviderCollectionService alertProviderCollectionService;
    private final AlertCollectionSchedulerProperties properties;
    private final PartnerWatchlistRepository partnerWatchlistRepository;
    private final AlertCollectionTargetUniverseProvider targetUniverseProvider;
    private final TaskExecutor collectionExecutor;
    private final Set<String> collectionsInFlight = ConcurrentHashMap.newKeySet();

    @Autowired
    public AlertCollectionScheduler(
            AlertProviderCollectionService alertProviderCollectionService,
            AlertCollectionSchedulerProperties properties,
            PartnerWatchlistRepository partnerWatchlistRepository,
            AlertCollectionTargetUniverseProvider targetUniverseProvider,
            @Qualifier("alertCollectionExecutor") TaskExecutor collectionExecutor) {
        this.alertProviderCollectionService = alertProviderCollectionService;
        this.properties = properties;
        this.partnerWatchlistRepository = partnerWatchlistRepository;
        this.targetUniverseProvider = targetUniverseProvider;
        this.collectionExecutor = collectionExecutor;
    }

    AlertCollectionScheduler(
            AlertProviderCollectionService alertProviderCollectionService,
            AlertCollectionSchedulerProperties properties,
            PartnerWatchlistRepository partnerWatchlistRepository,
            AlertCollectionTargetUniverseProvider targetUniverseProvider) {
        this(
                alertProviderCollectionService,
                properties,
                partnerWatchlistRepository,
                targetUniverseProvider,
                new SyncTaskExecutor());
    }

    @Scheduled(
            fixedDelayString = "${omni-connect.alert.scheduler.fixed-delay-ms:600000}",
            initialDelayString = "${omni-connect.alert.scheduler.initial-delay-ms:60000}")
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
        if (properties.defaultUniverseEnabled()) {
            addWatchlist(stockCodesByPartner, new PartnerWatchlist(
                    properties.defaultUniversePartnerId(),
                    targetUniverseProvider.defaultStockCodes(
                            properties.priorityStockLimit(),
                            properties.includeForeignOwnershipRestrictedStocks())));
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

        for (List<String> stockCodes : batches(watchlist.stockCodes())) {
            scheduleBatch(watchlist.partnerId(), stockCodes);
        }
    }

    private void scheduleBatch(String partnerId, List<String> stockCodes) {
        String collectionKey = partnerId + ":" + String.join(",", stockCodes);
        if (!collectionsInFlight.add(collectionKey)) {
            log.info("Skip overlapping alert collection partnerId={} stockCount={}", partnerId, stockCodes.size());
            return;
        }
        try {
            collectionExecutor.execute(() -> {
                try {
                    collectBatch(partnerId, stockCodes);
                } finally {
                    collectionsInFlight.remove(collectionKey);
                }
            });
        } catch (RejectedExecutionException exception) {
            collectionsInFlight.remove(collectionKey);
            log.warn("Alert collection queue rejected partnerId={} stockCount={}", partnerId, stockCodes.size(), exception);
        }
    }

    private void collectBatch(String partnerId, List<String> stockCodes) {
        try {
            alertProviderCollectionService.collectIncrementalAnalyzeAndPublish(new AlertCollectPublishRequest(
                    partnerId,
                    stockCodes,
                    properties.newsDisplay(),
                    properties.disclosureLookbackDays()));
        } catch (RuntimeException exception) {
            // provider 장애가 다른 협력사나 남은 배치 수집으로 전파되지 않도록 격리한다.
            log.warn(
                    "Scheduled alert collection failed for partnerId={} stockCount={} firstStockCode={}",
                    partnerId,
                    stockCodes.size(),
                    stockCodes.isEmpty() ? "" : stockCodes.get(0),
                    exception);
        }
    }

    private List<List<String>> batches(List<String> stockCodes) {
        int batchSize = properties.collectionBatchSize();
        List<List<String>> batches = new ArrayList<>();
        for (int start = 0; start < stockCodes.size(); start += batchSize) {
            int end = Math.min(start + batchSize, stockCodes.size());
            batches.add(List.copyOf(stockCodes.subList(start, end)));
        }
        return batches;
    }
}
