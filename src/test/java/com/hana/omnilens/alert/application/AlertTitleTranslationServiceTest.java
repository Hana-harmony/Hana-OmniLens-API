package com.hana.omnilens.alert.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.hana.omnilens.provider.translation.DeepLTranslationClient;

class AlertTitleTranslationServiceTest {

    private final DeepLTranslationClient deepLTranslationClient = mock(DeepLTranslationClient.class);
    private final AlertTitleTranslationService translationService =
            new AlertTitleTranslationService(deepLTranslationClient);

    @Test
    void translateTitleReturnsDeepLTranslationFirst() {
        when(deepLTranslationClient.translateKoToEn("삼성전자 실적 개선"))
                .thenReturn("Samsung Electronics earnings improve");

        String translatedTitle = translationService.translateTitle("삼성전자 실적 개선");

        assertThat(translatedTitle).isEqualTo("Samsung Electronics earnings improve");
    }

    @Test
    void translateTitleFallsBackToOriginalWhenProviderFails() {
        when(deepLTranslationClient.translateKoToEn("삼성전자 실적 개선"))
                .thenThrow(new IllegalStateException("missing deepl secret"));

        String translatedTitle = translationService.translateTitle("삼성전자 실적 개선");

        assertThat(translatedTitle).isEqualTo("삼성전자 실적 개선");
    }

    @Test
    void translateTitleFallsBackToOriginalWhenProviderReturnsBlank() {
        when(deepLTranslationClient.translateKoToEn("삼성전자 실적 개선"))
                .thenReturn("");

        String translatedTitle = translationService.translateTitle("삼성전자 실적 개선");

        assertThat(translatedTitle).isEqualTo("삼성전자 실적 개선");
    }

    @Test
    void translateTextSplitsLongContentIntoChunks() {
        String longText = "삼성전자는 AI 서버 투자 확대로 실적 개선 기대가 커졌다. ".repeat(120);
        when(deepLTranslationClient.translateKoToEn(any()))
                .thenAnswer(invocation -> "EN:" + invocation.getArgument(0, String.class).length());

        String translatedText = translationService.translateText(longText);

        assertThat(translatedText).contains("EN:");
        verify(deepLTranslationClient, atLeast(2)).translateKoToEn(any());
    }
}
