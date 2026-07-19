package com.hana.omniconnect.alert.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

import com.hana.omniconnect.alert.domain.AlertSummaryLines;

public final class EnglishNewsQualityGate {

    private static final Pattern HANGUL_PATTERN = Pattern.compile("[가-힣]");
    private static final Pattern CJK_PATTERN = Pattern.compile("[\\u3400-\\u4DBF\\u4E00-\\u9FFF]");
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
    private static final List<String> LOW_QUALITY_PHRASES = List.of(
            "kang nam-go",
            "pab-wo",
            "dda-jeon",
            "levership",
            "hannak",
            "defi-shares",
            "nanyang dynamics",
            "snicklever",
            "stock-celltrion",
            "hanacorp",
            "sina-combankipt",
            "lg-hydration",
            "iong-wok",
            "nalmalai",
            "without a street",
            "according to a search",
            "three-sentence",
            "effect of the number",
            "dynamic of the",
            "nmsk",
            "auction raise",
            "auction distributor",
            "exchange order",
            "cosby market",
            "capacitor semiconductor",
            "chinese p&t7",
            "lithium supply",
            "dividend price of equity dividends",
            "envidia",
            "enviada",
            "enbody",
            "ofewa",
            "robotaxial",
            "terminal center",
            "it centriel",
            "robotic sum",
            "actuator's salary",
            "incentive traveler",
            "north and south",
            "hanoteoreminder",
            "hyang-yeol",
            "yuseo",
            "hidden world history",
            "korean farmer's 600-year",
            "fresh water break",
            "i'm going to",
            "power-driven",
            "two-carpet",
            "new bond's price flow",
            "flowing semiconductor ship",
            "on strike; the actuality",
            "entering the 'sides'",
            "triangle lower limited",
            "us-exited ai-investor",
            "samjeon nix's trading method does not exist",
            "samjeon nok",
            "future-sustainable capital",
            "adding silicon",
            "european shopping trip",
            "samnick",
            "middle and small businesses fund acts",
            "investors net at the european show",
            "no ai or human",
            "reveal ourselves",
            "countermeasures inspection",
            "approval of the megaproject",
            "core themes of ai and human death",
            "latest market and company interventions",
            "market and business events confirmed",
            "trading by samjeon nix",
            "by samjeon nix as key",
            "latest public news confirmed in the original",
            "impact of this president",
            "holding and surveillance",
            "samjeon nix trading",
            "latest market and corporate events confirmed",
            "krw-3777b",
            "sheriff's rifle",
            "iseutasi",
            "investor's net buying flow",
            "entrepreneurhan",
            "hallinkyos",
            "sk hallinkyos",
            "skhinky",
            "sinerlwyk",
            "hyanix",
            "skhynx",
            "klamath stock exchange",
            "north american and south american trade disputes",
            "substitute offering",
            "high-slang",
            "teatr esg",
            "tutat esg",
            "hyundai motor, kia, and mercedes-benz",
            "car insurance and vehicle services",
            "freaked out about the deposits",
            "triple-a hynix",
            "truck-train",
            "kospi faced the kospi market move",
            "investor impact is higher on the flow of earnings",
            "investor impact is higher on ev and hev markets",
            "foreign exchanges as the market becomes more active",
            "national association of churches",
            "18 temples",
            "90 trillion yuan",
            "receivable volume",
            "reception function",
            "periodic allowance",
            "the headline also references",
            "move-digest",
            "supply-digest",
            "gyeongneng district",
            "social-hq",
            "life-close welfare",
            "youth center of the 3rd army",
            "republic of china");

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
        String normalized = stripGeneratedHeadlineReferenceSuffix(value);
        if (hasUsableEnglishText(normalized)) {
            return normalized;
        }
        return "";
    }

    public static boolean hasUsableEnglishText(String value) {
        String normalized = normalizeWhitespace(value);
        return StringUtils.hasText(normalized)
                && !containsHangul(normalized)
                && !containsEllipsis(normalized)
                && !containsGenericFallback(normalized)
                && !containsLowQualityTranslation(normalized);
    }

    public static boolean hasUsableEnglishHeadlineText(String value) {
        String normalized = normalizeWhitespace(value);
        return hasUsableEnglishText(normalized)
                && !looksLikeOverlyTerseHeadlineFragment(normalized);
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
        String normalized = stripGeneratedHeadlineReferenceSuffix(value);
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
                || lower.equals("korean stock market")
                || lower.equals("[korean stock market ]")
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
                || lower.contains("the original korean text is retained because machine translation was unavailable")
                || lower.contains("review the linked article or filing for price, liquidity, and portfolio impact")
                || lower.contains("the key background is confirmed in the latest news")
                || lower.contains("the key background is confirmed in the latest disclosure")
                || lower.contains("confirmed in the latest news")
                || lower.contains("confirmed in the latest disclosure");
    }

    public static boolean containsLowQualityTranslation(String value) {
        return !lowQualityTranslationReasons(value).isEmpty();
    }

    public static List<String> lowQualityTranslationReasons(String value) {
        String normalized = normalizeWhitespace(value);
        String lower = normalized.toLowerCase(Locale.ROOT);
        List<String> reasons = new ArrayList<>();
        if (!StringUtils.hasText(lower)) {
            return List.of();
        }
        if (CJK_PATTERN.matcher(normalized).find()) {
            reasons.add("LOW_QUALITY_TRANSLATION:CJK_REMAINS");
        }
        if (containsBrokenTitlePlaceholder(normalized)) {
            reasons.add("LOW_QUALITY_TRANSLATION:BROKEN_TITLE_PLACEHOLDER");
        }
        if (looksLikeBrokenMarketHeadlineGrammar(normalized)) {
            reasons.add("LOW_QUALITY_TRANSLATION:BROKEN_MARKET_HEADLINE_GRAMMAR");
        }
        if (looksLikeBrokenMarketReasonGrammar(normalized)) {
            reasons.add("LOW_QUALITY_TRANSLATION:BROKEN_MARKET_REASON_GRAMMAR");
        }
        if (looksLikeTickerOnlyHeadlineFragment(normalized)) {
            reasons.add("LOW_QUALITY_TRANSLATION:TICKER_ONLY_HEADLINE_FRAGMENT");
        }
        for (String phrase : LOW_QUALITY_PHRASES) {
            if (lower.contains(phrase)) {
                reasons.add("LOW_QUALITY_TRANSLATION:PHRASE:" + phrase.replaceAll("[^a-z0-9]+", "_"));
            }
        }
        if (lower.length() >= 1_000) {
            return List.copyOf(reasons);
        }
        Pattern.compile("\\b[a-z][a-z]+(?:-[a-z][a-z]+){2,}(?:'s)?\\b")
                .matcher(lower)
                .results()
                .map(match -> match.group().replace("'s", ""))
                .filter(term -> !ALLOWED_HYPHENATED_TERMS.contains(term))
                .findFirst()
                .ifPresent(term -> reasons.add("LOW_QUALITY_TRANSLATION:HYPHENATED_TERM:" + term));
        return List.copyOf(reasons);
    }

    private static boolean containsBrokenTitlePlaceholder(String value) {
        return value.contains("[ ]")
                || value.contains("[]")
                || Pattern.compile("(^|\\s)\"\\s*\"(?=\\s|$)").matcher(value).find()
                || Pattern.compile("\\[[\\s\\d]*]").matcher(value).find()
                || value.contains("↑")
                || value.contains("↓")
                || value.contains("·,")
                || value.endsWith("·");
    }

    private static boolean looksLikeOverlyTerseHeadlineFragment(String value) {
        if (value.length() >= 120) {
            return false;
        }
        long wordCount = Pattern.compile("[A-Za-z]{2,}").matcher(value).results().count();
        return wordCount > 0 && wordCount < 3;
    }

    private static boolean looksLikeTickerOnlyHeadlineFragment(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        long wordCount = Pattern.compile("[A-Za-z]{2,}").matcher(value).results().count();
        boolean hasMarketTicker = lower.contains("kospi") || lower.contains("kosdaq");
        if (!hasMarketTicker || wordCount >= 4) {
            return false;
        }
        boolean hasContextVerb = Pattern.compile(
                "\\b(rise|rises|rose|fall|falls|fell|drop|drops|dropped|"
                        + "plunge|plunges|plunged|"
                        + "rebound|rebounds|recover|recovers|open|opens|close|closes)\\b")
                .matcher(lower)
                .find();
        return !hasContextVerb || Pattern.compile("[↑↓]|\\d").matcher(value).find();
    }

    private static boolean looksLikeBrokenMarketHeadlineGrammar(String value) {
        return Pattern.compile("^KOSPI\\s+(?:drop|plunge|fall)\\b", Pattern.CASE_INSENSITIVE)
                .matcher(value)
                .find();
    }

    private static boolean looksLikeBrokenMarketReasonGrammar(String value) {
        return Pattern.compile("\\bMiddle East risk weigh\\b", Pattern.CASE_INSENSITIVE)
                .matcher(value)
                .find();
    }

    private static String sanitizeEnglishSummaryLine(String value) {
        String normalized = stripGeneratedHeadlineReferenceSuffix(value);
        if (!StringUtils.hasText(normalized)
                || containsHangul(normalized)
                || containsEllipsis(normalized)
                || containsSummaryMeta(normalized)
                || containsGenericFallback(normalized)
                || containsLowQualityTranslation(normalized)
                || startsWithStockCodeSubject(normalized)
                || isFragmentarySummaryLine(normalized)
                || !endsAsEnglishSentence(normalized)) {
            return "";
        }
        return normalized;
    }

    private static boolean isFragmentarySummaryLine(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (value.startsWith("()")
                || value.equals(".")
                || lower.equals("korean stock market.")
                || lower.equals("korean stock market")) {
            return true;
        }
        long wordCount = Pattern.compile("[A-Za-z]{2,}").matcher(value).results().count();
        if (wordCount < 4) {
            return true;
        }
        long letterCount = Pattern.compile("[A-Za-z]").matcher(value).results().count();
        long digitCount = Pattern.compile("\\d").matcher(value).results().count();
        long punctuationCount = Pattern.compile("[()%,;:·]").matcher(value).results().count();
        if (letterCount == 0 || punctuationCount * 100 > letterCount * 34) {
            return true;
        }
        if (digitCount > letterCount && wordCount < 7) {
            return true;
        }
        return lower.startsWith("the article cites ") && wordCount < 6;
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

    private static String stripGeneratedHeadlineReferenceSuffix(String value) {
        String normalized = normalizeWhitespace(value)
                .replaceFirst(
                        "(?is)^(?:[.!?]\\s*)?the headline also references.*?[.!?]\\s+(?=[A-Z\\[])",
                        "");
        return normalized
                .replaceFirst("(?is)\\s+the headline also references.*$", "")
                .trim();
    }

    private static boolean containsEllipsis(String value) {
        return Pattern.compile("^(?:\\.\\.\\.|…)|(?:\\.\\.\\.|…)[\\s\"')\\]]*$").matcher(value).find();
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
