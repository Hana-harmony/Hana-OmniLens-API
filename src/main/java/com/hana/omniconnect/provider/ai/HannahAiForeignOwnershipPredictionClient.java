package com.hana.omniconnect.provider.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.hana.omniconnect.config.HannahAiProperties;
import com.hana.omniconnect.provider.ExternalProviderResiliencePolicy;

@Component
public class HannahAiForeignOwnershipPredictionClient {

    private final RestClient restClient;
    private final ExternalProviderResiliencePolicy resiliencePolicy;

    @Autowired
    public HannahAiForeignOwnershipPredictionClient(
            RestClient.Builder restClientBuilder,
            HannahAiProperties properties,
            ExternalProviderResiliencePolicy resiliencePolicy) {
        this.restClient = HannahAiRestClientFactory.create(restClientBuilder, properties);
        this.resiliencePolicy = resiliencePolicy;
    }

    HannahAiForeignOwnershipPredictionClient(RestClient restClient, ExternalProviderResiliencePolicy resiliencePolicy) {
        this.restClient = restClient;
        this.resiliencePolicy = resiliencePolicy;
    }

    public HannahAiForeignOwnershipPredictionResponse predict(HannahAiForeignOwnershipPredictionRequest request) {
        HannahAiApiResponse<HannahAiForeignOwnershipPredictionResponse> response =
                resiliencePolicy.execute("hannah-ai-foreign-ownership-prediction", () -> restClient.post()
                        .uri("/api/v1/market/foreign-ownership/predict")
                        .body(request)
                        .retrieve()
                        .body(HannahAiForeignOwnershipPredictionEnvelope.TYPE));

        if (response == null || !response.success() || response.data() == null) {
            throw new IllegalStateException("Hannah AI returned an empty foreign ownership prediction response");
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

    private static final class HannahAiForeignOwnershipPredictionEnvelope {
        private static final org.springframework.core.ParameterizedTypeReference<
                HannahAiApiResponse<HannahAiForeignOwnershipPredictionResponse>> TYPE =
                new org.springframework.core.ParameterizedTypeReference<>() {
                };
    }
}
