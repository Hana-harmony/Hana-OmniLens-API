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
public class HannahAiAnalysisClient {

    private final RestClient restClient;
    private final ExternalProviderResiliencePolicy resiliencePolicy;
    private final ObjectMapper objectMapper;
    private final String maintenanceToken;

    @Autowired
    public HannahAiAnalysisClient(
            RestClient.Builder restClientBuilder,
            HannahAiProperties properties,
            ExternalProviderResiliencePolicy resiliencePolicy,
            ObjectMapper objectMapper) {
        this.restClient = HannahAiRestClientFactory.create(restClientBuilder, properties);
        this.resiliencePolicy = resiliencePolicy;
        this.objectMapper = objectMapper;
        this.maintenanceToken = properties.maintenanceToken();
    }

    HannahAiAnalysisClient(RestClient restClient, ExternalProviderResiliencePolicy resiliencePolicy) {
        this(restClient, resiliencePolicy, new ObjectMapper(), "");
    }

    HannahAiAnalysisClient(
            RestClient restClient,
            ExternalProviderResiliencePolicy resiliencePolicy,
            ObjectMapper objectMapper) {
        this(restClient, resiliencePolicy, objectMapper, "");
    }

    HannahAiAnalysisClient(
            RestClient restClient,
            ExternalProviderResiliencePolicy resiliencePolicy,
            ObjectMapper objectMapper,
            String maintenanceToken) {
        this.restClient = restClient;
        this.resiliencePolicy = resiliencePolicy;
        this.objectMapper = objectMapper;
        this.maintenanceToken = maintenanceToken == null ? "" : maintenanceToken.strip();
    }

    public HannahAiAnalysisResponse analyze(HannahAiAnalysisRequest request) {
        return analyze(request, HannahAiAnalysisProvider.QWEN);
    }

    public HannahAiAnalysisResponse analyze(
            HannahAiAnalysisRequest request,
            HannahAiAnalysisProvider provider) {
        if (provider == HannahAiAnalysisProvider.OPENAI_INITIAL_BACKFILL && maintenanceToken.isBlank()) {
            throw new IllegalStateException("Hannah AI maintenance token is required for OpenAI backfill");
        }
        HannahAiApiResponse<HannahAiAnalysisResponse> response = resiliencePolicy.execute(
                "hannah-ai-analysis",
                () -> parseResponse(restClient.post()
                        .uri("/api/v1/alerts/analyze")
                        .headers(headers -> {
                            if (provider == HannahAiAnalysisProvider.OPENAI_INITIAL_BACKFILL) {
                                headers.set("X-Hannah-Analysis-Provider", provider.name());
                                headers.set("X-Hannah-AI-Maintenance-Token", maintenanceToken);
                            }
                        })
                        .body(request)
                        .retrieve()
                        .body(byte[].class)));

        if (response == null || !response.success() || response.data() == null) {
            throw new IllegalStateException("Hannah AI returned an empty analysis response");
        }
        return response.data();
    }

    private HannahAiApiResponse<HannahAiAnalysisResponse> parseResponse(byte[] body) {
        try {
            return objectMapper.readValue(
                    new String(body == null ? new byte[0] : body, StandardCharsets.UTF_8),
                    HannahAiAnalysisEnvelope.TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Hannah AI returned a non-JSON analysis response", exception);
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

    private static final class HannahAiAnalysisEnvelope {
        private static final TypeReference<HannahAiApiResponse<HannahAiAnalysisResponse>> TYPE =
                new TypeReference<>() {
                };
    }
}
