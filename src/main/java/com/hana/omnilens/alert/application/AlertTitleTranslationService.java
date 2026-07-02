package com.hana.omnilens.alert.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.hana.omnilens.alert.domain.AlertGlossaryTerm;
import com.hana.omnilens.provider.translation.DeepLTranslationClient;

@Service
public class AlertTitleTranslationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertTitleTranslationService.class);
    private static final int MAX_CHUNK_CHARS = 4_000;

    private final DeepLTranslationClient deepLTranslationClient;
    private final Map<String, String> translationCache = new ConcurrentHashMap<>();

    public AlertTitleTranslationService(DeepLTranslationClient deepLTranslationClient) {
        this.deepLTranslationClient = deepLTranslationClient;
    }

    public String translateTitle(String originalTitle) {
        return translateTitle(originalTitle, List.of());
    }

    public String translateTitle(String originalTitle, List<AlertGlossaryTerm> glossaryTerms) {
        return translateOrFallback(originalTitle, glossaryTerms);
    }

    public String translateText(String originalText) {
        return translateText(originalText, List.of());
    }

    public String translateText(String originalText, List<AlertGlossaryTerm> glossaryTerms) {
        if (!StringUtils.hasText(originalText)) {
            return "";
        }
        return String.join("\n", chunks(originalText).stream()
                .map(chunk -> translateOrFallback(chunk, glossaryTerms))
                .toList());
    }

    private String translateOrFallback(String originalText, List<AlertGlossaryTerm> glossaryTerms) {
        if (!StringUtils.hasText(originalText)) {
            return "";
        }
        String cacheKey = sha256Hex(originalText + "\n" + glossaryFingerprint(glossaryTerms));
        String cached = translationCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        String translatedText = translateWithDeepL(originalText);
        String result = applyLocalismSurfaceTerms(
                StringUtils.hasText(translatedText) ? translatedText : originalText,
                glossaryTerms);
        translationCache.put(cacheKey, result);
        return result;
    }

    private String applyLocalismSurfaceTerms(String text, List<AlertGlossaryTerm> glossaryTerms) {
        if (!StringUtils.hasText(text) || glossaryTerms == null || glossaryTerms.isEmpty()) {
            return text;
        }
        String result = text;
        for (AlertGlossaryTerm term : glossaryTerms) {
            result = switch (term.normalizedTerm()) {
                case "개미" -> replaceIfMissing(result, "Ants", List.of(
                        "retail investors",
                        "retail investor",
                        "individual investors",
                        "individual investor",
                        "ant investors"));
                case "대장주" -> replaceIfMissing(result, "bellwether stock", List.of(
                        "sector leader stock",
                        "leading stock",
                        "market leader stock",
                        "leading share"));
                case "따따블" -> replaceIfMissing(result, "dda-dda-ble", List.of(
                        "IPO quadruple jump",
                        "quadruple IPO jump",
                        "fourfold IPO pop"));
                case "품절주" -> replaceIfMissing(result, "low-float stock", List.of(
                        "scarce-float stock",
                        "thin-float stock"));
                default -> result;
            };
        }
        return result;
    }

    private String replaceIfMissing(String text, String preferredSurface, List<String> translatedPhrases) {
        if (containsPhrase(text, preferredSurface)) {
            return text;
        }
        String result = text;
        for (String translatedPhrase : translatedPhrases) {
            Pattern pattern = Pattern.compile(
                    "\\b" + Pattern.quote(translatedPhrase) + "\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher matcher = pattern.matcher(result);
            result = matcher.replaceAll(Matcher.quoteReplacement(preferredSurface));
        }
        return result;
    }

    private boolean containsPhrase(String text, String phrase) {
        Pattern pattern = Pattern.compile(
                "\\b" + Pattern.quote(phrase) + "\\b",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return pattern.matcher(text).find();
    }

    private String glossaryFingerprint(List<AlertGlossaryTerm> glossaryTerms) {
        if (glossaryTerms == null || glossaryTerms.isEmpty()) {
            return "";
        }
        return String.join("|", glossaryTerms.stream()
                .map(term -> String.join(":",
                        nullToEmpty(term.sourceTerm()),
                        nullToEmpty(term.normalizedTerm()),
                        nullToEmpty(term.englishTerm()),
                        nullToEmpty(term.category())))
                .toList());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String translateWithDeepL(String originalText) {
        try {
            return deepLTranslationClient.translateKoToEn(originalText);
        } catch (RuntimeException exception) {
            LOGGER.warn("DeepL alert translation failed. Falling back to original text: {}",
                    exception.getClass().getSimpleName());
            return "";
        }
    }

    private List<String> chunks(String text) {
        String normalized = text.strip();
        if (normalized.length() <= MAX_CHUNK_CHARS) {
            return List.of(normalized);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + MAX_CHUNK_CHARS, normalized.length());
            int split = splitPoint(normalized, start, end);
            chunks.add(normalized.substring(start, split).strip());
            start = split;
            while (start < normalized.length() && Character.isWhitespace(normalized.charAt(start))) {
                start++;
            }
        }
        return chunks;
    }

    private int splitPoint(String text, int start, int end) {
        if (end >= text.length()) {
            return text.length();
        }
        for (int index = end; index > start + MAX_CHUNK_CHARS / 2; index--) {
            char current = text.charAt(index - 1);
            if (current == '.' || current == '\n' || current == '다' || current == '요') {
                return index;
            }
        }
        return end;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
