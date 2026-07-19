package com.hana.omniconnect.provider.ai;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.hana.omniconnect.config.HannahAiProperties;

final class HannahAiRestClientFactory {

    private HannahAiRestClientFactory() {
    }

    static RestClient create(RestClient.Builder restClientBuilder, HannahAiProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl().toString())
                .build();
    }
}
