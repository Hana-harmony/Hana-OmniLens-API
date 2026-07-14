package com.hana.omnilens.portal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omnilens.portal")
public record PortalProperties(
        String sessionSigningKey,
        String apiKeyEncryptionKey,
        String bootstrapAdminPassword
) {
}
