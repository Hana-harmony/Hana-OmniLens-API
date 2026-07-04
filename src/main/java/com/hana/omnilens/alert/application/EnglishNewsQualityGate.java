package com.hana.omnilens.alert.application;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

import com.hana.omnilens.alert.domain.AlertSummaryLines;

public final class EnglishNewsQualityGate {

    private static final Pattern HANGUL_PATTERN = Pattern.compile("[가-힣]");

    private EnglishNewsQualityGate() {
    }

    public static AlertSummaryLines englishSummaryLinesOrEmpty(AlertSummaryLines summaryLines) {
        if (summaryLines == null) {
            return new AlertSummaryLines("", "", "");
        }
        String what = englishSummaryLineOrEmpty(summaryLines.what());
        String why = englishSummaryLineOrEmpty(summaryLines.why());
        String impact = englishSummaryLineOrEmpty(summaryLines.impact());
        return new AlertSummaryLines(
                what,
                StringUtils.hasText(why) && !why.equals(what) ? why : "",
                StringUtils.hasText(impact) && !impact.equals(what) && !impact.equals(why)
                        ? impact
                        : "");
    }

    public static boolean hasUsableEnglishSummaryLines(AlertSummaryLines summaryLines) {
        if (summaryLines == null) {
            return false;
        }
        AlertSummaryLines sanitized = englishSummaryLinesOrEmpty(summaryLines);
        return StringUtils.hasText(sanitized.what())
                && StringUtils.hasText(sanitized.why())
                && StringUtils.hasText(sanitized.impact());
    }

    public static String englishTextOrEmpty(String value) {
        String normalized = normalizeWhitespace(value);
        if (hasUsableEnglishText(normalized)) {
            return normalized;
        }
        return "";
    }

    public static boolean hasUsableEnglishText(String value) {
        String normalized = normalizeWhitespace(value);
        return StringUtils.hasText(normalized)
                && !containsHangul(normalized)
                && !containsGenericFallback(normalized);
    }

    public static String englishSummaryTextOrEmpty(String value) {
        String normalized = normalizeWhitespace(value);
        if (StringUtils.hasText(normalized)
                && !containsHangul(normalized)
                && !containsEllipsis(normalized)
                && !containsSummaryMeta(normalized)
                && !containsGenericFallback(normalized)) {
            return normalized;
        }
        return "";
    }

    public static String englishSummaryLineOrEmpty(String value) {
        return sanitizeEnglishSummaryLine(value);
    }

    public static boolean containsHangul(String value) {
        return value != null && HANGUL_PATTERN.matcher(value).find();
    }

    public static boolean containsGenericFallback(String value) {
        String lower = normalizeWhitespace(value).toLowerCase(Locale.ROOT);
        return lower.contains("korean company update")
                || lower.contains("korean market update")
                || lower.contains("a korean market update")
                || lower.contains("this item covers") && lower.contains("from korean market news")
                || lower.contains("latest market or company context confirmed in the source article")
                || lower.contains("investors should review possible effects on prices, earnings, liquidity, and watched holdings");
    }

    private static String sanitizeEnglishSummaryLine(String value) {
        String normalized = normalizeWhitespace(value);
        if (!StringUtils.hasText(normalized)
                || containsHangul(normalized)
                || containsEllipsis(normalized)
                || containsSummaryMeta(normalized)
                || containsGenericFallback(normalized)
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
                || lower.contains("i'm sorry")
                || lower.contains("i can’t assist")
                || lower.contains("i can't assist")
                || lower.contains("please provide")
                || lower.contains("as an ai")
                || lower.contains("publisher of this newspaper")
                || lower.contains("columnist")
                || value.contains("중요도")
                || value.contains("감성")
                || value.contains("분류");
    }

    private static boolean endsAsEnglishSentence(String value) {
        return value.matches(".*[.!?][\"')\\]]*$");
    }
}
