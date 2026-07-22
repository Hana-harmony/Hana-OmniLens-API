package com.hana.omniconnect.alert.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface DisclosureProcessingRepository {

    boolean enqueue(DisclosureProcessingJob job, Instant now);

    Optional<DisclosureProcessingJob> claimNext(Instant now, Duration leaseDuration);

    void saveSourceDocument(
            DisclosureProcessingJob job,
            String sourceContent,
            String contentHash,
            String sourceLicensePolicy,
            Instant now);

    void markReady(DisclosureProcessingJob job, String alertId, Instant now);

    void markRejected(DisclosureProcessingJob job, String reason, Instant now);

    void scheduleRetry(DisclosureProcessingJob job, String reason, Instant nextAttemptAt, Instant now);
}
