package com.hana.omniconnect.provider.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HannahAiKoreanTranslationRequest(
        String text,
        @JsonProperty("source_language") String sourceLanguage,
        @JsonProperty("target_language") String targetLanguage,
        @JsonProperty("source_type") String sourceType,
        String title,
        @JsonProperty("glossary_terms") List<HannahAiGlossaryTerm> glossaryTerms
) {
    public HannahAiKoreanTranslationRequest {
        sourceLanguage = sourceLanguage == null || sourceLanguage.isBlank() ? "ko" : sourceLanguage;
        targetLanguage = targetLanguage == null || targetLanguage.isBlank() ? "en" : targetLanguage;
        sourceType = sourceType == null || sourceType.isBlank() ? "NEWS" : sourceType;
        title = title == null ? "" : title;
        glossaryTerms = glossaryTerms == null ? List.of() : List.copyOf(glossaryTerms);
    }
}
