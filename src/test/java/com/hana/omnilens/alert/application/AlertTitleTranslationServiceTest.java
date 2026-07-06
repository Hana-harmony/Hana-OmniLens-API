package com.hana.omnilens.alert.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.hana.omnilens.alert.domain.AlertGlossaryTerm;
import com.hana.omnilens.provider.ai.HannahAiKoreanTranslationClient;
import com.hana.omnilens.provider.ai.HannahAiKoreanTranslationRequest;
import com.hana.omnilens.provider.ai.HannahAiKoreanTranslationResponse;

class AlertTitleTranslationServiceTest {

    private static final String PROVIDER = "local-open-source-qwen3-translation";
    private static final String MODEL = "local-llm:mlx-community/Qwen3-0.6B-4bit";

    private final HannahAiKoreanTranslationClient hannahTranslationClient =
            mock(HannahAiKoreanTranslationClient.class);
    private final AlertTitleTranslationService translationService =
            new AlertTitleTranslationService(hannahTranslationClient);

    @Test
    void translateTitleReturnsLocalQwenTranslationFirst() {
        when(hannahTranslationClient.translate(any()))
                .thenReturn(translated("Samsung Electronics earnings improve"));

        String translatedTitle = translationService.translateTitle("삼성전자 실적 개선");

        assertThat(translatedTitle).isEqualTo("Samsung Electronics earnings improve");
    }

    @Test
    void translateTitleReturnsBlankWhenProviderFails() {
        when(hannahTranslationClient.translate(any()))
                .thenThrow(new IllegalStateException("hannah ai unavailable"));

        String translatedTitle = translationService.translateTitle("삼성전자 실적 개선");

        assertThat(translatedTitle).isEmpty();
    }

    @Test
    void translateTitleReturnsBlankWhenProviderReturnsBlank() {
        when(hannahTranslationClient.translate(any()))
                .thenReturn(translated(""));

        String translatedTitle = translationService.translateTitle("삼성전자 실적 개선");

        assertThat(translatedTitle).isEmpty();
    }

    @Test
    void translateTitleResultExposesSourceFallbackStatusWhenLocalQwenFails() {
        when(hannahTranslationClient.translate(any()))
                .thenReturn(sourceFallback());

        AlertTitleTranslationService.TranslationResult result =
                translationService.translateTitleWithResult("삼성전자 실적 개선", List.of());

        assertThat(result.translatedText()).isEmpty();
        assertThat(result.provider()).isEqualTo("source-language-fallback");
        assertThat(result.modelVersion()).isEqualTo(MODEL);
        assertThat(result.status()).isEqualTo("SOURCE_LANGUAGE_FALLBACK");
    }

    @Test
    void translateTextSplitsLongContentIntoChunks() {
        String longText = "삼성전자는 AI 서버 투자 확대로 실적 개선 기대가 커졌다. ".repeat(300);
        when(hannahTranslationClient.translate(any()))
                .thenAnswer(invocation -> {
                    HannahAiKoreanTranslationRequest request = invocation.getArgument(0);
                    return translated("EN:" + request.text().length());
                });

        String translatedText = translationService.translateText(longText);

        assertThat(translatedText).contains("EN:");
        verify(hannahTranslationClient, atLeast(2)).translate(any());
    }

    @Test
    void translateTextRejectsProviderOutputWithHangul() {
        when(hannahTranslationClient.translate(any()))
                .thenReturn(translated("Samsung Electronics expects 실적 improvement from AI server investment."));

        AlertTitleTranslationService.TranslationResult result = translationService.translateTextWithResult(
                "삼성전자는 AI 서버 투자 확대로 실적 개선 기대가 커졌다.",
                List.of());

        assertThat(result.translatedText()).isEmpty();
        assertThat(result.status()).isEqualTo("SOURCE_LANGUAGE_FALLBACK");
    }

    @Test
    void translateTextKeepsProviderSurfaceTermForGlossaryClickMapping() {
        when(hannahTranslationClient.translate(any()))
                .thenReturn(translated("Ants net bought Samsung Electronics."));

        String translatedText = translationService.translateText("개미가 삼성전자를 순매수했다.");

        assertThat(translatedText).isEqualTo("Ants net bought Samsung Electronics.");
    }

    @Test
    void translateTextPreservesLocalismSurfaceTermWhenProviderUsesGenericEnglish() {
        when(hannahTranslationClient.translate(any()))
                .thenReturn(translated("Retail investors net bought Samsung Electronics."));

        String plainTranslation = translationService.translateText("개미가 삼성전자를 순매수했다.");
        String glossaryTranslation = translationService.translateText(
                "개미가 삼성전자를 순매수했다.",
                List.of(new AlertGlossaryTerm("개미", "개미", "retail investors", "market_slang")));

        assertThat(plainTranslation).isEqualTo("Retail investors net bought Samsung Electronics.");
        assertThat(glossaryTranslation).isEqualTo("Retail investors net bought Samsung Electronics.");
        verify(hannahTranslationClient, times(2)).translate(any());
    }

    @Test
    void translateTextRepairsAntSurfaceToNaturalRetailInvestorsWhenGlossaryIsPresent() {
        when(hannahTranslationClient.translate(any()))
                .thenReturn(translated("Ants net bought Samsung Electronics."));

        String glossaryTranslation = translationService.translateText(
                "개미가 삼성전자를 순매수했다.",
                List.of(new AlertGlossaryTerm("개미", "개미", "retail investors", "market_slang")));

        assertThat(glossaryTranslation).isEqualTo("Retail investors net bought Samsung Electronics.");
    }

    private HannahAiKoreanTranslationResponse translated(String text) {
        return new HannahAiKoreanTranslationResponse(
                text,
                PROVIDER,
                MODEL,
                "TRANSLATED",
                "ko-en-qwen3-financial-translation-v1",
                List.of());
    }

    private HannahAiKoreanTranslationResponse sourceFallback() {
        return new HannahAiKoreanTranslationResponse(
                "",
                "source-language-fallback",
                MODEL,
                "SOURCE_LANGUAGE_FALLBACK",
                "ko-en-qwen3-financial-translation-v1",
                List.of("LOCAL_TRANSLATION_PROVIDER_ERROR"));
    }
}
