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
        // Qwen의 동시 추론 한도 2개에 맞춰 종목 뉴스와 시장 뉴스를 한 건씩 병렬 처리한다.
        dispatch(alertRunning, alertService::enrichNextPendingFullTranslation);
        dispatch(marketNewsRunning, marketNewsService::enrichNextPendingFullTranslation);
    }

    private void dispatch(AtomicBoolean running, Supplier<Optional<?>> enrichment) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    enrichment.get();
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
