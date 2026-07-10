package com.hana.omnilens.alert.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
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
import com.hana.omnilens.provider.ai.HannahAiGlossaryTerm;
import com.hana.omnilens.provider.ai.HannahAiKoreanTranslationClient;
import com.hana.omnilens.provider.ai.HannahAiKoreanTranslationRequest;
import com.hana.omnilens.provider.ai.HannahAiKoreanTranslationResponse;

@Service
public class AlertTitleTranslationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertTitleTranslationService.class);
    private static final int MAX_CHUNK_CHARS = 1_500;
    private static final Pattern HANGUL_PATTERN = Pattern.compile("[가-힣]");
    private static final Pattern TRAILING_ELLIPSIS_PATTERN = Pattern.compile("(?:\\.\\.\\.|…)[\\s\"')\\]]*$");
    public static final String STATUS_TRANSLATED = "TRANSLATED";
    public static final String STATUS_PARTIAL_SOURCE_LANGUAGE_FALLBACK = "PARTIAL_SOURCE_LANGUAGE_FALLBACK";
    public static final String STATUS_SOURCE_LANGUAGE_FALLBACK = "SOURCE_LANGUAGE_FALLBACK";
    public static final String PROVIDER_LOCAL_OPEN_SOURCE_QWEN = "local-open-source-qwen3-translation";
    public static final String PROVIDER_ALREADY_ENGLISH = "already-english";
    private static final String PROVIDER_SOURCE_LANGUAGE_FALLBACK = "source-language-fallback";
    public static final String MODEL_HANNAH_TRANSLATION_UNAVAILABLE = "hannah-ai-translation-unavailable";

    private final HannahAiKoreanTranslationClient hannahTranslationClient;
    private final Map<String, TranslationResult> translationCache = new ConcurrentHashMap<>();

    public AlertTitleTranslationService(HannahAiKoreanTranslationClient hannahTranslationClient) {
        this.hannahTranslationClient = hannahTranslationClient;
    }

    public String translateTitle(String originalTitle) {
        return translateTitle(originalTitle, List.of());
    }

    public String translateTitle(String originalTitle, List<AlertGlossaryTerm> glossaryTerms) {
        return translateTitleWithResult(originalTitle, glossaryTerms).translatedText();
    }

    public TranslationResult translateTitleWithResult(String originalTitle, List<AlertGlossaryTerm> glossaryTerms) {
        return translateTitleWithResult(originalTitle, glossaryTerms, "NEWS");
    }

    public TranslationResult translateTitleWithResult(
            String originalTitle,
            List<AlertGlossaryTerm> glossaryTerms,
            String sourceType) {
        return translateOrFallback(originalTitle, glossaryTerms, true, sourceType);
    }

    public String translateText(String originalText) {
        return translateText(originalText, List.of());
    }

    public String translateText(String originalText, List<AlertGlossaryTerm> glossaryTerms) {
        if (!StringUtils.hasText(originalText)) {
            return "";
        }
        return translateTextWithResult(originalText, glossaryTerms).translatedText();
    }

    public TranslationResult translateTextWithResult(String originalText, List<AlertGlossaryTerm> glossaryTerms) {
        return translateTextWithResult(originalText, glossaryTerms, "NEWS");
    }

    public TranslationResult translateTextWithResult(
            String originalText,
            List<AlertGlossaryTerm> glossaryTerms,
            String sourceType) {
        if (!StringUtils.hasText(originalText)) {
            return TranslationResult.sourceFallback("", MODEL_HANNAH_TRANSLATION_UNAVAILABLE);
        }
        List<TranslationResult> results = chunks(originalText).stream()
                .map(chunk -> translateOrFallback(chunk, glossaryTerms, false, sourceType))
                .toList();
        String translatedText = String.join("\n", results.stream()
                .map(TranslationResult::translatedText)
                .toList());
        return new TranslationResult(
                translatedText,
                aggregateProvider(results),
                aggregateModelVersion(results),
                aggregateStatus(results),
                aggregateQualityFlags(results));
    }

    private TranslationResult translateOrFallback(
            String originalText,
            List<AlertGlossaryTerm> glossaryTerms,
            boolean headlineMode,
            String sourceType) {
        if (!StringUtils.hasText(originalText)) {
            return TranslationResult.sourceFallback("", MODEL_HANNAH_TRANSLATION_UNAVAILABLE, List.of("EMPTY_SOURCE"));
        }
        String alreadyEnglishText = EnglishNewsQualityGate.englishTextOrEmpty(originalText);
        if (StringUtils.hasText(alreadyEnglishText)) {
            return new TranslationResult(
                    alreadyEnglishText,
                    PROVIDER_ALREADY_ENGLISH,
                    MODEL_HANNAH_TRANSLATION_UNAVAILABLE,
                    STATUS_TRANSLATED);
        }
        String normalizedSourceType = normalizedSourceType(sourceType);
        String cacheKey = sha256Hex(
                normalizedSourceType + "\n" + originalText + "\n" + glossaryFingerprint(glossaryTerms));
        TranslationResult cached = translationCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        HannahAiKoreanTranslationResponse response = translateWithHannah(
                originalText,
                glossaryTerms,
                normalizedSourceType);
        String translatedText = response == null ? "" : nullToEmpty(response.translatedText());
        String modelVersion = response == null
                ? MODEL_HANNAH_TRANSLATION_UNAVAILABLE
                : firstText(response.modelVersion(), MODEL_HANNAH_TRANSLATION_UNAVAILABLE);
        String status = normalizeStatus(response == null ? "" : response.status());
        List<String> rejectionFlags = translationRejectionFlags(
                originalText,
                translatedText,
                status,
                headlineMode);
        TranslationResult result = rejectionFlags.isEmpty()
                ? new TranslationResult(
                        applyLocalismSurfaceTerms(translatedText, glossaryTerms),
                        acceptedProvider(response.provider()),
                        modelVersion,
                        acceptedStatus(status),
                        response.qualityFlags())
                : TranslationResult.sourceFallback(
                        "",
                        modelVersion,
                        mergeQualityFlags(response == null ? List.of() : response.qualityFlags(), rejectionFlags));
        if (!rejectionFlags.isEmpty()) {
            LOGGER.warn(
                    "Rejected Korean translation: sourceType={} headlineMode={} sourceLength={} translatedLength={} flags={} translatedSample={}",
                    normalizedSourceType,
                    headlineMode,
                    originalText.length(),
                    translatedText.length(),
                    rejectionFlags,
                    logSample(translatedText));
        }
        if (STATUS_TRANSLATED.equals(result.status()) && shouldCacheTranslation(originalText, headlineMode)) {
            translationCache.put(cacheKey, result);
        }
        return result;
    }

    private boolean shouldCacheTranslation(String originalText, boolean headlineMode) {
        return headlineMode || originalText.length() < 700;
    }

    private String acceptedProvider(String provider) {
        if (!StringUtils.hasText(provider) || PROVIDER_SOURCE_LANGUAGE_FALLBACK.equals(provider)) {
            return PROVIDER_LOCAL_OPEN_SOURCE_QWEN;
        }
        return provider;
    }

    private String acceptedStatus(String status) {
        return status;
    }

    private boolean hasTranslatedStatus(String status) {
        return STATUS_TRANSLATED.equals(status);
    }

    private String applyLocalismSurfaceTerms(String text, List<AlertGlossaryTerm> glossaryTerms) {
        if (!StringUtils.hasText(text) || glossaryTerms == null || glossaryTerms.isEmpty()) {
            return text;
        }
        String result = text;
        for (AlertGlossaryTerm term : glossaryTerms) {
            result = switch (term.normalizedTerm()) {
                case "개미" -> replaceIfMissing(result, "retail investors", List.of(
                        "Ants",
                        "Ant",
                        "Gaemi",
                        "Gaemee",
                        "ant investors",
                        "ant investor"));
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
                case "삼전닉스" -> replaceIfMissing(result, "Samjeon Nix", List.of(
                        "Samsung Electronics and SK Hynix",
                        "Samsung Electronics-SK Hynix",
                        "Samsung Electronics/SK Hynix",
                        "Samsung Electronics and SK hynix",
                        "Samsung Electronics-SK hynix",
                        "Samsung Electronics/SK hynix",
                        "Samnick",
                        "Samnik",
                        "Samnic",
                        "Sam Nix"));
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
            String currentResult = result;
            Matcher matcher = pattern.matcher(currentResult);
            result = matcher.replaceAll(match -> Matcher.quoteReplacement(
                    casedReplacement(preferredSurface, currentResult, match.start())));
        }
        return result;
    }

    private String casedReplacement(String preferredSurface, String text, int matchStart) {
        if (preferredSurface.isEmpty() || matchStart < 0) {
            return preferredSurface;
        }
        int index = matchStart - 1;
        while (index >= 0 && Character.isWhitespace(text.charAt(index))) {
            index--;
        }
        boolean sentenceStart = index < 0 || ".!?\"'“”(".indexOf(text.charAt(index)) >= 0;
        if (!sentenceStart || !Character.isLowerCase(preferredSurface.charAt(0))) {
            return preferredSurface;
        }
        return Character.toUpperCase(preferredSurface.charAt(0)) + preferredSurface.substring(1);
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

    private HannahAiKoreanTranslationResponse translateWithHannah(
            String originalText,
            List<AlertGlossaryTerm> glossaryTerms,
            String sourceType) {
        try {
            return hannahTranslationClient.translate(new HannahAiKoreanTranslationRequest(
                    originalText,
                    "ko",
                    "en",
                    normalizedSourceType(sourceType),
                    "",
                    toHannahGlossaryTerms(glossaryTerms)));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Hannah AI alert translation failed. Marking translation as unavailable: type={}, message={}",
                    exception.getClass().getSimpleName(),
                    exception.getMessage());
            return null;
        }
    }

    private String normalizedSourceType(String sourceType) {
        return "DISCLOSURE".equalsIgnoreCase(sourceType) ? "DISCLOSURE" : "NEWS";
    }

    private List<HannahAiGlossaryTerm> toHannahGlossaryTerms(List<AlertGlossaryTerm> glossaryTerms) {
        if (glossaryTerms == null || glossaryTerms.isEmpty()) {
            return List.of();
        }
        return glossaryTerms.stream()
                .map(term -> new HannahAiGlossaryTerm(
                        term.sourceTerm(),
                        term.normalizedTerm(),
                        term.englishTerm(),
                        term.category(),
                        term.description()))
                .toList();
    }

    private List<String> translationRejectionFlags(
            String originalText,
            String translatedText,
            String status,
            boolean headlineMode) {
        List<String> flags = new ArrayList<>();
        if (!hasTranslatedStatus(status)) {
            flags.add("NON_TRANSLATED_STATUS:" + status);
            return flags;
        }
        if (!StringUtils.hasText(translatedText)) {
            flags.add("EMPTY_TRANSLATION");
            return flags;
        }
        if (originalText.strip().equals(translatedText.strip())) {
            flags.add("SOURCE_LANGUAGE_FALLBACK");
        }
        if (HANGUL_PATTERN.matcher(translatedText).find()) {
            flags.add("HANGUL_REMAINS");
        }
        if (TRAILING_ELLIPSIS_PATTERN.matcher(translatedText).find()) {
            flags.add("TRAILING_ELLIPSIS_TRANSLATION");
        }
        if (EnglishNewsQualityGate.containsGenericFallback(translatedText)) {
            flags.add("GENERIC_FALLBACK_TRANSLATION");
        }
        List<String> lowQualityReasons = EnglishNewsQualityGate.lowQualityTranslationReasons(translatedText);
        if (!lowQualityReasons.isEmpty()) {
            flags.addAll(lowQualityReasons);
        }
        boolean usable = headlineMode
                ? EnglishNewsQualityGate.hasUsableEnglishHeadlineText(translatedText)
                : EnglishNewsQualityGate.hasUsableEnglishText(translatedText);
        if (!usable && flags.isEmpty()) {
            flags.add(headlineMode ? "UNUSABLE_HEADLINE_TRANSLATION" : "UNUSABLE_ENGLISH_TRANSLATION");
        }
        return List.copyOf(flags);
    }

    private List<String> mergeQualityFlags(List<String> upstreamFlags, List<String> localFlags) {
        LinkedHashSet<String> flags = new LinkedHashSet<>();
        if (upstreamFlags != null) {
            flags.addAll(upstreamFlags);
        }
        flags.addAll(localFlags);
        return List.copyOf(flags);
    }

    private String logSample(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").strip();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240);
    }

    private String aggregateProvider(List<TranslationResult> results) {
        return results.stream()
                .map(TranslationResult::provider)
                .filter(StringUtils::hasText)
                .filter(provider -> !PROVIDER_SOURCE_LANGUAGE_FALLBACK.equals(provider))
                .findFirst()
                .orElse(PROVIDER_SOURCE_LANGUAGE_FALLBACK);
    }

    private String aggregateModelVersion(List<TranslationResult> results) {
        return results.stream()
                .map(TranslationResult::modelVersion)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(MODEL_HANNAH_TRANSLATION_UNAVAILABLE);
    }

    private String aggregateStatus(List<TranslationResult> results) {
        boolean hasTranslated = results.stream()
                .anyMatch(result -> STATUS_TRANSLATED.equals(result.status())
                        || STATUS_PARTIAL_SOURCE_LANGUAGE_FALLBACK.equals(result.status()));
        boolean hasFallback = results.stream()
                .anyMatch(result -> STATUS_SOURCE_LANGUAGE_FALLBACK.equals(result.status())
                        || STATUS_PARTIAL_SOURCE_LANGUAGE_FALLBACK.equals(result.status()));
        if (hasTranslated && hasFallback) {
            return STATUS_PARTIAL_SOURCE_LANGUAGE_FALLBACK;
        }
        return hasTranslated ? STATUS_TRANSLATED : STATUS_SOURCE_LANGUAGE_FALLBACK;
    }

    private List<String> aggregateQualityFlags(List<TranslationResult> results) {
        LinkedHashSet<String> flags = new LinkedHashSet<>();
        for (TranslationResult result : results) {
            flags.addAll(result.qualityFlags());
        }
        return List.copyOf(flags);
    }

    private String normalizeStatus(String status) {
        if (STATUS_TRANSLATED.equals(status)
                || STATUS_PARTIAL_SOURCE_LANGUAGE_FALLBACK.equals(status)
                || STATUS_SOURCE_LANGUAGE_FALLBACK.equals(status)) {
            return status;
        }
        return STATUS_SOURCE_LANGUAGE_FALLBACK;
    }

    private String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
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
            if (current == '.' || current == '\n' || isKoreanSentenceBoundary(text, index)) {
                return index;
            }
        }
        return end;
    }

    private boolean isKoreanSentenceBoundary(String text, int index) {
        char current = text.charAt(index - 1);
        if (current != '다' && current != '요') {
            return false;
        }
        if (index >= text.length()) {
            return true;
        }
        char next = text.charAt(index);
        return Character.isWhitespace(next) || next == '"' || next == '\'' || next == '”' || next == ')';
    }

    private String normalizeWhitespace(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record TranslationResult(
            String translatedText,
            String provider,
            String modelVersion,
            String status,
            List<String> qualityFlags
    ) {

        public TranslationResult(
                String translatedText,
                String provider,
                String modelVersion,
                String status) {
            this(translatedText, provider, modelVersion, status, List.of());
        }

        public TranslationResult {
            qualityFlags = qualityFlags == null ? List.of() : List.copyOf(qualityFlags);
        }

        private static TranslationResult sourceFallback(String text, String modelVersion) {
            return sourceFallback(text, modelVersion, List.of());
        }

        private static TranslationResult sourceFallback(
                String text,
                String modelVersion,
                List<String> qualityFlags) {
            return new TranslationResult(
                    text,
                    PROVIDER_SOURCE_LANGUAGE_FALLBACK,
                    modelVersion,
                    STATUS_SOURCE_LANGUAGE_FALLBACK,
                    qualityFlags);
        }
    }
}
