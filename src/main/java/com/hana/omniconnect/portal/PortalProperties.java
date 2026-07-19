package com.hana.omniconnect.portal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omni-connect.portal")
public record PortalProperties(
        String sessionSigningKey,
        String apiKeyEncryptionKey,
        String bootstrapAdminPassword
) {
}
