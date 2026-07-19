package com.hana.omniconnect.provider.ai;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.hana.omniconnect.config.HannahAiProperties;
import com.hana.omniconnect.provider.ExternalProviderResiliencePolicy;

@Component
public class HannahAiKoreanTranslationClient {

    private final RestClient restClient;
    private final ExternalProviderResiliencePolicy resiliencePolicy;
    private final ObjectMapper objectMapper;

    @Autowired
    public HannahAiKoreanTranslationClient(
            RestClient.Builder restClientBuilder,
            HannahAiProperties properties,
            ExternalProviderResiliencePolicy resiliencePolicy,
            ObjectMapper objectMapper) {
        this.restClient = HannahAiRestClientFactory.create(restClientBuilder, properties);
        this.resiliencePolicy = resiliencePolicy;
        this.objectMapper = objectMapper;
    }

    HannahAiKoreanTranslationClient(RestClient restClient, ExternalProviderResiliencePolicy resiliencePolicy) {
        this(restClient, resiliencePolicy, new ObjectMapper());
    }

    HannahAiKoreanTranslationClient(
            RestClient restClient,
            ExternalProviderResiliencePolicy resiliencePolicy,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.resiliencePolicy = resiliencePolicy;
        this.objectMapper = objectMapper;
    }

    public HannahAiKoreanTranslationResponse translate(HannahAiKoreanTranslationRequest request) {
        HannahAiApiResponse<HannahAiKoreanTranslationResponse> response =
                resiliencePolicy.execute("hannah-ai-korean-translation", () -> parseResponse(restClient.post()
                                .uri("/api/v1/translation/ko-en")
                                .body(request)
                                .retrieve()
                                .body(byte[].class)));

        if (response == null || !response.success() || response.data() == null) {
            throw new IllegalStateException("Hannah AI returned an empty Korean translation response");
        }
        return response.data();
    }

    private HannahAiApiResponse<HannahAiKoreanTranslationResponse> parseResponse(byte[] body) {
        try {
            return objectMapper.readValue(
                    new String(body == null ? new byte[0] : body, StandardCharsets.UTF_8),
                    HannahAiKoreanTranslationEnvelope.TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Hannah AI returned a non-JSON Korean translation response", exception);
        }
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
        private static final TypeReference<HannahAiApiResponse<HannahAiKoreanTranslationResponse>> TYPE =
                new TypeReference<>() {
                };
    }
}
