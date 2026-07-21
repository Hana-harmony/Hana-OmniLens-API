package com.hana.omniconnect.alert.application;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.hana.omniconnect.marketnews.application.MarketNewsCollectionService;

@Component
public class NewsTranslationEnrichmentScheduler {

    private static final Logger log = LoggerFactory.getLogger(NewsTranslationEnrichmentScheduler.class);
    private static final int MAX_DRAIN_BATCH = 10;

    private final AlertAnalysisPublishingService alertService;
    private final MarketNewsCollectionService marketNewsService;
    private final TaskExecutor executor;
    private final AtomicBoolean running = new AtomicBoolean();

    public NewsTranslationEnrichmentScheduler(
            AlertAnalysisPublishingService alertService,
            MarketNewsCollectionService marketNewsService,
            @Qualifier("newsTranslationEnrichmentExecutor") TaskExecutor executor) {
        this.alertService = alertService;
        this.marketNewsService = marketNewsService;
        this.executor = executor;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 120_000)
    public void enrichPendingTranslations() {
        dispatch(this::drainPendingAlternating);
    }

    private void drainPendingAlternating() {
        int processed = 0;
        while (processed < MAX_DRAIN_BATCH) {
            boolean found = false;
            if (processed < MAX_DRAIN_BATCH && drainOne(alertService::enrichNextPendingFullTranslation)) {
                processed++;
                found = true;
            }
            if (processed < MAX_DRAIN_BATCH && drainOne(marketNewsService::enrichNextPendingFullTranslation)) {
                processed++;
                found = true;
            }
            if (!found) {
                return;
            }
        }
    }

    private boolean drainOne(Supplier<Optional<?>> enrichment) {
        Optional<?> result = enrichment.get();
        return result != null && result.isPresent();
    }

    private void dispatch(Runnable enrichment) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    enrichment.run();
                } catch (RuntimeException exception) {
                    log.warn("News full translation enrichment failed", exception);
                } finally {
                    running.set(false);
                }
            });
        } catch (TaskRejectedException exception) {
            running.set(false);
            log.debug("News full translation enrichment executor is busy");
        }
    }

}
