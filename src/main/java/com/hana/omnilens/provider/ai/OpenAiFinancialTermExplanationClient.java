package com.hana.omnilens.provider.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.term.api.KoreanFinancialTermExplainRequest;

@Component
public class OpenAiFinancialTermExplanationClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public OpenAiFinancialTermExplanationClient(
            RestClient.Builder restClientBuilder,
            @Value("${omnilens.providers.openai-translation.base-url:https://api.openai.com}") String baseUrl,
            @Value("${omnilens.providers.openai-translation.api-key:${OPENAI_API_KEY:}}") String apiKey,
            @Value("${omnilens.providers.openai-translation.model:gpt-4o-mini}") String model) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = StringUtils.hasText(model) ? model : "gpt-4o-mini";
    }

    public String explain(KoreanFinancialTermExplainRequest request) {
        if (!StringUtils.hasText(apiKey)) {
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
                                        Explain Korean stock-market local terms for foreign retail investors.
                                        Use the article context when available. Return 2-3 concise English sentences only.
                                        Do not mention uncertainty unless the term is genuinely ambiguous.
                                        """),
                                new Message("user", prompt(request)))))
                .retrieve()
                .body(OpenAiChatCompletionResponse.class);
        return response == null ? "" : response.content();
    }

    private String prompt(KoreanFinancialTermExplainRequest request) {
        return """
                Term: %s
                Source type: %s
                Article title: %s
                Stock: %s %s
                Article URL: %s
                Context:
                %s
                """.formatted(
                nullToEmpty(request.term()),
                nullToEmpty(request.sourceType()),
                nullToEmpty(request.title()),
                nullToEmpty(request.stockCode()),
                nullToEmpty(request.stockName()),
                nullToEmpty(request.articleUrl()),
                nullToEmpty(request.context()));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
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
