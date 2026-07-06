package com.hana.omnilens.alert.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.hana.omnilens.alert.domain.AlertGlossaryTerm;

class KoreanMarketGlossaryTermExtractorTest {

    private final KoreanMarketGlossaryTermExtractor extractor = new KoreanMarketGlossaryTermExtractor();

    @Test
    void filterDisplayableTermsRejectsGenericFinancialWords() {
        List<AlertGlossaryTerm> terms = extractor.filterDisplayableTerms(List.of(
                new AlertGlossaryTerm("earnings", "실적", "earnings", "event"),
                new AlertGlossaryTerm("Foreign investors", "외국인", "foreign investors", "investor_type"),
                new AlertGlossaryTerm("Samjeon Nix", "삼전닉스", "Samjeon Nix", "market_slang"),
                new AlertGlossaryTerm("Daejangju", "대장주", "bellwether stock", "market_slang")));

        assertThat(terms)
                .extracting(AlertGlossaryTerm::normalizedTerm)
                .containsExactly("삼전닉스", "대장주");
    }

    @Test
    void supplementKeepsOnlyDictionaryBackedExistingTerms() {
        List<AlertGlossaryTerm> terms = extractor.supplement(
                List.of(
                        new AlertGlossaryTerm("earnings", "실적", "earnings", "event"),
                        new AlertGlossaryTerm("Ants", "개미", "retail investors", "market_slang"),
                        new AlertGlossaryTerm("Foreign investors", "외국인", "foreign investors", "investor_type")),
                "Ants net bought Samsung Electronics while earnings expectations improved.");

        assertThat(terms)
                .singleElement()
                .satisfies(term -> {
                    assertThat(term.normalizedTerm()).isEqualTo("개미");
                    assertThat(term.description()).contains("individual retail investors");
                });
    }

    @Test
    void supplementRecognizesSamnikAsSamjeonNixDictionaryTerm() {
        List<AlertGlossaryTerm> terms = extractor.supplement(
                List.of(),
                "삼닉 레버리지로 손실 입은 개미들이 매도 여부를 고민했다.");

        assertThat(terms)
                .extracting(AlertGlossaryTerm::sourceTerm, AlertGlossaryTerm::normalizedTerm)
                .contains(org.assertj.core.api.Assertions.tuple("삼닉", "삼전닉스"));
    }
}
