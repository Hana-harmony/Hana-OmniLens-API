package com.hana.omnilens.provider.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.ForeignOwnershipModelTrainingProperties;
import com.hana.omnilens.config.HannahAiProperties;

@Component
public class HannahAiForeignOwnershipRetrainClient {

    private static final String MAINTENANCE_TOKEN_HEADER = "X-HANNAH-AI-MAINTENANCE-TOKEN";

    private final RestClient restClient;
    private final ForeignOwnershipModelTrainingProperties properties;

    @Autowired
    public HannahAiForeignOwnershipRetrainClient(
            HannahAiProperties hannahAiProperties,
            ForeignOwnershipModelTrainingProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(hannahAiProperties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
        this.properties = properties;
    }

    HannahAiForeignOwnershipRetrainClient(
            RestClient restClient,
            ForeignOwnershipModelTrainingProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public HannahAiForeignOwnershipRetrainResponse retrain(HannahAiForeignOwnershipRetrainRequest request) {
        HannahAiApiResponse<HannahAiForeignOwnershipRetrainResponse> response = restClient.post()
                .uri("/api/v1/market/foreign-ownership/model/retrain")
                .headers(headers -> {
                    if (StringUtils.hasText(properties.maintenanceToken())) {
                        headers.set(MAINTENANCE_TOKEN_HEADER, properties.maintenanceToken());
                    }
                })
                .body(request)
                .retrieve()
                .body(HannahAiForeignOwnershipRetrainEnvelope.TYPE);

        if (response == null || !response.success() || response.data() == null) {
            throw new IllegalStateException("Hannah AI returned an empty foreign ownership retrain response");
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

    private static final class HannahAiForeignOwnershipRetrainEnvelope {
        private static final org.springframework.core.ParameterizedTypeReference<
                HannahAiApiResponse<HannahAiForeignOwnershipRetrainResponse>> TYPE =
                new org.springframework.core.ParameterizedTypeReference<>() {
                };
    }
}
