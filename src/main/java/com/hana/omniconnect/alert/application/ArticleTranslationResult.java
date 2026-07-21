package com.hana.omniconnect.alert.application;

import java.util.List;

/** Qwen 기사 분석에서 반환된 영문 결과와 품질 상태를 보관한다. */
public record ArticleTranslationResult(
        String translatedText,
        String provider,
        String modelVersion,
        String status,
        List<String> qualityFlags
) {

    public static final String STATUS_TRANSLATED = "TRANSLATED";
    public static final String STATUS_PARTIAL_SOURCE_LANGUAGE_FALLBACK = "PARTIAL_SOURCE_LANGUAGE_FALLBACK";
    public static final String STATUS_SOURCE_LANGUAGE_FALLBACK = "SOURCE_LANGUAGE_FALLBACK";
    public static final String PROVIDER_ALREADY_ENGLISH = "already-english";
    public static final String MODEL_TRANSLATION_UNAVAILABLE = "hannah-ai-translation-unavailable";

    public ArticleTranslationResult(
            String translatedText,
            String provider,
            String modelVersion,
            String status) {
        this(translatedText, provider, modelVersion, status, List.of());
    }

    public ArticleTranslationResult {
        qualityFlags = qualityFlags == null ? List.of() : List.copyOf(qualityFlags);
    }
}
