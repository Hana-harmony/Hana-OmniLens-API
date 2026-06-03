package com.hana.omnilens.provider.ai;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.HannahAiProperties;

@Component
public class HannahAiAnalysisClient {

    private final RestClient restClient;

    public HannahAiAnalysisClient(RestClient.Builder restClientBuilder, HannahAiProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl().toString())
                .build();
    }

    public HannahAiAnalysisResponse analyze(HannahAiAnalysisRequest request) {
        HannahAiAnalysisResponse response = restClient.post()
                .uri("/api/v1/alerts/analyze")
                .body(request)
                .retrieve()
                .body(HannahAiAnalysisResponse.class);

        if (response == null) {
            throw new IllegalStateException("Hannah AI returned an empty analysis response");
        }
        return response;
    }
}
