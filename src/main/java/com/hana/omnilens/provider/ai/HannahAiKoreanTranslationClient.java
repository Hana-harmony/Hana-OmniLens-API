package com.hana.omnilens.provider.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.HannahAiProperties;
import com.hana.omnilens.provider.ExternalProviderResiliencePolicy;

@Component
public class HannahAiKoreanTranslationClient {

    private final RestClient restClient;
    private final ExternalProviderResiliencePolicy resiliencePolicy;

    @Autowired
    public HannahAiKoreanTranslationClient(
            RestClient.Builder restClientBuilder,
            HannahAiProperties properties,
            ExternalProviderResiliencePolicy resiliencePolicy) {
        this.restClient = HannahAiRestClientFactory.create(restClientBuilder, properties);
        this.resiliencePolicy = resiliencePolicy;
    }

    HannahAiKoreanTranslationClient(RestClient restClient, ExternalProviderResiliencePolicy resiliencePolicy) {
        this.restClient = restClient;
        this.resiliencePolicy = resiliencePolicy;
    }

    public HannahAiKoreanTranslationResponse translate(HannahAiKoreanTranslationRequest request) {
        HannahAiApiResponse<HannahAiKoreanTranslationResponse> response =
                resiliencePolicy.execute("hannah-ai-korean-translation", () -> restClient.post()
                        .uri("/api/v1/translation/ko-en")
                        .body(request)
                        .retrieve()
                        .body(HannahAiKoreanTranslationEnvelope.TYPE));

        if (response == null || !response.success() || response.data() == null) {
            throw new IllegalStateException("Hannah AI returned an empty Korean translation response");
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

    private static final class HannahAiKoreanTranslationEnvelope {
        private static final org.springframework.core.ParameterizedTypeReference<
                HannahAiApiResponse<HannahAiKoreanTranslationResponse>> TYPE =
                new org.springframework.core.ParameterizedTypeReference<>() {
                };
    }
}
