package com.hana.omnilens.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix = "omnilens.security.signature")
public record ApiSignatureProperties(
        boolean enabled,
        Duration allowedClockSkew,
        NonceStoreMode nonceStoreMode,
        int maxNonces
) {

    public ApiSignatureProperties {
        allowedClockSkew = allowedClockSkew == null ? Duration.ofMinutes(2) : allowedClockSkew;
        nonceStoreMode = nonceStoreMode == null ? NonceStoreMode.REDIS : nonceStoreMode;
        maxNonces = maxNonces <= 0 ? 10_000 : maxNonces;
    }

    public enum NonceStoreMode {
        REDIS,
        IN_MEMORY
    }
}
