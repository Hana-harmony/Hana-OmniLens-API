package com.hana.omniconnect.alert.application;

import java.time.Instant;

public record DisclosureProcessingJob(
        String jobId,
        String partnerId,
        String stockCode,
        String receiptNumber,
        String corporationName,
        String reportName,
        String originalUrl,
        Instant publishedAt,
        String sourceContent,
        String contentHash,
        String sourceLicensePolicy,
        String status,
        int attemptCount,
        String leaseToken) {

    public boolean hasSourceContent() {
        return sourceContent != null && !sourceContent.isBlank();
    }
}
