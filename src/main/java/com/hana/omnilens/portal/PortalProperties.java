package com.hana.omnilens.portal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omnilens.portal")
public record PortalProperties(
        String sessionSigningKey,
        String apiKeyEncryptionKey,
        String bootstrapAdminUsername,
        String bootstrapAdminPassword,
        String bootstrapAdminName,
        String bootstrapAdminPhone
) {
}
