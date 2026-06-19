package com.hana.omnilens.provider.translation;

import java.util.List;

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
public class DeepLTranslationClient {

    private final RestClient restClient;
    private final ExternalProviderProperties.DeepLTranslation properties;
    private final ExternalProviderResiliencePolicy resiliencePolicy;

    public DeepLTranslationClient(
            RestClient.Builder restClientBuilder,
            ExternalProviderProperties properties,
            ExternalProviderResiliencePolicy resiliencePolicy) {
        this.restClient = restClientBuilder
                .baseUrl(properties.deepLTranslation().baseUrl().toString())
                .build();
        this.properties = properties.deepLTranslation();
        this.resiliencePolicy = resiliencePolicy;
    }

    public String translateKoToEn(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }

        String apiKey = properties.requiredApiKey();
        DeepLTranslationResponse response = resiliencePolicy.execute("deepl-translation", () -> restClient.post()
                .uri("/v2/translate")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Authorization", "DeepL-Auth-Key " + apiKey)
                .body(form(text))
                .retrieve()
                .body(DeepLTranslationResponse.class));

        return response == null ? "" : response.translatedText();
    }

    private MultiValueMap<String, String> form(String text) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("text", text);
        form.add("source_lang", "KO");
        form.add("target_lang", "EN-US");
        return form;
    }

    private record DeepLTranslationResponse(List<DeepLTranslation> translations) {

        private String translatedText() {
            if (translations == null || translations.isEmpty() || translations.get(0) == null) {
                return "";
            }
            return translations.get(0).text();
        }
    }

    private record DeepLTranslation(
            @JsonProperty("detected_source_language") String detectedSourceLanguage,
            String text) {
    }
}
