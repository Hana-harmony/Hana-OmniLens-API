package com.hana.omniconnect.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omni-connect.security")
public record OmniConnectSecurityProperties(
        List<String> corsAllowedOrigins
) {
}
