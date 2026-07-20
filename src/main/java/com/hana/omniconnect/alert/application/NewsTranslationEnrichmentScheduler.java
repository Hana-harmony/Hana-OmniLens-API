package com.hana.omniconnect.alert.application;

import java.util.concurrent.atomic.AtomicBoolean;

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

    private final AlertAnalysisPublishingService alertService;
    private final MarketNewsCollectionService marketNewsService;
    private final TaskExecutor executor;
    private final AtomicBoolean alertRunning = new AtomicBoolean();
    private final AtomicBoolean marketNewsRunning = new AtomicBoolean();

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
        dispatch(alertRunning, "stock alert", alertService::enrichNextPendingFullTranslation);
        dispatch(marketNewsRunning, "market news", marketNewsService::enrichNextPendingFullTranslation);
    }

    private void dispatch(AtomicBoolean running, String source, Runnable enrichment) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    enrichment.run();
                } catch (RuntimeException exception) {
                    log.warn("News full translation enrichment failed: source={}", source, exception);
                } finally {
                    running.set(false);
                }
            });
        } catch (TaskRejectedException exception) {
            running.set(false);
            log.debug("News full translation enrichment executor is busy: source={}", source);
        }
    }
}
