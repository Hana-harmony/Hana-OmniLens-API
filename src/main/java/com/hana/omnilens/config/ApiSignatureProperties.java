package com.hana.omnilens.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "omnilens.security.signature")
public record ApiSignatureProperties(
        boolean enabled,
        String secret,
        Duration allowedClockSkew,
        NonceStoreMode nonceStoreMode,
        int maxNonces
) {

    public ApiSignatureProperties {
        secret = secret == null ? "" : secret;
        allowedClockSkew = allowedClockSkew == null ? Duration.ofMinutes(5) : allowedClockSkew;
        nonceStoreMode = nonceStoreMode == null ? NonceStoreMode.REDIS : nonceStoreMode;
        maxNonces = maxNonces <= 0 ? 10_000 : maxNonces;
    }

    public String requiredSecret() {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("omnilens.security.signature.secret is not configured");
        }
        return secret;
    }

    public enum NonceStoreMode {
        REDIS,
        IN_MEMORY
    }
}
