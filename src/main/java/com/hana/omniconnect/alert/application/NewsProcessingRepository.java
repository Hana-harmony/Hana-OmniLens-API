package com.hana.omniconnect.alert.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface NewsProcessingRepository {

    boolean enqueue(NewsProcessingJob job, Instant now);

    Optional<NewsProcessingJob> claimNext(Instant now, Duration leaseDuration);

    void markReady(NewsProcessingJob job, String alertId, Instant now);

    void markRejected(NewsProcessingJob job, String reason, Instant now);

    void scheduleRetry(NewsProcessingJob job, String reason, Instant nextAttemptAt, Instant now);
}
