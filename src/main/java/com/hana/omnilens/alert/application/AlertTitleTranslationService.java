package com.hana.omnilens.alert.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
        String cacheKey = sha256Hex(originalText);
        String cached = translationCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        String translatedText = translateWithDeepL(originalText);
        String result = StringUtils.hasText(translatedText) ? translatedText : originalText;
        translationCache.put(cacheKey, result);
        return result;
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
