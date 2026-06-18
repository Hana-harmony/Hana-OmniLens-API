package com.hana.omnilens.provider.translation;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.ExternalProviderProperties;
import com.hana.omnilens.provider.ExternalProviderResiliencePolicy;

@Component
public class PapagoTranslationClient {

    private final RestClient restClient;
    private final ExternalProviderProperties.PapagoTranslation properties;
    private final ExternalProviderResiliencePolicy resiliencePolicy;

    public PapagoTranslationClient(
            RestClient.Builder restClientBuilder,
            ExternalProviderProperties properties,
            ExternalProviderResiliencePolicy resiliencePolicy) {
        this.restClient = restClientBuilder
                .baseUrl(properties.papagoTranslation().baseUrl().toString())
                .build();
        this.properties = properties.papagoTranslation();
        this.resiliencePolicy = resiliencePolicy;
    }

    public String translateKoToEn(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }

        String clientId = properties.requiredClientId();
        String clientSecret = properties.requiredClientSecret();
        PapagoTranslationResponse response = resiliencePolicy.execute("papago-translation", () -> restClient.post()
                .uri("/v1/papago/n2mt")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("X-Naver-Client-Id", clientId)
                .header("X-Naver-Client-Secret", clientSecret)
                .body(form(text))
                .retrieve()
                .body(PapagoTranslationResponse.class));

        return response == null ? "" : response.translatedText();
    }

    private MultiValueMap<String, String> form(String text) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("source", "ko");
        form.add("target", "en");
        form.add("text", text);
        return form;
    }

    private record PapagoTranslationResponse(PapagoMessage message) {

        private String translatedText() {
            if (message == null || message.result() == null) {
                return "";
            }
            return message.result().translatedText();
        }
    }

    private record PapagoMessage(PapagoResult result) {
    }

    private record PapagoResult(@JsonProperty("translatedText") String translatedText) {
    }
}
