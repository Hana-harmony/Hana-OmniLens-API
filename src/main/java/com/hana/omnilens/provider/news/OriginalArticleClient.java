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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.hana.omnilens.provider.ExternalProviderResiliencePolicy;

@Component
public class OriginalArticleClient {

    public static final String LICENSED_NAVER_ORIGINAL_FULL_TEXT = "licensed_naver_original_full_text_v1";

    private static final Logger LOGGER = LoggerFactory.getLogger(OriginalArticleClient.class);
    private static final int MAX_CONTENT_CHARS = 20_000;
    private static final int MAX_IMAGES = 10;

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
            String html = resiliencePolicy.execute("news-original-content", () -> restClient.get()
                    .uri(uri)
                    .header("User-Agent", "Hana-OmniLensBot/1.0 (+https://github.com/Hana-harmony)")
                    .retrieve()
                    .body(String.class));
            return parse(uri, html);
        } catch (RestClientException | IllegalArgumentException exception) {
            LOGGER.warn("Original article fetch failed. url={}, reason={}",
                    uri, exception.getClass().getSimpleName());
            return Optional.empty();
        }
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
        Element article = document.selectFirst("article");
        if (article != null && article.text().length() >= 120) {
            return article.text();
        }
        Element main = document.selectFirst("main");
        if (main != null && main.text().length() >= 120) {
            return main.text();
        }
        Element body = document.body();
        return body == null ? "" : body.text();
    }

    private List<String> imageUrls(Document document, URI sourceUri) {
        Set<String> urls = new LinkedHashSet<>();
        addImageUrl(urls, document.selectFirst("meta[property=og:image]"), "content", sourceUri);
        addImageUrl(urls, document.selectFirst("meta[name=twitter:image]"), "content", sourceUri);
        for (Element image : document.select("article img[src], main img[src], body img[src]")) {
            addImageUrl(urls, image, "src", sourceUri);
            if (urls.size() >= MAX_IMAGES) {
                break;
            }
        }
        return urls.stream().limit(MAX_IMAGES).toList();
    }

    private void addImageUrl(Set<String> urls, Element element, String attribute, URI sourceUri) {
        if (element == null || urls.size() >= MAX_IMAGES) {
            return;
        }
        String rawUrl = element.attr(attribute);
        URI imageUri = safeHttpUri(sourceUri.resolve(rawUrl).toString());
        if (imageUri != null) {
            urls.add(imageUri.toString());
        }
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
}
