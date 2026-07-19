package com.hana.omniconnect.provider.market;

import java.time.Clock;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.hana.omniconnect.config.ExternalProviderProperties;
import com.hana.omniconnect.provider.ExternalProviderResiliencePolicy;

@Component
public class KisAccessTokenProvider {

    private final RestClient restClient;
    private final ExternalProviderProperties.Kis properties;
    private final ExternalProviderResiliencePolicy resiliencePolicy;
    private final Clock clock;

    private String cachedToken = "";
    private Instant expiresAt = Instant.EPOCH;

    @Autowired
    public KisAccessTokenProvider(
            RestClient.Builder restClientBuilder,
            ExternalProviderProperties properties,
            ExternalProviderResiliencePolicy resiliencePolicy) {
        this(restClientBuilder, properties.kis(), resiliencePolicy, Clock.systemUTC());
    }

    KisAccessTokenProvider(
            RestClient.Builder restClientBuilder,
            ExternalProviderProperties.Kis properties,
            ExternalProviderResiliencePolicy resiliencePolicy,
            Clock clock) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl().toString())
                .build();
        this.properties = properties;
        this.resiliencePolicy = resiliencePolicy;
        this.clock = clock;
    }

    synchronized String accessToken() {
        if (StringUtils.hasText(properties.accessToken())) {
            return properties.accessToken();
        }
        if (StringUtils.hasText(cachedToken) && clock.instant().isBefore(expiresAt.minusSeconds(60))) {
            return cachedToken;
        }
        String appKey = properties.requiredAppKey();
        String appSecret = properties.requiredAppSecret();
        KisTokenResponse response = resiliencePolicy.execute("kis-access-token", () -> restClient.post()
                .uri("/oauth2/tokenP")
                .body(new KisTokenRequest("client_credentials", appKey, appSecret))
                .retrieve()
                .body(KisTokenResponse.class));
        if (response == null || !StringUtils.hasText(response.accessToken())) {
            throw new IllegalStateException("KIS access token was not issued");
        }
        cachedToken = response.accessToken();
        long expiresIn = response.expiresIn() <= 0 ? 86_400L : response.expiresIn();
        expiresAt = clock.instant().plusSeconds(expiresIn);
        return cachedToken;
    }

    private record KisTokenRequest(
            @JsonProperty("grant_type") String grantType,
            String appkey,
            String appsecret
    ) {
    }

    private record KisTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn
    ) {
    }
}
