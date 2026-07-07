package com.hana.omnilens.alert.application;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

import com.hana.omnilens.alert.domain.AlertSummaryLines;

public final class EnglishNewsQualityGate {

    private static final Pattern HANGUL_PATTERN = Pattern.compile("[가-힣]");
    private static final Set<String> ALLOWED_HYPHENATED_TERMS = Set.of(
            "ai-server",
            "article-backed",
            "balance-sheet",
            "buy-side",
            "data-center",
            "debt-funded",
            "double-edged",
            "edge-computing",
            "foreign-investor",
            "high-bandwidth",
            "high-value",
            "high-value-added",
            "humanoid-robot",
            "interest-rate",
            "kospi-listed",
            "low-float",
            "large-cap",
            "market-wide",
            "middle-class",
            "mom-and-pop",
            "multi-trillion-dollar",
            "operating-profit",
            "one-to-ten",
            "policy-rate",
            "price-to-book",
            "price-to-earnings",
            "sell-side",
            "semiconductor-led",
            "stock-market",
            "supply-chain",
            "treasury-share",
            "value-up",
            "year-on-year");

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
                && !containsGenericFallback(normalized)
                && !containsLowQualityTranslation(normalized);
    }

    public static boolean looksLikeStructuredSummaryContent(String value) {
        String normalized = normalizeWhitespace(value);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return Pattern.compile("(^|\\s)what\\s*:", Pattern.CASE_INSENSITIVE).matcher(normalized).find()
                && Pattern.compile("(^|\\s)why\\s*:", Pattern.CASE_INSENSITIVE).matcher(normalized).find()
                && Pattern.compile("(^|\\s)impact\\s*:", Pattern.CASE_INSENSITIVE).matcher(normalized).find()
                || lower.startsWith("what happened:")
                        && lower.contains("why it matters:")
                        && lower.contains("investor impact:");
    }

    public static boolean looksLikeSummaryOnlyContent(
            String content,
            AlertSummaryLines summaryLines,
            String translatedSummary,
            String originalContent) {
        String normalizedContent = normalizeForComparison(content);
        if (!StringUtils.hasText(normalizedContent)) {
            return false;
        }
        if (looksLikeStructuredSummaryContent(content)) {
            return true;
        }
        if (equalsNormalized(normalizedContent, translatedSummary)) {
            return true;
        }
        if (summaryLines != null) {
            String rawSummaryLines = String.join(" ",
                    nullToEmpty(summaryLines.what()),
                    nullToEmpty(summaryLines.why()),
                    nullToEmpty(summaryLines.impact()));
            String labeledSummaryLines = String.join(" ",
                    label("What", summaryLines.what()),
                    label("Why", summaryLines.why()),
                    label("Impact", summaryLines.impact()));
            if (equalsNormalized(normalizedContent, rawSummaryLines)
                    || equalsNormalized(normalizedContent, labeledSummaryLines)) {
                return true;
            }
        }
        String normalizedOriginal = normalizeWhitespace(originalContent);
        if (normalizedOriginal.length() < 500) {
            return false;
        }
        int contentLength = normalizedContent.length();
        int minimumExpectedLength = Math.max(240, (int) (normalizedOriginal.length() * 0.22));
        if (contentLength >= minimumExpectedLength) {
            return false;
        }
        String lower = normalizedContent.toLowerCase(Locale.ROOT);
        return englishSentenceCount(normalizedContent) <= 3
                && (lower.contains("investors should")
                        || lower.contains("the article cites")
                        || lower.contains("the disclosure cites")
                        || lower.contains("the company said")
                        || lower.contains("market reaction"));
    }

    public static String englishSummaryTextOrEmpty(String value) {
        String normalized = normalizeWhitespace(value);
        if (StringUtils.hasText(normalized)
                && !containsHangul(normalized)
                && !containsEllipsis(normalized)
                && !containsSummaryMeta(normalized)
                && !containsGenericFallback(normalized)
                && !containsLowQualityTranslation(normalized)) {
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
                || lower.contains("investors should review possible effects on prices, earnings, liquidity, and watched holdings")
                || lower.contains("drew attention in the article around the reported event")
                || lower.contains("the article links the move to the article-backed market context")
                || lower.contains("drew attention in the article")
                || lower.contains("the story links the shift to supply")
                || lower.contains("the story links the shift to the article")
                || lower.contains("investors should follow the next disclosure and watch the market reaction")
                || lower.contains("investors should track the next disclosure and market reaction as the story develops")
                || lower.contains("the key background is confirmed in the latest news")
                || lower.contains("the key background is confirmed in the latest disclosure")
                || lower.contains("confirmed in the latest news")
                || lower.contains("confirmed in the latest disclosure");
    }

    public static boolean containsLowQualityTranslation(String value) {
        String lower = normalizeWhitespace(value).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(lower)) {
            return false;
        }
        if (lower.contains("kang nam-go")
                || lower.contains("pab-wo")
                || lower.contains("dda-jeon")
                || lower.contains("levership")
                || lower.contains("hannak")
                || lower.contains("defi-shares")
                || lower.contains("nanyang dynamics")
                || lower.contains("snicklever")
                || lower.contains("stock-celltrion")
                || lower.contains("hanacorp")
                || lower.contains("sina-combankipt")
                || lower.contains("lg-hydration")
                || lower.contains("iong-wok")
                || lower.contains("nalmalai")
                || lower.contains("without a street")
                || lower.contains("according to a search")
                || lower.contains("three-sentence")
                || lower.contains("effect of the number")
                || lower.contains("dynamic of the")
                || lower.contains("nmsk")
                || lower.contains("auction raise")
                || lower.contains("auction distributor")
                || lower.contains("exchange order")
                || lower.contains("cosby market")
                || lower.contains("capacitor semiconductor")
                || lower.contains("chinese p&t7")
                || lower.contains("lithium supply")
                || lower.contains("dividend price of equity dividends")
                || lower.contains("envidia")
                || lower.contains("enviada")
                || lower.contains("enbody")
                || lower.contains("ofewa")
                || lower.contains("robotaxial")
                || lower.contains("terminal center")
                || lower.contains("it centriel")
                || lower.contains("robotic sum")
                || lower.contains("actuator's salary")
                || lower.contains("incentive traveler")
                || lower.contains("north and south")
                || lower.contains("hanoteoreminder")
                || lower.contains("hyang-yeol")
                || lower.contains("yuseo")
                || lower.contains("hidden world history")
                || lower.contains("korean farmer's 600-year")
                || lower.contains("fresh water break")
                || lower.contains("i'm going to")
                || lower.contains("power-driven")
                || lower.contains("two-carpet")
                || lower.contains("new bond's price flow")
                || lower.contains("flowing semiconductor ship")
                || lower.contains("on strike; the actuality")
                || lower.contains("entering the 'sides'")
                || lower.contains("triangle lower limited")
                || lower.contains("us-exited ai-investor")
                || lower.contains("samjeon nix's trading method does not exist")
                || lower.contains("samjeon nok")
                || lower.contains("future-sustainable capital")
                || lower.contains("adding silicon")
                || lower.contains("european shopping trip")
                || lower.contains("samnick")
                || lower.contains("middle and small businesses fund acts")
                || lower.contains("investors net at the european show")
                || lower.contains("no ai or human")
                || lower.contains("reveal ourselves")
                || lower.contains("countermeasures inspection")
                || lower.contains("approval of the megaproject")
                || lower.contains("core themes of ai and human death")
                || lower.contains("latest market and company interventions")
                || lower.contains("market and business events confirmed")
                || lower.contains("trading by samjeon nix")
                || lower.contains("by samjeon nix as key")
                || lower.contains("latest public news confirmed in the original")
                || lower.contains("impact of this president")
                || lower.contains("holding and surveillance")
                || lower.contains("samjeon nix trading")
                || lower.contains("latest market and corporate events confirmed")
                || lower.contains("krw-3777b")
                || lower.contains("sheriff's rifle")
                || lower.contains("iseutasi")
                || lower.contains("investor's net buying flow")
                || lower.contains("entrepreneurhan")
                || lower.contains("hallinkyos")
                || lower.contains("sk hallinkyos")
                || lower.contains("skhinky")
                || lower.contains("sinerlwyk")
                || lower.contains("hyanix")
                || lower.contains("skhynx")
                || lower.contains("klamath stock exchange")
                || lower.contains("north american and south american trade disputes")
                || lower.contains("substitute offering")
                || lower.contains("high-slang")
                || lower.contains("teatr esg")
                || lower.contains("tutat esg")
                || lower.contains("hyundai motor, kia, and mercedes-benz")
                || lower.contains("car insurance and vehicle services")
                || lower.contains("freaked out about the deposits")
                || lower.contains("triple-a hynix")
                || lower.contains("truck-train")
                || lower.contains("kospi faced the kospi market move")
                || lower.contains("investor impact is higher on the flow of earnings")
                || lower.contains("investor impact is higher on ev and hev markets")
                || lower.contains("foreign exchanges as the market becomes more active")
                || lower.contains("national association of churches")
                || lower.contains("18 temples")
                || lower.contains("90 trillion yuan")
                || lower.contains("receivable volume")
                || lower.contains("reception function")
                || lower.contains("periodic allowance")
                || lower.contains("move-digest")
                || lower.contains("supply-digest")
                || lower.contains("gyeongneng district")
                || lower.contains("social-hq")
                || lower.contains("life-close welfare")
                || lower.contains("youth center of the 3rd army")
                || lower.contains("republic of china")) {
            return true;
        }
        return Pattern.compile("\\b[a-z][a-z]+(?:-[a-z][a-z]+){2,}(?:'s)?\\b")
                .matcher(lower)
                .results()
                .map(match -> match.group().replace("'s", ""))
                .anyMatch(term -> !ALLOWED_HYPHENATED_TERMS.contains(term));
    }

    private static String sanitizeEnglishSummaryLine(String value) {
        String normalized = normalizeWhitespace(value);
        if (!StringUtils.hasText(normalized)
                || containsHangul(normalized)
                || containsEllipsis(normalized)
                || containsSummaryMeta(normalized)
                || containsGenericFallback(normalized)
                || containsLowQualityTranslation(normalized)
                || startsWithStockCodeSubject(normalized)
                || !endsAsEnglishSentence(normalized)) {
            return "";
        }
        return normalized;
    }

    private static boolean startsWithStockCodeSubject(String value) {
        return Pattern.compile("^\\d{6}\\b").matcher(value).find();
    }

    private static boolean equalsNormalized(String normalizedValue, String other) {
        String normalizedOther = normalizeForComparison(other);
        return StringUtils.hasText(normalizedOther) && normalizedValue.equals(normalizedOther);
    }

    private static String normalizeForComparison(String value) {
        return normalizeWhitespace(value)
                .replaceAll("(?i)\\b(what|why|impact|what happened|why it matters|investor impact)\\s*:", "")
                .replaceAll("[\\p{Punct}&&[^%]]+", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String label(String label, String value) {
        return StringUtils.hasText(value) ? label + ": " + value : "";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int englishSentenceCount(String text) {
        Matcher matcher = Pattern.compile("[^.!?]+[.!?]").matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
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
