package com.hana.omniconnect.alert.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NewsProcessingScheduler {

    private static final Logger log = LoggerFactory.getLogger(NewsProcessingScheduler.class);
    private final NewsProcessingService processingService;
    private final TaskExecutor processingExecutor;

    public NewsProcessingScheduler(
            NewsProcessingService processingService,
            @Qualifier("newsProcessingExecutor") TaskExecutor processingExecutor) {
        this.processingService = processingService;
        this.processingExecutor = processingExecutor;
    }

    @Scheduled(fixedDelay = 5_000, initialDelay = 30_000, scheduler = "newsTaskScheduler")
    public void processNextNews() {
        try {
            processingExecutor.execute(this::processSafely);
        } catch (TaskRejectedException exception) {
            // 두 분석 슬롯이 사용 중이면 영속 큐에 두고 다음 주기에 처리한다.
            log.debug("News processing workers are busy");
        }
    }

    private void processSafely() {
        try {
            processingService.processNext();
        } catch (RuntimeException exception) {
            // 작업별 실패는 저장된 재시도 상태로 격리하고 다음 스케줄을 유지한다.
            log.warn("News processing scheduler failed", exception);
        }
    }
}
