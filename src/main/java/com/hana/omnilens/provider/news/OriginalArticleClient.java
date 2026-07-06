package com.hana.omnilens.provider.news;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

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

import com.hana.omnilens.provider.ExternalProviderResiliencePolicy;

@Component
public class OriginalArticleClient {

    public static final String LICENSED_NAVER_ORIGINAL_FULL_TEXT = "licensed_naver_original_full_text_v1";

    private static final Logger LOGGER = LoggerFactory.getLogger(OriginalArticleClient.class);
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_CONTENT_CHARS = 20_000;
    private static final int MIN_ARTICLE_BODY_CHARS = 60;
    private static final int MAX_IMAGES = 10;
    private static final List<String> ARTICLE_BODY_SELECTORS = List.of(
            "#divNewsContent",
            "[itemprop=articleBody]",
            ".section-content[itemprop=articleBody]",
            "#article-view-content-div",
            ".article-body-only",
            "#articleBody",
            "#article_body",
            ".news_body",
            ".article_body",
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
            "article");
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
            return parse(fetched.sourceUri(), fetched.html());
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

    private Optional<OriginalArticleContent> parse(URI sourceUri, String html) {
        if (!StringUtils.hasText(html)) {
            return Optional.empty();
        }

        Document document = Jsoup.parse(html, sourceUri.toString());
        document.select("script,style,noscript,iframe,nav,header,footer,aside,form").remove();
        String canonicalUrl = canonicalUrl(document, sourceUri);
        String content = normalize(selectContent(document));
        if (!StringUtils.hasText(content)) {
            return Optional.empty();
        }
        String limitedContent = limit(content, MAX_CONTENT_CHARS);
        return Optional.of(new OriginalArticleContent(
                limitedContent,
                imageUrls(document, sourceUri),
                canonicalUrl,
                sha256Hex(canonicalUrl + "\n" + limitedContent),
                LICENSED_NAVER_ORIGINAL_FULL_TEXT));
    }

    private String selectContent(Document document) {
        for (String selector : ARTICLE_BODY_SELECTORS) {
            Element article = document.selectFirst(selector);
            String candidate = cleanArticleText(article);
            if (candidate.length() >= MIN_ARTICLE_BODY_CHARS && !isBoilerplate(candidate)) {
                return candidate;
            }
        }
        return metaDescription(document);
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
        return removeBoilerplateText(normalize(copy.text()));
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
                || normalized.contains("internet explorer 8");
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
                .replaceAll(
                        "[가-힣]{2,4}\\s+[^@\\s]{0,20}\\s*기자\\s+[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}",
                        " ")
                .replaceAll("독자들의\\s*PICK!.*$", " ")
                .replaceAll("머니투데이\\s*주요뉴스.*$", " ")
                .replaceAll("함께\\s*볼만한\\s*뉴스.*$", " ");
        return normalize(cleaned);
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

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
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
