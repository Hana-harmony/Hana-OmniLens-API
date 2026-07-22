package com.hana.omniconnect.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omni-connect.ai.hannah")
public record HannahAiProperties(
        URI baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Duration taxReadTimeout) {

    public HannahAiProperties {
        baseUrl = baseUrl == null ? URI.create("http://hannah-montana-ai:8000") : baseUrl;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofMinutes(30) : readTimeout;
        taxReadTimeout = taxReadTimeout == null ? Duration.ofSeconds(90) : taxReadTimeout;
    }
}
