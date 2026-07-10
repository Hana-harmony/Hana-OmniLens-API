package com.hana.omnilens.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omnilens.ai.hannah")
public record HannahAiProperties(
        URI baseUrl,
        Duration connectTimeout,
        Duration readTimeout) {

    public HannahAiProperties {
        baseUrl = baseUrl == null ? URI.create("http://hannah-montana-ai:8000") : baseUrl;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofMinutes(30) : readTimeout;
    }
}
