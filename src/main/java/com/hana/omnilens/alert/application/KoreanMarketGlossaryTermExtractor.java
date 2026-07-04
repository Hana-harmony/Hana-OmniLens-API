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
                    List.of("sell-side sidecar", "buy-side sidecar", "sidecar"),
                    "Temporary program-trading halt used in Korea when index futures move sharply."),
            new DictionaryTerm("서킷브레이커", "Circuit breaker", "market_mechanism",
                    List.of("sell-side circuit breaker", "buy-side circuit breaker", "circuit breaker"),
                    "Market-wide trading halt triggered by a large index move."),
            new DictionaryTerm("코스피", "KOSPI", "index",
                    List.of("KOSPI"),
                    "Korea Composite Stock Price Index, the main benchmark for listed Korean equities."),
            new DictionaryTerm("코스닥", "KOSDAQ", "index",
                    List.of("KOSDAQ"),
                    "Korea's growth-stock market index, similar in role to a tech-heavy exchange benchmark."),
            new DictionaryTerm("코스피200", "KOSPI 200", "index",
                    List.of("KOSPI 200"),
                    "Index of 200 major KOSPI-listed stocks used for futures, options, and ETFs."),
            new DictionaryTerm("코스닥150", "KOSDAQ 150", "index",
                    List.of("KOSDAQ 150"),
                    "Index of 150 representative KOSDAQ stocks used for ETFs and derivatives."),
            new DictionaryTerm("코리아 디스카운트", "Korea discount", "market_term",
                    List.of("Korea discount"),
                    "Valuation discount often applied to Korean equities due to governance, dividend, or geopolitical concerns."),
            new DictionaryTerm("밸류업", "Value-up", "policy_term",
                    List.of("value-up", "Value-up"),
                    "Korean market policy theme encouraging companies to improve valuation and shareholder returns."),
            new DictionaryTerm("저PBR", "Low PBR", "valuation_term",
                    List.of("low PBR", "Low PBR"),
                    "Low price-to-book ratio stock, often mentioned in Korea's value-up policy context."),
            new DictionaryTerm("개미", "Retail investors", "investor_slang",
                    List.of("retail investors", "ants", "ant", "gaemee", "gaemi"),
                    "Korean stock-market slang for individual retail investors."),
            new DictionaryTerm("대장주", "Market Leader", "market_slang",
                    List.of("Daejangju", "daejangju", "bellwether stock", "market leader stock", "leading stock"),
                    "Refers to the leading stock in a particular sector or the entire market that dictates the overall trend."),
            new DictionaryTerm("따따블", "Ttattabeul", "ipo_slang",
                    List.of("ttattabeul", "quadruple debut", "fourfold debut"),
                    "Korean IPO slang for a stock jumping to four times its offering price on debut."),
            new DictionaryTerm("품절주", "Limited-float stock", "market_slang",
                    List.of("limited-float stock", "scarce-float stock"),
                    "A stock with very limited tradable float, often prone to sharp price moves."),
            new DictionaryTerm("빚투", "Leveraged retail investing", "risk_slang",
                    List.of("leveraged retail investing", "debt-funded investing"),
                    "Korean market slang for leveraged retail investing funded with borrowed money."),
            new DictionaryTerm("어닝쇼크", "Earnings shock", "event",
                    List.of("earnings shock"),
                    "An earnings result materially below market expectations."),
            new DictionaryTerm("어닝서프라이즈", "Earnings surprise", "event",
                    List.of("earnings surprise"),
                    "An earnings result materially above market expectations."),
            new DictionaryTerm("삼전닉스", "Samjeon Nix", "market_slang",
                    List.of("Samjeon Nix", "Samjeon Nix Gaja", "Samjeon-Nix", "SamjeonNix",
                            "삼전닉스", "삼전 닉스", "삼전·닉스", "삼전-닉스"),
                    "Korean market slang combining Samsung Electronics and SK Hynix, usually referring to the two dominant semiconductor bellwethers."),
            new DictionaryTerm("가즈아", "Gaja", "market_slang",
                    List.of("Gaja", "go go", "가즈아"),
                    "Korean investor slang used like a rallying cry meaning roughly 'let's go' for a rising trade."));

    public List<AlertGlossaryTerm> supplement(
            List<AlertGlossaryTerm> existingTerms,
            String... texts) {
        Map<String, AlertGlossaryTerm> termsBySurface = new LinkedHashMap<>();
        if (existingTerms != null) {
            for (AlertGlossaryTerm term : existingTerms) {
                if (isDictionaryTerm(term)) {
                    String key = termKey(term);
                    if (StringUtils.hasText(key)) {
                        termsBySurface.put(key, term);
                    }
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
                String key = surface.toLowerCase(Locale.ROOT);
                AlertGlossaryTerm existingTerm = termsBySurface.get(key);
                if (existingTerm == null) {
                    termsBySurface.put(
                            key,
                            new AlertGlossaryTerm(
                                    surface,
                                    term.normalizedTerm(),
                                    term.englishTerm(),
                                    term.category(),
                                    term.description()));
                } else if (!StringUtils.hasText(existingTerm.description())) {
                    termsBySurface.put(
                            key,
                            new AlertGlossaryTerm(
                                    existingTerm.sourceTerm(),
                                    existingTerm.normalizedTerm(),
                                    existingTerm.englishTerm(),
                                    existingTerm.category(),
                                    term.description()));
                }
            }
        }
        return new ArrayList<>(termsBySurface.values());
    }

    public List<AlertGlossaryTerm> filterDisplayableTerms(List<AlertGlossaryTerm> terms) {
        if (terms == null || terms.isEmpty()) {
            return List.of();
        }
        List<AlertGlossaryTerm> displayableTerms = new ArrayList<>();
        for (AlertGlossaryTerm term : terms) {
            if (isDictionaryTerm(term)) {
                displayableTerms.add(term);
            }
        }
        return displayableTerms;
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

    private boolean isDictionaryTerm(AlertGlossaryTerm term) {
        if (term == null) {
            return false;
        }
        for (DictionaryTerm dictionaryTerm : TERMS) {
            if (matches(dictionaryTerm, term.normalizedTerm())
                    || matches(dictionaryTerm, term.sourceTerm())
                    || matches(dictionaryTerm, term.englishTerm())) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(DictionaryTerm dictionaryTerm, String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalizedValue = value.trim();
        if (dictionaryTerm.normalizedTerm().equalsIgnoreCase(normalizedValue)
                || dictionaryTerm.englishTerm().equalsIgnoreCase(normalizedValue)) {
            return true;
        }
        return dictionaryTerm.surfaces().stream()
                .anyMatch(surface -> surface.equalsIgnoreCase(normalizedValue));
    }

    private String termKey(AlertGlossaryTerm term) {
        String key = firstText(term.sourceTerm(), term.normalizedTerm(), term.englishTerm());
        return StringUtils.hasText(key) ? key.toLowerCase(Locale.ROOT) : "";
    }

    private String firstText(String first, String second, String third) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        if (StringUtils.hasText(second)) {
            return second;
        }
        return third;
    }

    private record DictionaryTerm(
            String normalizedTerm,
            String englishTerm,
            String category,
            List<String> surfaces,
            String description
    ) {
    }
}
