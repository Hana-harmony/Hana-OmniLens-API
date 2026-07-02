package com.hana.omnilens.provider.translation;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiTranslationClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public OpenAiTranslationClient(
            RestClient.Builder restClientBuilder,
            @Value("${omnilens.providers.openai-translation.base-url:https://api.openai.com}") String baseUrl,
            @Value("${omnilens.providers.openai-translation.api-key:${OPENAI_API_KEY:}}") String apiKey,
            @Value("${omnilens.providers.openai-translation.model:gpt-4o-mini}") String model) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = StringUtils.hasText(model) ? model : "gpt-4o-mini";
    }

    public String translateKoToEn(String text) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(apiKey)) {
            return "";
        }
        OpenAiChatCompletionResponse response = restClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(new OpenAiChatCompletionRequest(
                        model,
                        0,
                        List.of(
                                new Message("system", """
                                        Translate Korean financial news into natural, concise English.
                                        Return only the translation. Preserve stock codes, numbers, dates, URLs, and company names.
                                        Translate Korean market localisms so foreign investors can understand them.
                                        """),
                                new Message("user", text))))
                .retrieve()
                .body(OpenAiChatCompletionResponse.class);
        return response == null ? "" : response.content();
    }

    private record OpenAiChatCompletionRequest(
            String model,
            double temperature,
            List<Message> messages
    ) {
    }

    private record Message(String role, String content) {
    }

    private record OpenAiChatCompletionResponse(List<Choice> choices) {

        private String content() {
            if (choices == null || choices.isEmpty() || choices.get(0) == null || choices.get(0).message() == null) {
                return "";
            }
            return choices.get(0).message().content();
        }
    }

    private record Choice(@JsonProperty("message") Message message) {
    }
}
