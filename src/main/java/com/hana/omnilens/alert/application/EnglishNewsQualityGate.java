package com.hana.omnilens.alert.application;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

import com.hana.omnilens.alert.domain.AlertSummaryLines;

public final class EnglishNewsQualityGate {

    private static final Pattern HANGUL_PATTERN = Pattern.compile("[가-힣]");

    private EnglishNewsQualityGate() {
    }

    public static AlertSummaryLines englishSummaryLinesOrFallback(AlertSummaryLines summaryLines, String subject) {
        AlertSummaryLines fallback = englishSummaryFallbackLines(subject);
        if (summaryLines == null) {
            return fallback;
        }
        String what = sanitizeEnglishSummaryLine(summaryLines.what());
        String why = sanitizeEnglishSummaryLine(summaryLines.why());
        String impact = sanitizeEnglishSummaryLine(summaryLines.impact());
        return new AlertSummaryLines(
                StringUtils.hasText(what) ? what : fallback.what(),
                StringUtils.hasText(why) && !why.equals(what) ? why : fallback.why(),
                StringUtils.hasText(impact) && !impact.equals(what) && !impact.equals(why)
                        ? impact
                        : fallback.impact());
    }

    public static boolean hasUsableEnglishSummaryLines(AlertSummaryLines summaryLines) {
        if (summaryLines == null) {
            return false;
        }
        return StringUtils.hasText(sanitizeEnglishSummaryLine(summaryLines.what()))
                && StringUtils.hasText(sanitizeEnglishSummaryLine(summaryLines.why()))
                && StringUtils.hasText(sanitizeEnglishSummaryLine(summaryLines.impact()));
    }

    public static String englishTextOrFallback(String value, String fallback) {
        String normalized = normalizeWhitespace(value);
        if (StringUtils.hasText(normalized) && !containsHangul(normalized) && !containsEllipsis(normalized)) {
            return normalized;
        }
        return fallback == null ? "" : fallback;
    }

    public static String englishSummaryTextOrFallback(String value, String fallback) {
        String normalized = normalizeWhitespace(value);
        if (StringUtils.hasText(normalized)
                && !containsHangul(normalized)
                && !containsEllipsis(normalized)
                && !containsSummaryMeta(normalized)) {
            return normalized;
        }
        return fallback == null ? "" : fallback;
    }

    public static String englishContentFallback(
            String originalContent,
            String translatedTitle,
            AlertSummaryLines summaryLines) {
        if (!StringUtils.hasText(originalContent)) {
            return "";
        }
        return String.join("\n\n",
                englishSubject(translatedTitle),
                "What: " + summaryLines.what(),
                "Why: " + summaryLines.why(),
                "Impact: " + summaryLines.impact());
    }

    public static AlertSummaryLines englishSummaryFallbackLines(String subject) {
        String displaySubject = englishSubject(subject);
        return new AlertSummaryLines(
                "This item covers " + displaySubject + " from Korean market news.",
                "The key background is the latest market or company context confirmed in the source article.",
                "Investors should review possible effects on prices, earnings, liquidity, and watched holdings.");
    }

    public static String englishSubject(String subject) {
        String normalized = normalizeWhitespace(subject);
        if (!StringUtils.hasText(normalized) || containsHangul(normalized) || containsEllipsis(normalized)) {
            return "a Korean market update";
        }
        return normalized.length() > 160 ? "a Korean market update" : normalized;
    }

    public static boolean containsHangul(String value) {
        return value != null && HANGUL_PATTERN.matcher(value).find();
    }

    private static String sanitizeEnglishSummaryLine(String value) {
        String normalized = normalizeWhitespace(value);
        if (!StringUtils.hasText(normalized)
                || containsHangul(normalized)
                || containsEllipsis(normalized)
                || containsSummaryMeta(normalized)
                || !endsAsEnglishSentence(normalized)) {
            return "";
        }
        return normalized;
    }

    private static String normalizeWhitespace(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private static boolean containsEllipsis(String value) {
        return value.contains("...") || value.contains("…");
    }

    private static boolean containsSummaryMeta(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("classified")
                || lower.contains("importance")
                || lower.contains("sentiment")
                || value.contains("중요도")
                || value.contains("감성")
                || value.contains("분류");
    }

    private static boolean endsAsEnglishSentence(String value) {
        return value.matches(".*[.!?][\"')\\]]*$");
    }
}
