package com.hana.omnilens.provider.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.ExternalProviderProperties;
import com.hana.omnilens.provider.ProviderTestResilience;

class DeepLTranslationClientTest {

    @Test
    void translateKoToEnSendsDeepLRequestAndMapsTranslatedText() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DeepLTranslationClient client = new DeepLTranslationClient(
                builder,
                properties(),
                ProviderTestResilience.disabled());

        server.expect(requestTo("https://api-free.deepl.com/v2/translate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "DeepL-Auth-Key deepl-secret"))
                .andExpect(content().string(containsString("source_lang=KO")))
                .andExpect(content().string(containsString("target_lang=EN-US")))
                .andExpect(content().string(containsString("%EC%82%BC%EC%84%B1%EC%A0%84%EC%9E%90")))
                .andRespond(withSuccess("""
                        {
                          "translations": [
                            {
                              "detected_source_language": "KO",
                              "text": "Samsung Electronics earnings improve"
                            }
                          ]
                        }
                        """, APPLICATION_JSON));

        String translatedText = client.translateKoToEn("삼성전자 실적 개선");

        assertThat(translatedText).isEqualTo("Samsung Electronics earnings improve");
        server.verify();
    }

    private ExternalProviderProperties properties() {
        return new ExternalProviderProperties(
                null,
                null,
                null,
                null,
                null,
                new ExternalProviderProperties.DeepLTranslation(
                        URI.create("https://api-free.deepl.com"),
                        "deepl-secret"));
    }
}
