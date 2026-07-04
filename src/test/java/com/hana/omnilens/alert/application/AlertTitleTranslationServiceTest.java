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
import com.hana.omnilens.provider.translation.OpenAiTranslationClient;

class AlertTitleTranslationServiceTest {

    private final OpenAiTranslationClient openAiTranslationClient = mock(OpenAiTranslationClient.class);
    private final AlertTitleTranslationService translationService =
            new AlertTitleTranslationService(openAiTranslationClient);

    @Test
    void translateTitleReturnsGptTranslationFirst() {
        when(openAiTranslationClient.translateKoToEn("삼성전자 실적 개선"))
                .thenReturn("Samsung Electronics earnings improve");

        String translatedTitle = translationService.translateTitle("삼성전자 실적 개선");

        assertThat(translatedTitle).isEqualTo("Samsung Electronics earnings improve");
    }

    @Test
    void translateTitleFallsBackToOriginalWhenProviderFails() {
        when(openAiTranslationClient.translateKoToEn("삼성전자 실적 개선"))
                .thenThrow(new IllegalStateException("missing openai secret"));

        String translatedTitle = translationService.translateTitle("삼성전자 실적 개선");

        assertThat(translatedTitle).isEqualTo("삼성전자 실적 개선");
    }

    @Test
    void translateTitleFallsBackToOriginalWhenProviderReturnsBlank() {
        when(openAiTranslationClient.translateKoToEn("삼성전자 실적 개선"))
                .thenReturn("");

        String translatedTitle = translationService.translateTitle("삼성전자 실적 개선");

        assertThat(translatedTitle).isEqualTo("삼성전자 실적 개선");
    }

    @Test
    void translateTitleResultExposesSourceFallbackStatusWhenGptFails() {
        when(openAiTranslationClient.translateKoToEn("삼성전자 실적 개선"))
                .thenReturn("");

        AlertTitleTranslationService.TranslationResult result =
                translationService.translateTitleWithResult("삼성전자 실적 개선", List.of());

        assertThat(result.translatedText()).isEqualTo("삼성전자 실적 개선");
        assertThat(result.provider()).isEqualTo("source-language-fallback");
        assertThat(result.status()).isEqualTo("SOURCE_LANGUAGE_FALLBACK");
    }

    @Test
    void translateTextSplitsLongContentIntoChunks() {
        String longText = "삼성전자는 AI 서버 투자 확대로 실적 개선 기대가 커졌다. ".repeat(120);
        when(openAiTranslationClient.translateKoToEn(any()))
                .thenAnswer(invocation -> "EN:" + invocation.getArgument(0, String.class).length());

        String translatedText = translationService.translateText(longText);

        assertThat(translatedText).contains("EN:");
        verify(openAiTranslationClient, atLeast(2)).translateKoToEn(any());
    }

    @Test
    void translateTextRejectsProviderOutputWithHangul() {
        when(openAiTranslationClient.translateKoToEn("삼성전자는 AI 서버 투자 확대로 실적 개선 기대가 커졌다."))
                .thenReturn("Samsung Electronics expects 실적 improvement from AI server investment.");

        AlertTitleTranslationService.TranslationResult result = translationService.translateTextWithResult(
                "삼성전자는 AI 서버 투자 확대로 실적 개선 기대가 커졌다.",
                List.of());

        assertThat(result.translatedText()).isEqualTo("삼성전자는 AI 서버 투자 확대로 실적 개선 기대가 커졌다.");
        assertThat(result.status()).isEqualTo("SOURCE_LANGUAGE_FALLBACK");
    }

    @Test
    void translateTextKeepsProviderSurfaceTermForGlossaryClickMapping() {
        when(openAiTranslationClient.translateKoToEn("개미가 삼성전자를 순매수했다."))
                .thenReturn("Ants net bought Samsung Electronics.");

        String translatedText = translationService.translateText("개미가 삼성전자를 순매수했다.");

        assertThat(translatedText).isEqualTo("Ants net bought Samsung Electronics.");
    }

    @Test
    void translateTextPreservesLocalismSurfaceTermWhenProviderUsesGenericEnglish() {
        when(openAiTranslationClient.translateKoToEn("개미가 삼성전자를 순매수했다."))
                .thenReturn("Retail investors net bought Samsung Electronics.");

        String plainTranslation = translationService.translateText("개미가 삼성전자를 순매수했다.");
        String glossaryTranslation = translationService.translateText(
                "개미가 삼성전자를 순매수했다.",
                List.of(new AlertGlossaryTerm("개미", "개미", "retail investors", "market_slang")));

        assertThat(plainTranslation).isEqualTo("Retail investors net bought Samsung Electronics.");
        assertThat(glossaryTranslation).isEqualTo("Ants net bought Samsung Electronics.");
        verify(openAiTranslationClient, times(2)).translateKoToEn("개미가 삼성전자를 순매수했다.");
    }
}
