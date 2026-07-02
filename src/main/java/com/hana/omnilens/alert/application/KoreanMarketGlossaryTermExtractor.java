package com.hana.omnilens.alert.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

import com.hana.omnilens.alert.domain.AlertGlossaryTerm;

public class KoreanMarketGlossaryTermExtractor {

    private static final List<DictionaryTerm> TERMS = List.of(
            new DictionaryTerm("사이드카", "Sidecar", "market_mechanism",
                    List.of("sell-side sidecar", "buy-side sidecar", "sidecar")),
            new DictionaryTerm("서킷브레이커", "Circuit breaker", "market_mechanism",
                    List.of("sell-side circuit breaker", "buy-side circuit breaker", "circuit breaker")),
            new DictionaryTerm("코스피", "KOSPI", "index",
                    List.of("KOSPI")),
            new DictionaryTerm("코스닥", "KOSDAQ", "index",
                    List.of("KOSDAQ")),
            new DictionaryTerm("코스피200", "KOSPI 200", "index",
                    List.of("KOSPI 200")),
            new DictionaryTerm("코스닥150", "KOSDAQ 150", "index",
                    List.of("KOSDAQ 150")),
            new DictionaryTerm("코리아 디스카운트", "Korea discount", "market_term",
                    List.of("Korea discount")),
            new DictionaryTerm("밸류업", "Value-up", "policy_term",
                    List.of("value-up", "Value-up")),
            new DictionaryTerm("저PBR", "Low PBR", "valuation_term",
                    List.of("low PBR", "Low PBR")),
            new DictionaryTerm("개미", "Retail investors", "investor_slang",
                    List.of("retail investors", "ants", "ant", "gaemee", "gaemi")),
            new DictionaryTerm("따따블", "Ttattabeul", "ipo_slang",
                    List.of("ttattabeul", "quadruple debut", "fourfold debut")),
            new DictionaryTerm("품절주", "Limited-float stock", "market_slang",
                    List.of("limited-float stock", "scarce-float stock")));

    public List<AlertGlossaryTerm> supplement(
            List<AlertGlossaryTerm> existingTerms,
            String... texts) {
        Map<String, AlertGlossaryTerm> termsBySurface = new LinkedHashMap<>();
        if (existingTerms != null) {
            for (AlertGlossaryTerm term : existingTerms) {
                if (StringUtils.hasText(term.sourceTerm())) {
                    termsBySurface.put(term.sourceTerm().toLowerCase(Locale.ROOT), term);
                }
            }
        }

        String joinedText = String.join("\n", nonNullTexts(texts));
        if (!StringUtils.hasText(joinedText)) {
            return new ArrayList<>(termsBySurface.values());
        }
        for (DictionaryTerm term : TERMS) {
            String surface = firstSurface(joinedText, term.surfaces());
            if (StringUtils.hasText(surface)) {
                termsBySurface.putIfAbsent(
                        surface.toLowerCase(Locale.ROOT),
                        new AlertGlossaryTerm(surface, term.normalizedTerm(), term.englishTerm(), term.category()));
            }
        }
        return new ArrayList<>(termsBySurface.values());
    }

    private List<String> nonNullTexts(String... texts) {
        List<String> values = new ArrayList<>();
        if (texts == null) {
            return values;
        }
        for (String text : texts) {
            if (StringUtils.hasText(text)) {
                values.add(text);
            }
        }
        return values;
    }

    private String firstSurface(String text, List<String> surfaces) {
        for (String surface : surfaces) {
            Pattern pattern = Pattern.compile(
                    "(?<![0-9A-Za-z])" + Pattern.quote(surface) + "(?![0-9A-Za-z])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group();
            }
        }
        return "";
    }

    private record DictionaryTerm(
            String normalizedTerm,
            String englishTerm,
            String category,
            List<String> surfaces
    ) {
    }
}
