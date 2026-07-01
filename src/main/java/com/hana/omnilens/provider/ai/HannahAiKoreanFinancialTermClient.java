package com.hana.omnilens.provider.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.HannahAiProperties;
import com.hana.omnilens.provider.ExternalProviderResiliencePolicy;

@Component
public class HannahAiKoreanFinancialTermClient {

    private final RestClient restClient;
    private final ExternalProviderResiliencePolicy resiliencePolicy;

    public HannahAiKoreanFinancialTermClient(
            RestClient.Builder restClientBuilder,
            HannahAiProperties properties,
            ExternalProviderResiliencePolicy resiliencePolicy) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl().toString())
                .build();
        this.resiliencePolicy = resiliencePolicy;
    }

    public HannahAiKoreanFinancialTermExplainResponse explain(
            HannahAiKoreanFinancialTermExplainRequest request) {
        HannahAiApiResponse<HannahAiKoreanFinancialTermExplainResponse> response =
                resiliencePolicy.execute("hannah-ai-korean-financial-term-explain", () -> restClient.post()
                        .uri("/api/v1/korean-financial-terms/explain")
                        .body(request)
                        .retrieve()
                        .body(HannahAiKoreanFinancialTermEnvelope.TYPE));

        if (response == null || !response.success() || response.data() == null) {
            throw new IllegalStateException("Hannah AI returned an empty Korean financial term response");
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

    private static final class HannahAiKoreanFinancialTermEnvelope {
        private static final org.springframework.core.ParameterizedTypeReference<
                HannahAiApiResponse<HannahAiKoreanFinancialTermExplainResponse>> TYPE =
                new org.springframework.core.ParameterizedTypeReference<>() {
                };
    }
}
