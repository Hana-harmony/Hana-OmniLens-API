package com.hana.omnilens.provider.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HannahAiGlossaryTerm(
        @JsonProperty("source_term") String sourceTerm,
        @JsonProperty("normalized_term") String normalizedTerm,
        @JsonProperty("english_term") String englishTerm,
        String category
) {
}
