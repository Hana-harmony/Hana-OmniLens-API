package com.hana.omnilens.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omnilens.providers.frankfurter")
public record FrankfurterProperties(URI baseUrl) {

    public FrankfurterProperties {
        baseUrl = baseUrl == null ? URI.create("https://api.frankfurter.dev") : baseUrl;
    }
}
