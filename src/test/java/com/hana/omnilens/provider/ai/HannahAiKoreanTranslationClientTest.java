package com.hana.omnilens.provider.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.provider.ProviderTestResilience;

class HannahAiKoreanTranslationClientTest {

    @Test
    void translateUsesInternalAiContractWithoutServiceToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HannahAiKoreanTranslationClient client = new HannahAiKoreanTranslationClient(
                builder.baseUrl("http://localhost:8000").build(),
                ProviderTestResilience.disabled());

        server.expect(requestTo("http://localhost:8000/api/v1/translation/ko-en"))
                .andExpect(headerDoesNotExist("X-HANNAH-AI-SERVICE-TOKEN"))
                .andExpect(content().string(containsString("\"text\":\"개미가 삼전닉스를 순매수했다.\"")))
                .andExpect(content().string(containsString("\"source_language\":\"ko\"")))
                .andExpect(content().string(containsString("\"target_language\":\"en\"")))
                .andExpect(content().string(containsString("\"normalized_term\":\"삼전닉스\"")))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "status": 200,
                          "code": "COMMON_000",
                          "message": "OK",
                          "data": {
                            "translated_text": "Ants net bought Samjeon Nix.",
                            "provider": "local-open-source-qwen3-translation",
                            "model_version": "local-llm:mlx-community/Qwen3-0.6B-4bit",
                            "status": "TRANSLATED",
                            "prompt_version": "ko-en-qwen3-financial-translation-v1",
                            "quality_flags": []
                          },
                          "timestamp": "2026-07-05T00:00:00Z"
                        }
                        """, APPLICATION_JSON));

        HannahAiKoreanTranslationResponse response = client.translate(
                new HannahAiKoreanTranslationRequest(
                        "개미가 삼전닉스를 순매수했다.",
                        "ko",
                        "en",
                        "NEWS",
                        "삼전닉스 순매수",
                        List.of(new HannahAiGlossaryTerm(
                                "삼전닉스",
                                "삼전닉스",
                                "Samsung Electronics and SK hynix",
                                "market_slang"))));

        assertThat(response.translatedText()).isEqualTo("Ants net bought Samjeon Nix.");
        assertThat(response.provider()).isEqualTo("local-open-source-qwen3-translation");
        assertThat(response.modelVersion()).isEqualTo("local-llm:mlx-community/Qwen3-0.6B-4bit");
        assertThat(response.status()).isEqualTo("TRANSLATED");
        server.verify();
    }
}
