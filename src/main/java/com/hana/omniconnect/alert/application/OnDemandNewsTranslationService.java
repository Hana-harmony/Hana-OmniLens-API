package com.hana.omniconnect.alert.application;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import com.hana.omniconnect.alert.domain.AlertEvent;
import com.hana.omniconnect.marketnews.application.MarketNewsCollectionService;
import com.hana.omniconnect.marketnews.domain.MarketNewsEvent;

@Service
public class OnDemandNewsTranslationService {

    private static final Logger log = LoggerFactory.getLogger(OnDemandNewsTranslationService.class);
    private static final Duration RETRY_COOLDOWN = Duration.ofMinutes(15);

    private final AlertAnalysisPublishingService alertService;
    private final MarketNewsCollectionService marketNewsService;
    private final NewsTranslationEnrichmentAttemptStore attemptStore;
    private final TaskExecutor executor;
    private final Set<String> running = ConcurrentHashMap.newKeySet();

    public OnDemandNewsTranslationService(
            AlertAnalysisPublishingService alertService,
            MarketNewsCollectionService marketNewsService,
            NewsTranslationEnrichmentAttemptStore attemptStore,
            @Qualifier("onDemandNewsTranslationExecutor") TaskExecutor executor) {
        this.alertService = alertService;
        this.marketNewsService = marketNewsService;
        this.attemptStore = attemptStore;
        this.executor = executor;
    }

    public void requestAlertTranslation(AlertEvent event) {
        if (alertService.isDisplayableFullArticle(event)) {
            return;
        }
        submit("alert", event.alertId(), () -> alertService.ensureDisplayableFullArticle(event));
    }

    public void requestMarketNewsTranslation(MarketNewsEvent event) {
        if (marketNewsService.isDisplayableFullArticle(event)) {
            return;
        }
        submit(
                "market",
                event.newsId(),
                () -> marketNewsService.ensureDisplayableFullArticleByNewsId(event.newsId()));
    }

    private void submit(String source, String eventId, Runnable translation) {
        String runningKey = source + ":" + eventId;
        if (!running.add(runningKey)) {
            return;
        }
        if (!attemptStore.claim(source + "-demand", eventId, RETRY_COOLDOWN)) {
            running.remove(runningKey);
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    translation.run();
                } catch (RuntimeException exception) {
                    log.warn(
                            "On-demand full article translation failed: source={}, eventId={}",
                            source,
                            eventId,
                            exception);
                } finally {
                    running.remove(runningKey);
                }
            });
        } catch (TaskRejectedException exception) {
            running.remove(runningKey);
            attemptStore.release(source + "-demand", eventId);
            log.debug("On-demand full article translation queue is full: source={}", source);
        }
    }
}
