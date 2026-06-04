package com.hana.omnilens.provider.ai;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.HannahAiProperties;
import com.hana.omnilens.provider.ExternalProviderResiliencePolicy;

@Component
public class HannahAiAnalysisClient {

    private final RestClient restClient;
    private final ExternalProviderResiliencePolicy resiliencePolicy;

    public HannahAiAnalysisClient(
            RestClient.Builder restClientBuilder,
            HannahAiProperties properties,
            ExternalProviderResiliencePolicy resiliencePolicy) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl().toString())
                .build();
        this.resiliencePolicy = resiliencePolicy;
    }

    public HannahAiAnalysisResponse analyze(HannahAiAnalysisRequest request) {
        HannahAiAnalysisResponse response = resiliencePolicy.execute("hannah-ai-analysis", () -> restClient.post()
                .uri("/api/v1/alerts/analyze")
                .body(request)
                .retrieve()
                .body(HannahAiAnalysisResponse.class));

        if (response == null) {
            throw new IllegalStateException("Hannah AI returned an empty analysis response");
        }
        return response;
    }
}
