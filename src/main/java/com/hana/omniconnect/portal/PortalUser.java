package com.hana.omniconnect.portal;

import java.time.Instant;

public record PortalUser(
        String userId,
        String username,
        String passwordHash,
        String displayName,
        String phoneNumber,
        PortalRole role,
        Instant createdAt,
        Instant updatedAt,
        boolean passwordChangeRequired,
        long sessionVersion,
        Instant passwordChangedAt
) {
}
