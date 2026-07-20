package com.hana.omniconnect.alert.application;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;

import com.hana.omniconnect.marketnews.application.MarketNewsCollectionService;

@ExtendWith(MockitoExtension.class)
class NewsTranslationEnrichmentSchedulerTest {

    @Mock
    private AlertAnalysisPublishingService alertService;

    @Mock
    private MarketNewsCollectionService marketNewsService;

    @Test
    void drainsAlertAndMarketNewsTranslationQueuesOutsideTheSchedulerThread() {
        when(alertService.enrichNextPendingFullTranslation())
                .thenReturn(Optional.of(org.mockito.Mockito.mock(com.hana.omniconnect.alert.domain.AlertEvent.class)))
                .thenReturn(Optional.empty());
        when(marketNewsService.enrichNextPendingFullTranslation())
                .thenReturn(Optional.of(org.mockito.Mockito.mock(
                        com.hana.omniconnect.marketnews.domain.MarketNewsEvent.class)))
                .thenReturn(Optional.empty());
        NewsTranslationEnrichmentScheduler scheduler = new NewsTranslationEnrichmentScheduler(
                alertService,
                marketNewsService,
                new SyncTaskExecutor());

        scheduler.enrichPendingTranslations();

        verify(alertService, org.mockito.Mockito.times(2)).enrichNextPendingFullTranslation();
        verify(marketNewsService, org.mockito.Mockito.times(2)).enrichNextPendingFullTranslation();
    }
}
