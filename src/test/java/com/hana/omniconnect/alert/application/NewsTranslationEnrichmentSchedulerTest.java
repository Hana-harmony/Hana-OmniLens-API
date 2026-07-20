package com.hana.omniconnect.alert.application;

import static org.mockito.Mockito.verify;

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
    void enrichesOneAlertAndOneMarketNewsItemOutsideTheSchedulerThread() {
        NewsTranslationEnrichmentScheduler scheduler = new NewsTranslationEnrichmentScheduler(
                alertService,
                marketNewsService,
                new SyncTaskExecutor());

        scheduler.enrichPendingTranslations();

        verify(alertService).enrichNextPendingFullTranslation();
        verify(marketNewsService).enrichNextPendingFullTranslation();
    }
}
