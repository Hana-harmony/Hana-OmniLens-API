package com.hana.omniconnect.alert.application;

import java.time.Instant;

public record NewsProcessingJob(
        String jobId,
        String partnerId,
        String stockCode,
        String title,
        String snippet,
        String originalUrl,
        Instant publishedAt,
        String sourceContent,
        String imageUrls,
        String canonicalUrl,
        String contentHash,
        String sourceLicensePolicy,
        String status,
        int attemptCount,
        String leaseToken) {
}
