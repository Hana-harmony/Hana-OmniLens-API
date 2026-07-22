package com.hana.omniconnect.provider.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.hana.omniconnect.config.HannahAiProperties;
import com.hana.omniconnect.provider.ExternalProviderResiliencePolicy;

@Component
public class HannahAiTaxDocumentVerificationClient {

    private final RestClient restClient;
    private final ExternalProviderResiliencePolicy resiliencePolicy;

    @Autowired
    public HannahAiTaxDocumentVerificationClient(
            RestClient.Builder restClientBuilder,
            HannahAiProperties properties,
            ExternalProviderResiliencePolicy resiliencePolicy) {
        this.restClient = HannahAiRestClientFactory.create(
                restClientBuilder,
                properties,
                properties.taxReadTimeout());
        this.resiliencePolicy = resiliencePolicy;
    }

    HannahAiTaxDocumentVerificationClient(
            RestClient restClient,
            ExternalProviderResiliencePolicy resiliencePolicy) {
        this.restClient = restClient;
        this.resiliencePolicy = resiliencePolicy;
    }

    public HannahAiTaxDocumentVerificationResponse verify(
            HannahAiTaxDocumentVerificationRequest request) {
        HannahAiApiResponse<HannahAiTaxDocumentVerificationResponse> response =
                resiliencePolicy.execute("hannah-ai-tax-document-verification", () -> restClient.post()
                        .uri("/api/v1/tax/documents/verify")
                        .body(request)
                        .retrieve()
                        .body(HannahAiTaxDocumentVerificationEnvelope.TYPE));

        if (response == null || !response.success() || response.data() == null) {
            throw new IllegalStateException("Hannah AI returned an empty tax document response");
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

    private static final class HannahAiTaxDocumentVerificationEnvelope {
        private static final org.springframework.core.ParameterizedTypeReference<
                HannahAiApiResponse<HannahAiTaxDocumentVerificationResponse>> TYPE =
                new org.springframework.core.ParameterizedTypeReference<>() {
                };
    }
}
