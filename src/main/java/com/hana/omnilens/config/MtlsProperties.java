package com.hana.omnilens.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omnilens.security.mtls")
public record MtlsProperties(boolean enabled) {
}
