package com.hana.omnilens.provider.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HannahAiGlossaryTerm(
        @JsonProperty("source_term") String sourceTerm,
        @JsonProperty("normalized_term") String normalizedTerm,
        @JsonProperty("english_term") String englishTerm,
        String category,
        String description
) {
    public HannahAiGlossaryTerm(String sourceTerm, String normalizedTerm, String englishTerm, String category) {
        this(sourceTerm, normalizedTerm, englishTerm, category, "");
    }

    public HannahAiGlossaryTerm {
        sourceTerm = sourceTerm == null ? "" : sourceTerm;
        normalizedTerm = normalizedTerm == null ? "" : normalizedTerm;
        englishTerm = englishTerm == null ? "" : englishTerm;
        category = category == null ? "" : category;
        description = description == null ? "" : description;
    }
}
