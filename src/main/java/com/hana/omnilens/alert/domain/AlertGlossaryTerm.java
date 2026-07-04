package com.hana.omnilens.alert.domain;

public record AlertGlossaryTerm(
        String sourceTerm,
        String normalizedTerm,
        String englishTerm,
        String category,
        String description
) {

    public AlertGlossaryTerm(String sourceTerm, String normalizedTerm, String englishTerm, String category) {
        this(sourceTerm, normalizedTerm, englishTerm, category, "");
    }

    public AlertGlossaryTerm {
        sourceTerm = sourceTerm == null ? "" : sourceTerm;
        normalizedTerm = normalizedTerm == null ? "" : normalizedTerm;
        englishTerm = englishTerm == null ? "" : englishTerm;
        category = category == null ? "" : category;
        description = description == null ? "" : description;
    }
}
