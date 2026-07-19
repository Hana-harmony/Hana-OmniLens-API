package com.hana.omniconnect.portal;

import java.time.Instant;

public record PortalApiKeyApplication(
        String applicationId,
        String userId,
        String partnerId,
        ApiKeyApplicationStatus status,
        Instant requestedAt,
        Instant reviewedAt,
        String reviewedByUserId,
        String encryptedApiKey,
        String apiKeySha256Prefix,
        String rejectionReason,
        Instant updatedAt
) {
}
