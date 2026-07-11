package com.hana.omnilens.provider.news;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hana.omnilens.provider.ExternalProviderResiliencePolicy;

@Component
public class OriginalArticleClient {

    public static final String LICENSED_NAVER_ORIGINAL_FULL_TEXT = "licensed_naver_original_full_text_v1";

    private static final Logger LOGGER = LoggerFactory.getLogger(OriginalArticleClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_CONTENT_CHARS = 1_000_000;
    private static final int MIN_ARTICLE_BODY_CHARS = 100;
    private static final int MAX_IMAGES = 10;
    private static final Pattern SCRIPT_LOCATION_REDIRECT = Pattern.compile(
            "(?:top\\.|window\\.|document\\.)?location(?:\\.href)?\\s*=\\s*['\"]([^'\"]+)['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final List<String> ARTICLE_BODY_SELECTORS = List.of(
            "#divNewsContent",
            "#textBody",
            ".viewer",
            ".article-view__body",
            "[itemprop=articleBody]",
            ".section-content[itemprop=articleBody]",
            "#article-view-content-div",
            ".article-body-only",
            "#articleBody",
            "#article_body",
            "#CmAdContent",
            ".acem_text",
            ".article_content_end_middle",
            ".article_content_end_w",
            "#article",
            "#article_main",
            ".viewSection",
            "#cont_newstext",
            ".detail-body",
            ".view_con_text",
            ".news_body",
            ".news-content",
            ".article_body",
            ".article-body",
            "#news_body",
            ".article-view-content",
            ".article_content",
            ".article-content",
            ".article_txt",
            ".view_con",
            ".view_content",
            ".newsct_article",
            "#dic_area",
            ".go_trans._article_content",
            "main article",
            "article",
            "main");
    private static final List<String> IMAGE_URL_ATTRIBUTES = List.of(
            "src",
            "data-src",
            "data-original",
            "data-lazy-src",
            "data-original-src",
            "data-url",
            "data-image");
    private static final List<String> IMAGE_SRCSET_ATTRIBUTES = List.of(
            "srcset",
            "data-srcset",
            "data-original-set");
    private static final String NON_CONTENT_SELECTOR = String.join(",",
            "script",
            "style",
            "noscript",
            "iframe",
            "figure",
            "figcaption",
            "form",
            "button",
            "nav",
            "aside",
            "[hidden]",
            "[style*=display:none]",
            "[style*=\"display: none\"]",
            "#stockCodes",
            "ins",
            "table",
            ".ad",
            ".ads",
            ".advertisement",
            ".advertisement-area",
            ".adv-area",
            "[class*=advert]",
            "[id*=advert]",
            "[class*=banner]",
            "[id*=banner]",
            "[class*=share]",
            "[id*=share]",
            "[class*=sns]",
            "[id*=sns]",
            "[class*=recommend]",
            "[id*=recommend]",
            "[class*=related]",
            "[id*=related]",
            ".article-list",
            "[class*=article-list]",
            "[class*=copyright]",
            "[id*=copyright]",
            "[class*=reporter]",
            "[id*=reporter]",
            ".article_con_img",
            ".mimg_img",
            ".mimg_open",
            ".blind");

    private final RestClient restClient;
    private final ExternalProviderResiliencePolicy resiliencePolicy;

    public OriginalArticleClient(
            RestClient.Builder restClientBuilder,
            ExternalProviderResiliencePolicy resiliencePolicy) {
        this.restClient = restClientBuilder.build();
        this.resiliencePolicy = resiliencePolicy;
    }

    public Optional<OriginalArticleContent> fetch(String originalUrl) {
        URI uri = safeHttpUri(originalUrl);
        if (uri == null) {
            return Optional.empty();
        }

        try {
            FetchedHtml fetched = resiliencePolicy.execute("news-original-content", () -> fetchHtml(uri, 0));
            Optional<OriginalArticleContent> parsed = parse(fetched.sourceUri(), fetched.html());
            if (parsed.isPresent()) {
                return parsed;
            }
            Optional<OriginalArticleContent> publisherAlternate = fetchKnownPublisherAlternateArticle(fetched);
            if (publisherAlternate.isPresent()) {
                return publisherAlternate;
            }
            Optional<OriginalArticleContent> redirected = fetchScriptRedirectArticle(fetched);
            if (redirected.isPresent()) {
                return redirected;
            }
            return fetchAmpArticle(fetched);
        } catch (RestClientException | IllegalArgumentException exception) {
            LOGGER.warn("Original article fetch failed. url={}, reason={}",
                    uri, exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private FetchedHtml fetchHtml(URI uri, int redirectCount) {
        ResponseEntity<String> response = restClient.get()
                .uri(uri)
                .header("User-Agent", "Hana-OmniLensBot/1.0 (+https://github.com/Hana-harmony)")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .retrieve()
                .toEntity(String.class);
        if (response.getStatusCode().is3xxRedirection()) {
            URI location = response.getHeaders().getLocation();
            if (location == null || redirectCount >= MAX_REDIRECTS) {
                return new FetchedHtml("", uri);
            }
            URI redirectedUri = safeHttpUri(uri.resolve(location).toString());
            if (redirectedUri == null) {
                return new FetchedHtml("", uri);
            }
            return fetchHtml(redirectedUri, redirectCount + 1);
        }
        return new FetchedHtml(response.getBody(), uri);
    }

    private Optional<OriginalArticleContent> fetchKnownPublisherAlternateArticle(FetchedHtml fetched) {
        URI alternateUri = knownPublisherAlternateUri(fetched.sourceUri());
        if (alternateUri == null || alternateUri.equals(fetched.sourceUri())) {
            return Optional.empty();
        }
        try {
            FetchedHtml alternateFetched = resiliencePolicy.execute(
                    "news-original-content-publisher-alternate",
                    () -> fetchHtml(alternateUri, 0));
            return parse(alternateFetched.sourceUri(), alternateFetched.html());
        } catch (RestClientException | IllegalArgumentException exception) {
            LOGGER.warn("Original article publisher alternate fetch failed. url={}, reason={}",
                    alternateUri, exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private URI knownPublisherAlternateUri(URI sourceUri) {
        if (sourceUri == null || sourceUri.getHost() == null || sourceUri.getPath() == null) {
            return null;
        }
        String host = sourceUri.getHost().toLowerCase(Locale.ROOT);
        String path = sourceUri.getPath();
        if ("biz.sbs.co.kr".equals(host)) {
            Matcher matcher = Pattern.compile("^/article_hub/(\\d+)$").matcher(path);
            if (matcher.find()) {
                return safeHttpUri("https://biz.sbs.co.kr/amp/article/" + matcher.group(1));
            }
        }
        return null;
    }

    private Optional<OriginalArticleContent> fetchScriptRedirectArticle(FetchedHtml fetched) {
        URI redirectUri = scriptRedirectUri(fetched);
        if (redirectUri == null || redirectUri.equals(fetched.sourceUri())) {
            return Optional.empty();
        }
        try {
            FetchedHtml redirectedFetched = resiliencePolicy.execute(
                    "news-original-content-script-redirect",
                    () -> fetchHtml(redirectUri, 0));
            Optional<OriginalArticleContent> parsed = parse(redirectedFetched.sourceUri(), redirectedFetched.html());
            if (parsed.isPresent()) {
                return parsed;
            }
            return fetchAmpArticle(redirectedFetched);
        } catch (RestClientException | IllegalArgumentException exception) {
            LOGGER.warn("Original article script redirect fetch failed. url={}, reason={}",
                    redirectUri, exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private URI scriptRedirectUri(FetchedHtml fetched) {
        if (fetched == null || !StringUtils.hasText(fetched.html())) {
            return null;
        }
        Document document = Jsoup.parse(fetched.html(), fetched.sourceUri().toString());
        List<URI> candidates = new ArrayList<>();
        for (Element script : document.select("script")) {
            Matcher matcher = SCRIPT_LOCATION_REDIRECT.matcher(script.html());
            while (matcher.find()) {
                URI candidate = safeHttpUri(fetched.sourceUri().resolve(matcher.group(1)).toString());
                if (candidate != null && !candidate.equals(fetched.sourceUri())) {
                    candidates.add(candidate);
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
                .filter(candidate -> sameHost(candidate, fetched.sourceUri()))
                .findFirst()
                .orElseGet(() -> candidates.get(0));
    }

    private boolean sameHost(URI left, URI right) {
        if (left == null || right == null || left.getHost() == null || right.getHost() == null) {
            return false;
        }
        return left.getHost().equalsIgnoreCase(right.getHost());
    }

    private Optional<OriginalArticleContent> fetchAmpArticle(FetchedHtml fetched) {
        URI ampUri = ampHtmlUri(fetched);
        if (ampUri == null || ampUri.equals(fetched.sourceUri())) {
            return Optional.empty();
        }
        try {
            FetchedHtml ampFetched = resiliencePolicy.execute(
                    "news-original-content-amp",
                    () -> fetchHtml(ampUri, 0));
            return parse(ampFetched.sourceUri(), ampFetched.html());
        } catch (RestClientException | IllegalArgumentException exception) {
            LOGGER.warn("Original article AMP fetch failed. url={}, reason={}",
                    ampUri, exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private URI ampHtmlUri(FetchedHtml fetched) {
        if (fetched == null || !StringUtils.hasText(fetched.html())) {
            return null;
        }
        Document document = Jsoup.parse(fetched.html(), fetched.sourceUri().toString());
        Element ampHtml = document.selectFirst("link[rel=amphtml]");
        if (ampHtml == null || !StringUtils.hasText(ampHtml.attr("href"))) {
            return null;
        }
        return safeHttpUri(fetched.sourceUri().resolve(ampHtml.attr("href")).toString());
    }

    private Optional<OriginalArticleContent> parse(URI sourceUri, String html) {
        if (!StringUtils.hasText(html)) {
            return Optional.empty();
        }

        Document document = Jsoup.parse(html, sourceUri.toString());
        String scriptedContent = normalize(selectScriptedContent(document));
        document.select("script,style,noscript,iframe,nav,header,footer,aside,form").remove();
        String canonicalUrl = canonicalUrl(document, sourceUri);
        String domContent = selectContent(document);
        String content = isBetterArticleCandidate(scriptedContent, domContent) ? scriptedContent : domContent;
        if (!StringUtils.hasText(content)) {
            return Optional.empty();
        }
        String limitedContent = limitArticleContent(content, canonicalUrl);
        return Optional.of(new OriginalArticleContent(
                limitedContent,
                imageUrls(document, sourceUri),
                canonicalUrl,
                sha256Hex(canonicalUrl + "\n" + limitedContent),
                LICENSED_NAVER_ORIGINAL_FULL_TEXT,
                pageTitle(document)));
    }

    private String pageTitle(Document document) {
        for (String selector : List.of(
                "meta[property=og:title]",
                "meta[name=twitter:title]",
                "meta[name=title]")) {
            Element element = document.selectFirst(selector);
            if (element != null && StringUtils.hasText(element.attr("content"))) {
                return cleanPageTitle(element.attr("content"));
            }
        }
        Element heading = document.selectFirst("h1");
        if (heading != null && StringUtils.hasText(heading.text())) {
            return cleanPageTitle(heading.text());
        }
        return cleanPageTitle(document.title());
    }

    private String cleanPageTitle(String value) {
        String title = normalize(value)
                .replaceAll("\\s+[-|:]\\s+[^-|:]{1,40}$", "")
                .replaceAll("\\s+::?\\s+[^:]{1,40}$", "")
                .strip();
        return limit(title, 300);
    }

    private String selectContent(Document document) {
        String selected = "";
        for (String selector : ARTICLE_BODY_SELECTORS) {
            for (Element article : document.select(selector)) {
                String candidate = cleanArticleText(article);
                if (isLikelyArticleBody(candidate)
                        && isBetterArticleCandidate(candidate, selected)) {
                    selected = candidate;
                }
            }
        }
        return selected;
    }

    private String selectScriptedContent(Document document) {
        for (Element script : document.select("script")) {
            String scriptBody = script.html();
            String content = extractFusionGlobalContent(scriptBody);
            if (isLikelyArticleBody(content)) {
                return content;
            }
        }
        return "";
    }

    private String extractFusionGlobalContent(String scriptBody) {
        String marker = "Fusion.globalContent=";
        int markerIndex = scriptBody.indexOf(marker);
        if (markerIndex < 0) {
            return "";
        }
        int objectStart = scriptBody.indexOf('{', markerIndex + marker.length());
        if (objectStart < 0) {
            return "";
        }
        int objectEnd = findJsonObjectEnd(scriptBody, objectStart);
        if (objectEnd <= objectStart) {
            return "";
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(scriptBody.substring(objectStart, objectEnd));
            return normalize(extractFusionTextElements(root));
        } catch (Exception exception) {
            LOGGER.debug("Fusion global content parse skipped. reason={}", exception.getClass().getSimpleName());
            return "";
        }
    }

    private int findJsonObjectEnd(String value, int objectStart) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = objectStart; index < value.length(); index++) {
            char current = value.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = inString;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return index + 1;
                }
            }
        }
        return -1;
    }

    private String extractFusionTextElements(JsonNode root) {
        JsonNode elements = root.path("content_elements");
        if (!elements.isArray()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode element : elements) {
            if (!"text".equals(element.path("type").asText())) {
                continue;
            }
            String content = element.path("content").asText("");
            if (StringUtils.hasText(content)) {
                parts.add(Jsoup.parse(content).text());
            }
        }
        return String.join(" ", parts);
    }

    private boolean isLikelyArticleBody(String candidate) {
        return candidate.length() >= MIN_ARTICLE_BODY_CHARS
                && sourceSentenceCount(candidate) >= 2
                && !isBoilerplate(candidate);
    }

    private boolean isBetterArticleCandidate(String candidate, String current) {
        if (!StringUtils.hasText(current)) {
            return true;
        }
        int candidateScore = articleBodyScore(candidate);
        int currentScore = articleBodyScore(current);
        return candidateScore > currentScore
                || (candidateScore == currentScore && candidate.length() > current.length());
    }

    private int articleBodyScore(String value) {
        String normalized = normalize(value);
        int sentenceCount = sourceSentenceCount(normalized);
        int score = normalized.length() + (sentenceCount * 80);
        if (normalized.contains("[앵커]") || normalized.contains("[기자]")) {
            score += 120;
        }
        if (normalized.contains("관련 키워드") || normalized.contains("주요뉴스")) {
            score -= 120;
        }
        score -= stockNewsAggregatorNoiseCount(normalized) * 400;
        if (normalized.contains("실시간 속보") || normalized.contains("많이 본 뉴스")
                || normalized.contains("관련기사") || normalized.contains("추천뉴스")) {
            score -= 2_000;
        }
        return score;
    }

    private int sourceSentenceCount(String text) {
        return (int) java.util.regex.Pattern.compile("[.!?。]|(?:다|요|니다|습니다|한다|했다|됐다|된다)(?=\\s|$)")
                .splitAsStream(text)
                .filter(value -> value != null && !value.isBlank())
                .count();
    }

    private String cleanArticleText(Element article) {
        if (article == null) {
            return "";
        }
        Element copy = article.clone();
        copy.select(NON_CONTENT_SELECTOR).forEach(element -> {
            if (element != copy) {
                element.remove();
            }
        });
        List<String> paragraphs = copy.select("p,li,blockquote,h1,h2,h3,h4").stream()
                .map(Element::text)
                .map(this::removeBoilerplateText)
                .filter(StringUtils::hasText)
                .toList();
        if (paragraphs.size() >= 2) {
            return String.join("\n\n", paragraphs);
        }
        return removeBoilerplateText(normalizeArticleText(copy.wholeText()));
    }

    private boolean isBoilerplate(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.startsWith("quick report title author date result")
                || normalized.startsWith("share this article send article")
                || normalized.contains("copy url close")
                || normalized.contains("all violation of")
                || normalized.contains("facebook twitter kakao")
                || normalized.contains("가상커서를 해제")
                || normalized.contains("읽어주기 기능은")
                || normalized.contains("댓글 이용시 소셜계정")
                || normalized.contains("internet explorer 8")
                || normalized.contains("핫 뉴스 태그")
                || normalized.contains("단독 연재 인터뷰")
                || stockNewsAggregatorNoiseCount(normalized) >= 4;
    }

    private int stockNewsAggregatorNoiseCount(String normalized) {
        int count = 0;
        for (String marker : List.of("［", "[", "기자］", "기자]")) {
            int fromIndex = 0;
            while (true) {
                int index = normalized.indexOf(marker, fromIndex);
                if (index < 0) {
                    break;
                }
                count++;
                fromIndex = index + marker.length();
            }
        }
        return count;
    }

    private String removeBoilerplateText(String value) {
        String cleaned = value
                .replaceAll("잠깐!\\s*현재\\s*Internet Explorer\\s*8이하[^!。.!?]*[!。.!?]", " ")
                .replaceAll("읽어주기 기능은[^.。!?]*(?:있습니다|하세요)[.。!]?", " ")
                .replaceAll("센스리더 사용자는[^.。!?]*(?:하세요|이용하세요)[.。!]?", " ")
                .replaceAll("\\(가상커서 해제 단축키\\s*:[^)]+\\)", " ")
                .replaceAll("좌\\s*/\\s*우 방향키는[^.。!?]*(?:조절됩니다|이동됩니다)[.。!]?", " ")
                .replaceAll("상\\s*/\\s*하 방향키는[^.。!?]*(?:조절됩니다|이동됩니다)[.。!]?", " ")
                .replaceAll("스페이스 바를 누르시면[^.。!?]*됩니다[.。!]?", " ")
                .replaceAll("댓글 이용시 소셜계정으로 로그인하셔야 하며[^.。!?]*표시됩니다[.。!]?", " ")
                .replaceAll("이미지\\s*확대보기", " ")
                .replaceAll("등록\\s*\\d{4}[.\\-/]\\d{1,2}[.\\-/]\\d{1,2}\\s*\\d{1,2}:\\d{2}:\\d{2}", " ")
                .replaceAll("구글에서\\s*선호하는\\s*매체로\\s*추가", " ")
                .replaceAll("\\b작게\\s+크게\\b", " ")
                .replaceAll("가\\s+가\\s+기사의\\s*본문\\s*내용은\\s*이\\s*글자\\s*크기로\\s*변경됩니다[.。!]?", " ")
                .replaceAll("본문의\\s*글자\\s*크기[^.。!?]*(?:조절|변경)[^.。!?]*[.。!]?", " ")
                .replaceAll("본문\\s*글자\\s*크기[^.。!?]*(?:조절|변경)[^.。!?]*[.。!]?", " ")
                .replaceAll("댓글\\s*\\d+\\s*추천\\s*\\d+", " ")
                .replaceAll("무단\\s*전재\\s*및\\s*재배포\\s*금지", " ")
                .replaceAll("\\[서울=뉴시스]\\s*[^.。!?]{0,140}\\(사진출처:[^)]+\\)", " ")
                .replaceAll(
                        "[가-힣]{2,4}\\s+[^@\\s]{0,20}\\s*기자\\s+[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}",
                        " ")
                .replaceAll("독자들의\\s*PICK!.*$", " ")
                .replaceAll("머니투데이\\s*주요뉴스.*$", " ")
                .replaceAll("함께\\s*볼만한\\s*뉴스.*$", " ");
        int newsPimBodyStart = cleaned.indexOf("[서울=뉴스핌]");
        if (newsPimBodyStart > 0 && cleaned.substring(0, newsPimBodyStart).contains("AI 핵심 요약")) {
            cleaned = cleaned.substring(newsPimBodyStart);
        }
        cleaned = cleaned
                .replaceAll("AI\\s*핵심\\s*요약\\s*beta\\s*분석\\s*중\\.\\.\\..*?!\\s*", " ")
                .replaceAll("AI가\\s*자동\\s*생성한\\s*요약으로[^.。!?]*[.。!?]", " ")
                .replaceAll("이\\s*기사는\\s*인공지능\\(AI\\)\\s*번역으로\\s*생산된\\s*콘텐츠로[^.。!?]*[.。!?]", " ")
                .replaceAll("googletag\\.cmd\\.push\\(function\\(\\)\\s*\\{.*?\\}\\);", " ")
                .replaceAll("\\s*좋아요\\s*\\d+\\s*훈훈해요\\s*\\d+.*$", " ")
                .replaceAll("\\s*이\\s*기사를\\s*공유합니다.*$", " ")
                .replaceAll("\\s*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
                        + "\\s+(?:(?:[가-힣]{2,4}\\s*기자)|(?:.*저작권자\\s*ⓒ)).*$", " ")
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\s*"
                        + "[가-힣]{2,4}\\s*기자\\s*(?:#[^#\\s]+\\s*)*"
                        + "(?:※\\s*)?저작권자\\s*ⓒ[^\\n]*$", " ")
                .replaceAll("\\s*(?:#[^#\\s]+\\s*)+※\\s*저작권자\\s*ⓒ[^\\n]*$", " ")
                .replaceAll("※\\s*저작권자\\s*ⓒ[^\\n]*$", " ");
        return normalizeArticleText(cleaned);
    }

    private String metaDescription(Document document) {
        Element ogDescription = document.selectFirst("meta[property=og:description]");
        if (ogDescription != null && StringUtils.hasText(ogDescription.attr("content"))) {
            return ogDescription.attr("content");
        }
        Element description = document.selectFirst("meta[name=description]");
        return description == null ? "" : description.attr("content");
    }

    private List<String> imageUrls(Document document, URI sourceUri) {
        Set<String> urls = new LinkedHashSet<>();
        addImageUrl(urls, document.selectFirst("meta[property=og:image]"), "content", sourceUri);
        addImageUrl(urls, document.selectFirst("meta[property=og:image:url]"), "content", sourceUri);
        addImageUrl(urls, document.selectFirst("meta[property=og:image:secure_url]"), "content", sourceUri);
        addImageUrl(urls, document.selectFirst("meta[name=twitter:image]"), "content", sourceUri);
        addImageUrl(urls, document.selectFirst("meta[name=twitter:image:src]"), "content", sourceUri);
        addImageUrl(urls, document.selectFirst("meta[itemprop=image]"), "content", sourceUri);
        addImageUrl(urls, document.selectFirst("meta[itemprop=thumbnailUrl]"), "content", sourceUri);
        addImageUrl(urls, document.selectFirst("meta[name=thumbnail]"), "content", sourceUri);
        addImageUrl(urls, document.selectFirst("meta[name=image]"), "content", sourceUri);
        addImageUrl(urls, document.selectFirst("link[rel=image_src]"), "href", sourceUri);
        Element article = firstArticleImageRoot(document);
        if (article != null) {
            addImageUrlsFrom(urls, article, sourceUri);
        } else {
            for (Element root : document.select("article, main, body")) {
                addImageUrlsFrom(urls, root, sourceUri);
                if (urls.size() >= MAX_IMAGES) {
                    break;
                }
            }
        }
        return urls.stream().limit(MAX_IMAGES).toList();
    }

    private Element firstArticleImageRoot(Document document) {
        for (String selector : ARTICLE_BODY_SELECTORS) {
            Element article = document.selectFirst(selector);
            if (article != null) {
                return article;
            }
        }
        return null;
    }

    private void addImageUrlsFrom(Set<String> urls, Element root, URI sourceUri) {
        for (Element image : root.select("img")) {
            if (urls.size() >= MAX_IMAGES) {
                return;
            }
            for (String attribute : IMAGE_URL_ATTRIBUTES) {
                addImageUrl(urls, image, attribute, sourceUri);
            }
            for (String attribute : IMAGE_SRCSET_ATTRIBUTES) {
                addImageSrcSetUrls(urls, image.attr(attribute), sourceUri);
            }
            if (urls.size() >= MAX_IMAGES) {
                break;
            }
        }
    }

    private void addImageUrl(Set<String> urls, Element element, String attribute, URI sourceUri) {
        if (element == null || urls.size() >= MAX_IMAGES) {
            return;
        }
        addImageUrl(urls, element.attr(attribute), sourceUri);
    }

    private void addImageSrcSetUrls(Set<String> urls, String rawSrcSet, URI sourceUri) {
        if (!StringUtils.hasText(rawSrcSet) || urls.size() >= MAX_IMAGES) {
            return;
        }
        for (String candidate : rawSrcSet.split(",")) {
            if (urls.size() >= MAX_IMAGES) {
                return;
            }
            String rawUrl = candidate.trim().split("\\s+")[0];
            addImageUrl(urls, rawUrl, sourceUri);
        }
    }

    private void addImageUrl(Set<String> urls, String rawUrl, URI sourceUri) {
        if (!StringUtils.hasText(rawUrl) || urls.size() >= MAX_IMAGES) {
            return;
        }
        String normalizedUrl = rawUrl.trim();
        if (normalizedUrl.startsWith("data:")
                || normalizedUrl.startsWith("blob:")
                || normalizedUrl.startsWith("javascript:")
                || normalizedUrl.contains("{{")) {
            return;
        }
        URI imageUri = safeHttpUri(sourceUri.resolve(normalizedUrl).toString());
        if (imageUri != null && !isLikelyNonArticleImage(imageUri)) {
            urls.add(imageUri.toString());
        }
    }

    private boolean isLikelyNonArticleImage(URI imageUri) {
        String path = Optional.ofNullable(imageUri.getPath()).orElse("").toLowerCase(Locale.ROOT);
        String query = Optional.ofNullable(imageUri.getQuery()).orElse("").toLowerCase(Locale.ROOT);
        String value = path + "?" + query;
        return path.endsWith(".svg")
                || value.contains("logo")
                || value.contains("sns")
                || value.contains("share")
                || value.contains("icon")
                || value.contains("utilbar")
                || value.contains("quick")
                || value.contains("btn_")
                || value.contains("profile")
                || value.contains("reporter")
                || value.contains("banner")
                || value.contains("sample")
                || value.contains("spacer")
                || value.contains("blank")
                || value.contains("no_image")
                || value.contains("noimage")
                || value.contains("1x1")
                || value.contains("trans_");
    }

    private String canonicalUrl(Document document, URI sourceUri) {
        Element canonical = document.selectFirst("link[rel=canonical]");
        if (canonical != null && StringUtils.hasText(canonical.attr("href"))) {
            URI canonicalUri = safeHttpUri(sourceUri.resolve(canonical.attr("href")).toString());
            if (canonicalUri != null) {
                return canonicalUri.toString();
            }
        }
        return sourceUri.toString();
    }

    private URI safeHttpUri(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            URI uri = new URI(value.strip()).normalize();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost();
            if ((!scheme.equals("https") && !scheme.equals("http")) || !StringUtils.hasText(host)) {
                return null;
            }
            if (isBlockedHost(host)) {
                return null;
            }
            return uri;
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private boolean isBlockedHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.equals("localhost") || normalized.endsWith(".localhost")) {
            return true;
        }
        try {
            InetAddress address = InetAddress.getByName(normalized);
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress();
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").strip();
    }

    private String normalizeArticleText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00a0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f ]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String limitArticleContent(String value, String canonicalUrl) {
        if (value.length() <= MAX_CONTENT_CHARS) {
            return value;
        }
        LOGGER.warn("Original article content exceeded local safety limit. url={}, sourceChars={}, maxChars={}",
                canonicalUrl, value.length(), MAX_CONTENT_CHARS);
        return value.substring(0, MAX_CONTENT_CHARS);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record FetchedHtml(String html, URI sourceUri) {
    }
}
