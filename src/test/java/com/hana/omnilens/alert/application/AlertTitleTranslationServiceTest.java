package com.hana.omnilens.alert.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.hana.omnilens.alert.domain.AlertGlossaryTerm;
import com.hana.omnilens.provider.ai.HannahAiKoreanTranslationClient;
import com.hana.omnilens.provider.ai.HannahAiKoreanTranslationRequest;
import com.hana.omnilens.provider.ai.HannahAiKoreanTranslationResponse;

class AlertTitleTranslationServiceTest {

    private static final String PROVIDER = "local-open-source-qwen3-translation";
    private static final String MODEL = "local-llm:Qwen3-4B-GGUF-Q4";

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
    void translateTitleExposesEmptySourceFallbackWhenProviderFails() {
        when(hannahTranslationClient.translate(any()))
                .thenThrow(new IllegalStateException("hannah ai unavailable"));

        String translatedTitle = translationService.translateTitle("삼성전자 실적 개선");

        assertThat(translatedTitle).isEmpty();
    }

    @Test
    void translateTitleExposesEmptySourceFallbackWhenProviderReturnsBlank() {
        when(hannahTranslationClient.translate(any()))
                .thenReturn(translated(""));

        String translatedTitle = translationService.translateTitle("삼성전자 실적 개선");

        assertThat(translatedTitle).isEmpty();
    }

    @Test
    void translateTitleRejectsBrokenHannahTitleFragments() {
        when(hannahTranslationClient.translate(any()))
                .thenReturn(translated("韓Korean stock market \" \""));

        AlertTitleTranslationService.TranslationResult result =
                translationService.translateTitleWithResult(
                        "장동혁, 韓증시 널뛰기 장세에 \"'블랙 에브리데이' 될까 걱정\"",
                        List.of());

        assertThat(result.translatedText()).isEmpty();
        assertThat(result.provider()).isEqualTo("source-language-fallback");
        assertThat(result.status()).isEqualTo("SOURCE_LANGUAGE_FALLBACK");
    }

    @Test
    void translateTitleRejectsTickerOnlyFragments() {
        when(hannahTranslationClient.translate(any()))
                .thenReturn(translated("KOSPI 3%↑"));

        AlertTitleTranslationService.TranslationResult result =
                translationService.translateTitleWithResult("미국 반도체주 훈풍에 코스피 장초반 3%↑", List.of());

        assertThat(result.translatedText()).isEmpty();
        assertThat(result.provider()).isEqualTo("source-language-fallback");
        assertThat(result.status()).isEqualTo("SOURCE_LANGUAGE_FALLBACK");
    }

    @Test
    void translateTitleAcceptsInternalNewsEllipsis() {
        when(hannahTranslationClient.translate(any()))
                .thenReturn(translated(
                        "Surge in short selling against Samjeon Nix... Foreign investors bet on decline"));

        AlertTitleTranslationService.TranslationResult result =
                translationService.translateTitleWithResult(
                        "‘삼전닉스’에 공매도 급증…외국인들 하락 베팅 나서나 [투자360]",
                        List.of());

        assertThat(result.translatedText())
                .isEqualTo("Surge in short selling against Samjeon Nix... Foreign investors bet on decline");
        assertThat(result.status()).isEqualTo("TRANSLATED");
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
    void translateTitleDoesNotCacheSourceFallbackFailure() {
        when(hannahTranslationClient.translate(any()))
                .thenReturn(sourceFallback())
                .thenReturn(translated("Samsung Electronics earnings improve"));

        AlertTitleTranslationService.TranslationResult firstResult =
                translationService.translateTitleWithResult("삼성전자 실적 개선", List.of());
        AlertTitleTranslationService.TranslationResult secondResult =
                translationService.translateTitleWithResult("삼성전자 실적 개선", List.of());

        assertThat(firstResult.translatedText()).isEmpty();
        assertThat(secondResult.translatedText()).isEqualTo("Samsung Electronics earnings improve");
        verify(hannahTranslationClient, times(2)).translate(any());
    }

    @Test
    void translateTitleRejectsUsableTextWhenProviderMarksSourceFallback() {
        when(hannahTranslationClient.translate(any()))
                .thenReturn(new HannahAiKoreanTranslationResponse(
                        "KOSPI closes lower as foreign selling weighs",
                        "source-language-fallback",
                        MODEL,
                        "SOURCE_LANGUAGE_FALLBACK",
                        "ko-en-qwen3-financial-translation-v1",
                        List.of()));

        AlertTitleTranslationService.TranslationResult result =
                translationService.translateTitleWithResult("외국인 매도에 코스피 하락 마감", List.of());

        assertThat(result.translatedText()).isEmpty();
        assertThat(result.provider()).isEqualTo("source-language-fallback");
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
    void translateTextSplitsFullArticleIntoQwenSizedChunks() {
        String longText = "삼성전자는 AI 서버 투자 확대로 실적 개선 기대가 커졌다. ".repeat(300);
        when(hannahTranslationClient.translate(any()))
                .thenAnswer(invocation -> {
                    HannahAiKoreanTranslationRequest request = invocation.getArgument(0);
                    return translated("EN:" + request.text().length());
                });

        String translatedText = translationService.translateText(longText);

        assertThat(translatedText).contains("EN:");
        ArgumentCaptor<HannahAiKoreanTranslationRequest> captor =
                ArgumentCaptor.forClass(HannahAiKoreanTranslationRequest.class);
        verify(hannahTranslationClient, atLeast(2)).translate(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(request -> assertThat(request.text()).hasSizeLessThanOrEqualTo(1_500));
    }

    @Test
    void translateTextDoesNotCacheLongArticleBodyTranslations() {
        String longText = "삼성전자는 AI 서버 투자 확대로 실적 개선 기대가 커졌다. ".repeat(40);
        when(hannahTranslationClient.translate(any()))
                .thenReturn(translated("First full article translation."))
                .thenReturn(translated("Second full article translation."));

        String first = translationService.translateText(longText);
        String second = translationService.translateText(longText);

        assertThat(first).isEqualTo("First full article translation.");
        assertThat(second).isEqualTo("Second full article translation.");
        verify(hannahTranslationClient, times(2)).translate(any());
    }

    @Test
    void translateTextForDisclosureSendsDisclosureSourceTypeToHannah() {
        when(hannahTranslationClient.translate(any()))
                .thenReturn(translated("Samsung Electronics disclosed preliminary results."));

        translationService.translateTextWithResult(
                "삼성전자/연결재무제표기준영업(잠정)실적(공정공시)",
                List.of(),
                "DISCLOSURE");

        ArgumentCaptor<HannahAiKoreanTranslationRequest> captor =
                ArgumentCaptor.forClass(HannahAiKoreanTranslationRequest.class);
        verify(hannahTranslationClient).translate(captor.capture());
        assertThat(captor.getValue().sourceType()).isEqualTo("DISCLOSURE");
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
        assertThat(result.qualityFlags()).contains("HANGUL_REMAINS");
    }

    @Test
    void translateTextKeepsLocalQualityFlagWhenProviderReturnsSourceText() {
        String sourceText = "삼성전자는 AI 서버 투자 확대로 반도체 실적 개선 기대가 커졌다. "
                + "투자자는 영업이익 회복 속도를 확인해야 한다.";
        when(hannahTranslationClient.translate(any()))
                .thenReturn(translated(sourceText));

        AlertTitleTranslationService.TranslationResult result = translationService.translateTextWithResult(
                sourceText,
                List.of());

        assertThat(result.translatedText()).isEmpty();
        assertThat(result.status()).isEqualTo("SOURCE_LANGUAGE_FALLBACK");
        assertThat(result.qualityFlags()).contains("SOURCE_LANGUAGE_FALLBACK");
    }

    @Test
    void translateTextKeepsProviderSurfaceTermForGlossaryClickMapping() {
        when(hannahTranslationClient.translate(any()))
                .thenReturn(translated("Ants net bought Samsung Electronics."));

        String translatedText = translationService.translateText("개미가 삼성전자를 순매수했다.");

        assertThat(translatedText).isEqualTo("Ants net bought Samsung Electronics.");
    }

    @Test
    void translateTextUsesAntSurfaceWhenProviderUsesGenericEnglish() {
        when(hannahTranslationClient.translate(any()))
                .thenReturn(translated("Retail investors net bought Samsung Electronics."));

        String plainTranslation = translationService.translateText("개미가 삼성전자를 순매수했다.");
        String glossaryTranslation = translationService.translateText(
                "개미가 삼성전자를 순매수했다.",
                List.of(new AlertGlossaryTerm("개미", "개미", "retail investors", "market_slang")));

        assertThat(plainTranslation).isEqualTo("Retail investors net bought Samsung Electronics.");
        assertThat(glossaryTranslation).isEqualTo("Ants net bought Samsung Electronics.");
        verify(hannahTranslationClient, times(2)).translate(any());
    }

    @Test
    void translateTextPreservesAntSurfaceWhenGlossaryIsPresent() {
        when(hannahTranslationClient.translate(any()))
                .thenReturn(translated("Ants net bought Samsung Electronics."));

        String glossaryTranslation = translationService.translateText(
                "개미가 삼성전자를 순매수했다.",
                List.of(new AlertGlossaryTerm("개미", "개미", "retail investors", "market_slang")));

        assertThat(glossaryTranslation).isEqualTo("Ants net bought Samsung Electronics.");
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
