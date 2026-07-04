package com.hana.omnilens.provider.translation;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.provider.ExternalProviderResiliencePolicy;

@Component
public class OpenAiTranslationClient {

    private final RestClient restClient;
    private final ExternalProviderResiliencePolicy resiliencePolicy;
    private final String apiKey;
    private final String model;

    public OpenAiTranslationClient(
            RestClient.Builder restClientBuilder,
            ExternalProviderResiliencePolicy resiliencePolicy,
            @Value("${omnilens.providers.openai-translation.base-url:https://api.openai.com}") String baseUrl,
            @Value("${omnilens.providers.openai-translation.api-key:${OPENAI_API_KEY:}}") String apiKey,
            @Value("${omnilens.providers.openai-translation.model:gpt-4o-mini}") String model) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.resiliencePolicy = resiliencePolicy;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = StringUtils.hasText(model) ? model : "gpt-4o-mini";
    }

    public String translateKoToEn(String text) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(apiKey)) {
            return "";
        }
        OpenAiResponsesResponse response = resiliencePolicy.execute("openai-translation", () -> restClient.post()
                .uri("/v1/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(new OpenAiResponsesRequest(
                        model,
                        """
                        Translate Korean financial news into natural, concise English.
                        Return only the translation. Preserve stock codes, numbers, dates, URLs, and company names.
                        Translate Korean market localisms so foreign investors can understand them.
                        Do not leave any Korean Hangul characters in the output; romanize proper nouns when needed.
                        Translate every sentence from the input and do not summarize or truncate the article.
                        """,
                        List.of(new InputMessage("user", text)),
                        false,
                        0,
                        8192))
                .retrieve()
                .body(OpenAiResponsesResponse.class));
        return response == null ? "" : response.content();
    }

    public String model() {
        return model;
    }

    private record OpenAiResponsesRequest(
            String model,
            String instructions,
            List<InputMessage> input,
            boolean store,
            double temperature,
            @JsonProperty("max_output_tokens") int maxOutputTokens
    ) {
    }

    private record InputMessage(String role, String content) {
    }

    private record OpenAiResponsesResponse(String status, List<OutputItem> output) {

        private String content() {
            if (!"completed".equals(status) || output == null || output.isEmpty()) {
                return "";
            }
            return output.stream()
                    .filter(item -> item != null && "message".equals(item.type()))
                    .flatMap(item -> item.content() == null ? java.util.stream.Stream.empty() : item.content().stream())
                    .filter(content -> content != null && "output_text".equals(content.type()))
                    .map(OutputContent::text)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse("");
        }
    }

    private record OutputItem(String type, List<OutputContent> content) {
    }

    private record OutputContent(String type, String text) {
    }
}
