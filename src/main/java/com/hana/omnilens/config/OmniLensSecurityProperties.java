package com.hana.omnilens.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omnilens.security")
public record OmniLensSecurityProperties(
        List<String> corsAllowedOrigins
) {
}
