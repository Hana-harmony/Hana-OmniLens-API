package com.hana.omniconnect.portal;

import java.time.Duration;
import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PortalSessionRetentionWorker {

    private static final Duration RETENTION_AFTER_EXPIRY = Duration.ofDays(30);

    private final PortalSessionRepository sessionRepository;

    public PortalSessionRetentionWorker(PortalSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Scheduled(cron = "${omni-connect.portal.session-cleanup-cron:0 20 3 * * *}")
    public void deleteExpiredSessions() {
        sessionRepository.deleteExpiredBefore(Instant.now().minus(RETENTION_AFTER_EXPIRY));
    }
}
