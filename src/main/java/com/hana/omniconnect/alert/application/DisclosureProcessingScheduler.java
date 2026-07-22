package com.hana.omniconnect.alert.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DisclosureProcessingScheduler {

    private static final Logger log = LoggerFactory.getLogger(DisclosureProcessingScheduler.class);
    private final DisclosureProcessingService processingService;

    public DisclosureProcessingScheduler(DisclosureProcessingService processingService) {
        this.processingService = processingService;
    }

    @Scheduled(
            fixedDelay = 5_000,
            initialDelay = 30_000,
            scheduler = "disclosureTaskScheduler")
    public void processNextDisclosure() {
        try {
            processingService.processNext();
        } catch (RuntimeException exception) {
            // 작업별 실패는 저장된 재시도 상태로 격리하고 다음 스케줄을 유지한다.
            log.warn("Disclosure processing scheduler failed", exception);
        }
    }
}
