package com.hana.omnilens.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omnilens.ai.hannah")
public record HannahAiProperties(URI baseUrl) {

    public HannahAiProperties {
        baseUrl = baseUrl == null ? URI.create("http://hannah-montana-ai:8000") : baseUrl;
    }
}
