package com.hana.omnilens.alert.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void translateTitleReturnsLocalEnglishFallbackWhenProviderFails() {
        when(hannahTranslationClient.translate(any()))
                .thenThrow(new IllegalStateException("hannah ai unavailable"));

        String translatedTitle = translationService.translateTitle("삼성전자 실적 개선");

        assertThat(translatedTitle).contains("Korean market source item");
    }

    @Test
    void translateTitleReturnsLocalEnglishFallbackWhenProviderReturnsBlank() {
        when(hannahTranslationClient.translate(any()))
                .thenReturn(translated(""));

        String translatedTitle = translationService.translateTitle("삼성전자 실적 개선");

        assertThat(translatedTitle).contains("Korean market source item");
    }

    @Test
    void translateTitleRejectsBrokenHannahTitleFragments() {
        when(hannahTranslationClient.translate(any()))
                .thenReturn(translated("韓Korean stock market \" \""));

        AlertTitleTranslationService.TranslationResult result =
                translationService.translateTitleWithResult(
                        "장동혁, 韓증시 널뛰기 장세에 \"'블랙 에브리데이' 될까 걱정\"",
                        List.of());

        assertThat(result.translatedText()).contains("Korean market source item");
        assertThat(result.provider()).isEqualTo("source-language-fallback");
        assertThat(result.status()).isEqualTo("SOURCE_LANGUAGE_FALLBACK");
    }

    @Test
    void translateTitleRejectsTickerOnlyFragments() {
        when(hannahTranslationClient.translate(any()))
                .thenReturn(translated("KOSPI 3%↑"));

        AlertTitleTranslationService.TranslationResult result =
                translationService.translateTitleWithResult("미국 반도체주 훈풍에 코스피 장초반 3%↑", List.of());

        assertThat(result.translatedText()).contains("Korean market source item");
        assertThat(result.provider()).isEqualTo("source-language-fallback");
        assertThat(result.status()).isEqualTo("SOURCE_LANGUAGE_FALLBACK");
    }

    @Test
    void translateTitleResultExposesSourceFallbackStatusWhenLocalQwenFails() {
        when(hannahTranslationClient.translate(any()))
                .thenReturn(sourceFallback());

        AlertTitleTranslationService.TranslationResult result =
                translationService.translateTitleWithResult("삼성전자 실적 개선", List.of());

        assertThat(result.translatedText()).contains("Korean market source item");
        assertThat(result.provider()).isEqualTo("source-language-fallback");
        assertThat(result.modelVersion()).isEqualTo(MODEL);
        assertThat(result.status()).isEqualTo("SOURCE_LANGUAGE_FALLBACK");
    }

    @Test
    void translateTitleKeepsAlreadyEnglishTextWithoutCallingHannah() {
        AlertTitleTranslationService.TranslationResult result =
                translationService.translateTitleWithResult(
                        "KOSPI and KOSDAQ plunge as sell-side circuit breakers trigger",
                        List.of());

        assertThat(result.translatedText())
                .isEqualTo("KOSPI and KOSDAQ plunge as sell-side circuit breakers trigger");
        assertThat(result.provider()).isEqualTo("already-english");
        assertThat(result.status()).isEqualTo("TRANSLATED");
        verify(hannahTranslationClient, never()).translate(any());
    }

    @Test
    void translateTextSendsFullArticleWithinHannahRequestLimitWithoutChunking() {
        String longText = "삼성전자는 AI 서버 투자 확대로 실적 개선 기대가 커졌다. ".repeat(300);
        when(hannahTranslationClient.translate(any()))
                .thenAnswer(invocation -> {
                    HannahAiKoreanTranslationRequest request = invocation.getArgument(0);
                    return translated("EN:" + request.text().length());
                });

        String translatedText = translationService.translateText(longText);

        assertThat(translatedText).contains("EN:");
        verify(hannahTranslationClient, times(1)).translate(any());
    }

    @Test
    void translateTextRejectsProviderOutputWithHangul() {
        when(hannahTranslationClient.translate(any()))
                .thenReturn(translated("Samsung Electronics expects 실적 improvement from AI server investment."));

        AlertTitleTranslationService.TranslationResult result = translationService.translateTextWithResult(
                "삼성전자는 AI 서버 투자 확대로 실적 개선 기대가 커졌다.",
                List.of());

        assertThat(result.translatedText()).contains("Korean market source item");
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
