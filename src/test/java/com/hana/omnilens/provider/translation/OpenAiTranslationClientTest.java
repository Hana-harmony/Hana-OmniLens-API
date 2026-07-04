package com.hana.omnilens.provider.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.provider.ProviderTestResilience;

class OpenAiTranslationClientTest {

    @Test
    void translateKoToEnUsesResponsesApiAndMapsOutputText() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiTranslationClient client = new OpenAiTranslationClient(
                builder,
                ProviderTestResilience.disabled(),
                "https://api.openai.com",
                "openai-secret",
                "gpt-4o-mini");

        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer openai-secret"))
                .andExpect(content().string(containsString("\"store\":false")))
                .andExpect(content().string(containsString("Translate Korean financial news")))
                .andExpect(content().string(containsString("Do not leave any Korean Hangul characters")))
                .andExpect(content().string(containsString("\"max_output_tokens\":8192")))
                .andExpect(content().string(containsString("삼성전자 실적 개선")))
                .andRespond(withSuccess("""
                        {
                          "status": "completed",
                          "output": [
                            {
                              "type": "message",
                              "content": [
                                {
                                  "type": "output_text",
                                  "text": "Samsung Electronics earnings improve."
                                }
                              ]
                            }
                          ]
                        }
                        """, APPLICATION_JSON));

        String translatedText = client.translateKoToEn("삼성전자 실적 개선");

        assertThat(translatedText).isEqualTo("Samsung Electronics earnings improve.");
        server.verify();
    }
}
