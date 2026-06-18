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

class PapagoTranslationClientTest {

    @Test
    void translateKoToEnSendsPapagoNmtRequestAndMapsTranslatedText() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PapagoTranslationClient client = new PapagoTranslationClient(
                builder,
                properties(),
                ProviderTestResilience.disabled());

        server.expect(requestTo("https://openapi.naver.com/v1/papago/n2mt"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Naver-Client-Id", "papago-client"))
                .andExpect(header("X-Naver-Client-Secret", "papago-secret"))
                .andExpect(content().string(containsString("source=ko")))
                .andExpect(content().string(containsString("target=en")))
                .andExpect(content().string(containsString("%EC%82%BC%EC%84%B1%EC%A0%84%EC%9E%90")))
                .andRespond(withSuccess("""
                        {
                          "message": {
                            "result": {
                              "translatedText": "Samsung Electronics earnings improve"
                            }
                          }
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
                new ExternalProviderProperties.PapagoTranslation(
                        URI.create("https://openapi.naver.com"),
                        "papago-client",
                        "papago-secret"));
    }
}
