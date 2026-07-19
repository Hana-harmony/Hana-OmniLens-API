package com.hana.omniconnect.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omni-connect.providers.frankfurter")
public record FrankfurterProperties(URI baseUrl) {

    public FrankfurterProperties {
        baseUrl = baseUrl == null ? URI.create("https://api.frankfurter.dev") : baseUrl;
    }
}
