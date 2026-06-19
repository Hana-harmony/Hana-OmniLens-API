package com.hana.omnilens.alert.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.hana.omnilens.provider.translation.DeepLTranslationClient;
import com.hana.omnilens.provider.translation.PapagoTranslationClient;

class AlertTitleTranslationServiceTest {

    private final DeepLTranslationClient deepLTranslationClient = mock(DeepLTranslationClient.class);
    private final PapagoTranslationClient papagoTranslationClient = mock(PapagoTranslationClient.class);
    private final AlertTitleTranslationService translationService =
            new AlertTitleTranslationService(deepLTranslationClient, papagoTranslationClient);

    @Test
    void translateTitleReturnsDeepLTranslationFirst() {
        when(deepLTranslationClient.translateKoToEn("삼성전자 실적 개선"))
                .thenReturn("Samsung Electronics earnings improve");

        String translatedTitle = translationService.translateTitle("삼성전자 실적 개선");

        assertThat(translatedTitle).isEqualTo("Samsung Electronics earnings improve");
    }

    @Test
    void translateTitleFallsBackToPapagoWhenDeepLFails() {
        when(deepLTranslationClient.translateKoToEn("삼성전자 실적 개선"))
                .thenThrow(new IllegalStateException("missing deepl secret"));
        when(papagoTranslationClient.translateKoToEn("삼성전자 실적 개선"))
                .thenReturn("Samsung Electronics Papago fallback");

        String translatedTitle = translationService.translateTitle("삼성전자 실적 개선");

        assertThat(translatedTitle).isEqualTo("Samsung Electronics Papago fallback");
    }

    @Test
    void translateTitleFallsBackToOriginalWhenProviderFails() {
        when(deepLTranslationClient.translateKoToEn("삼성전자 실적 개선"))
                .thenThrow(new IllegalStateException("missing deepl secret"));
        when(papagoTranslationClient.translateKoToEn("삼성전자 실적 개선"))
                .thenThrow(new IllegalStateException("missing secret"));

        String translatedTitle = translationService.translateTitle("삼성전자 실적 개선");

        assertThat(translatedTitle).isEqualTo("삼성전자 실적 개선");
    }

    @Test
    void translateTitleFallsBackToOriginalWhenProviderReturnsBlank() {
        when(deepLTranslationClient.translateKoToEn("삼성전자 실적 개선"))
                .thenReturn("");
        when(papagoTranslationClient.translateKoToEn("삼성전자 실적 개선"))
                .thenReturn("");

        String translatedTitle = translationService.translateTitle("삼성전자 실적 개선");

        assertThat(translatedTitle).isEqualTo("삼성전자 실적 개선");
    }
}
