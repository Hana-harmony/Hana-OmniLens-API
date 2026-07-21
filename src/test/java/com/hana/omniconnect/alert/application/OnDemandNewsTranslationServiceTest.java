package com.hana.omniconnect.alert.application;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import com.hana.omniconnect.alert.domain.AlertEvent;
import com.hana.omniconnect.marketnews.application.MarketNewsCollectionService;

@ExtendWith(MockitoExtension.class)
class OnDemandNewsTranslationServiceTest {

    @Mock
    private AlertAnalysisPublishingService alertService;

    @Mock
    private MarketNewsCollectionService marketNewsService;

    @Mock
    private NewsTranslationEnrichmentAttemptStore attemptStore;

    @Mock
    private AlertEvent event;

    @Test
    void queuesMissingFullArticleWithoutRunningItOnTheRequestThread() {
        CapturingTaskExecutor executor = new CapturingTaskExecutor();
        when(event.alertId()).thenReturn("alert-1");
        when(attemptStore.claim("alert-demand", "alert-1", Duration.ofMinutes(15)))
                .thenReturn(true);
        OnDemandNewsTranslationService service = new OnDemandNewsTranslationService(
                alertService,
                marketNewsService,
                attemptStore,
                executor);

        service.requestAlertTranslation(event);

        verify(alertService, never()).ensureDisplayableFullArticle(event);
        executor.runCaptured();
        verify(alertService).ensureDisplayableFullArticle(event);
    }

    @Test
    void doesNotQueueAnAlreadyTranslatedArticle() {
        when(alertService.isDisplayableFullArticle(event)).thenReturn(true);
        OnDemandNewsTranslationService service = new OnDemandNewsTranslationService(
                alertService,
                marketNewsService,
                attemptStore,
                command -> {
                    throw new AssertionError("translated article must not be queued");
                });

        service.requestAlertTranslation(event);

        verify(attemptStore, never()).claim(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    private static final class CapturingTaskExecutor implements TaskExecutor {
        private Runnable captured;

        @Override
        public void execute(Runnable task) {
            captured = task;
        }

        void runCaptured() {
            captured.run();
        }
    }
}
