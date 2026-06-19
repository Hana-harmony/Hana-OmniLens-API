package com.hana.omnilens.alert.domain;

public record AlertGlossaryTerm(
        String sourceTerm,
        String normalizedTerm,
        String englishTerm,
        String category
) {
}
