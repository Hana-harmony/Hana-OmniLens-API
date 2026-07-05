package com.hana.omnilens.provider.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HannahAiKoreanTranslationResponse(
        @JsonProperty("translated_text") String translatedText,
        String provider,
        @JsonProperty("model_version") String modelVersion,
        String status,
        @JsonProperty("prompt_version") String promptVersion,
        @JsonProperty("quality_flags") List<String> qualityFlags
) {
    public HannahAiKoreanTranslationResponse {
        translatedText = translatedText == null ? "" : translatedText;
        provider = provider == null ? "" : provider;
        modelVersion = modelVersion == null ? "" : modelVersion;
        status = status == null ? "" : status;
        promptVersion = promptVersion == null ? "" : promptVersion;
        qualityFlags = qualityFlags == null ? List.of() : List.copyOf(qualityFlags);
    }
}
