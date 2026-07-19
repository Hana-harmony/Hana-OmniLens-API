package com.hana.omniconnect.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "omni-connect.providers.krx-open-api")
public record KrxOpenApiProperties(
        URI baseUrl,
        String authKey
) {

    public KrxOpenApiProperties {
        baseUrl = baseUrl == null ? URI.create("https://data-dbg.krx.co.kr") : baseUrl;
        authKey = authKey == null ? "" : authKey;
    }

    public String requiredAuthKey() {
        if (!StringUtils.hasText(authKey)) {
            throw new IllegalStateException("omni-connect.providers.krx-open-api.auth-key is not configured");
        }
        return authKey;
    }
}
