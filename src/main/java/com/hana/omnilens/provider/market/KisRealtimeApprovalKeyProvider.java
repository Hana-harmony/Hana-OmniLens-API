package com.hana.omnilens.provider.market;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.ExternalProviderProperties;
import com.hana.omnilens.provider.ExternalProviderResiliencePolicy;

@Component
public class KisRealtimeApprovalKeyProvider {

    private final RestClient restClient;
    private final ExternalProviderProperties.Kis properties;
    private final ExternalProviderResiliencePolicy resiliencePolicy;

    private String cachedApprovalKey = "";

    @Autowired
    public KisRealtimeApprovalKeyProvider(
            RestClient.Builder restClientBuilder,
            ExternalProviderProperties properties,
            ExternalProviderResiliencePolicy resiliencePolicy) {
        this(restClientBuilder, properties.kis(), resiliencePolicy);
    }

    public KisRealtimeApprovalKeyProvider(
            RestClient.Builder restClientBuilder,
            ExternalProviderProperties.Kis properties,
            ExternalProviderResiliencePolicy resiliencePolicy) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl().toString())
                .build();
        this.properties = properties;
        this.resiliencePolicy = resiliencePolicy;
    }

    public synchronized String approvalKey() {
        if (StringUtils.hasText(properties.approvalKey())) {
            return properties.approvalKey();
        }
        if (StringUtils.hasText(cachedApprovalKey)) {
            return cachedApprovalKey;
        }
        String appKey = properties.requiredAppKey();
        String appSecret = properties.requiredAppSecret();
        KisApprovalResponse response = resiliencePolicy.execute("kis-realtime-approval-key", () -> restClient.post()
                .uri("/oauth2/Approval")
                .body(new KisApprovalRequest("client_credentials", appKey, appSecret))
                .retrieve()
                .body(KisApprovalResponse.class));
        if (response == null || !StringUtils.hasText(response.approvalKey())) {
            throw new IllegalStateException("KIS realtime approval key was not issued");
        }
        cachedApprovalKey = response.approvalKey();
        return cachedApprovalKey;
    }

    private record KisApprovalRequest(
            @JsonProperty("grant_type") String grantType,
            String appkey,
            String secretkey
    ) {
    }

    private record KisApprovalResponse(@JsonProperty("approval_key") String approvalKey) {
    }
}
