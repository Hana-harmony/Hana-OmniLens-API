package com.hana.omniconnect.alert.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;

class NewsProcessingSchedulerTest {

    @Test
    void delegatesClaimedJobToDedicatedExecutor() {
        NewsProcessingService processingService = mock(NewsProcessingService.class);
        NewsProcessingScheduler scheduler = new NewsProcessingScheduler(
                processingService,
                new SyncTaskExecutor());

        scheduler.processNextNews();

        verify(processingService).processNext();
    }
}
