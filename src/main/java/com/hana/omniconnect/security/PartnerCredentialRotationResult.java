package com.hana.omniconnect.security;

import java.time.Instant;

public record PartnerCredentialRotationResult(
        String partnerId,
        String apiKey,
        String apiKeySha256Prefix,
        Instant rotatedAt,
        int deactivatedCredentialCount
) {
}
