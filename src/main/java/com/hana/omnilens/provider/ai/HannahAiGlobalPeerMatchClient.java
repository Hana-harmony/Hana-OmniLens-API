package com.hana.omnilens.provider.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.HannahAiProperties;
import com.hana.omnilens.provider.ExternalProviderResiliencePolicy;

@Component
public class HannahAiGlobalPeerMatchClient {

    private final RestClient restClient;
    private final ExternalProviderResiliencePolicy resiliencePolicy;

    @Autowired
    public HannahAiGlobalPeerMatchClient(
            RestClient.Builder restClientBuilder,
            HannahAiProperties properties,
            ExternalProviderResiliencePolicy resiliencePolicy) {
        this(HannahAiRestClientFactory.create(restClientBuilder, properties), resiliencePolicy);
    }

    HannahAiGlobalPeerMatchClient(
            RestClient restClient,
            ExternalProviderResiliencePolicy resiliencePolicy) {
        this.restClient = restClient;
        this.resiliencePolicy = resiliencePolicy;
    }

    public HannahAiGlobalPeerMatchResponse match(HannahAiGlobalPeerMatchRequest request) {
        HannahAiApiResponse<HannahAiGlobalPeerMatchResponse> response =
                resiliencePolicy.execute("hannah-ai-global-peer-match", () -> restClient.post()
                        .uri("/api/v1/market/global-peers/match")
                        .body(request)
                        .retrieve()
                        .body(HannahAiGlobalPeerMatchEnvelope.TYPE));

        if (response == null || !response.success() || response.data() == null) {
            throw new IllegalStateException("Hannah AI returned an empty global peer response");
        }
        return response.data();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record HannahAiApiResponse<T>(
            boolean success,
            String code,
            String message,
            T data
    ) {
    }

    private static final class HannahAiGlobalPeerMatchEnvelope {
        private static final org.springframework.core.ParameterizedTypeReference<
                HannahAiApiResponse<HannahAiGlobalPeerMatchResponse>> TYPE =
                new org.springframework.core.ParameterizedTypeReference<>() {
                };
    }
}
