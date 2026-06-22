package com.hana.omnilens.provider.disclosure;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.fasterxml.jackson.databind.JsonNode;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.hana.omnilens.config.ExternalProviderProperties;
import com.hana.omnilens.provider.ExternalProviderResiliencePolicy;

@Component
public class OpenDartDisclosureClient {

    public static final String OPENDART_PUBLIC_DISCLOSURE_TEXT = "opendart_public_disclosure_text_v1";

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenDartDisclosureClient.class);
    private static final DateTimeFormatter DART_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int MAX_DOCUMENT_BYTES = 1_500_000;
    private static final int MAX_CONTENT_CHARS = 20_000;

    private final RestClient restClient;
    private final ExternalProviderProperties.OpenDart properties;
    private final ExternalProviderResiliencePolicy resiliencePolicy;

    public OpenDartDisclosureClient(
            RestClient.Builder restClientBuilder,
            ExternalProviderProperties properties,
            ExternalProviderResiliencePolicy resiliencePolicy) {
        this.restClient = restClientBuilder
                .baseUrl(properties.openDart().baseUrl().toString())
                .build();
        this.properties = properties.openDart();
        this.resiliencePolicy = resiliencePolicy;
    }

    public List<OpenDartDisclosure> search(String corpCode, LocalDate beginDate, LocalDate endDate) {
        String apiKey = properties.requiredApiKey();
        JsonNode root = resiliencePolicy.execute("open-dart", () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/list.json")
                        .queryParam("crtfc_key", apiKey)
                        .queryParam("corp_code", corpCode)
                        .queryParam("bgn_de", DART_DATE.format(beginDate))
                        .queryParam("end_de", DART_DATE.format(endDate))
                        .queryParam("page_no", 1)
                        .queryParam("page_count", 100)
                        .build())
                .retrieve()
                .body(JsonNode.class));

        JsonNode list = root == null ? null : root.path("list");
        if (list == null || !list.isArray()) {
            return List.of();
        }

        return StreamSupport.stream(list.spliterator(), false)
                .map(this::toDisclosure)
                .toList();
    }

    public Optional<OpenDartDisclosureDocument> fetchDocumentContent(String receiptNumber) {
        if (!StringUtils.hasText(receiptNumber)) {
            return Optional.empty();
        }
        String apiKey = properties.requiredApiKey();
        try {
            byte[] payload = resiliencePolicy.execute("open-dart-document", () -> restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/document.xml")
                            .queryParam("crtfc_key", apiKey)
                            .queryParam("rcept_no", receiptNumber)
                            .build())
                    .retrieve()
                    .body(byte[].class));
            String documentText = normalize(extractText(payload));
            if (!StringUtils.hasText(documentText)) {
                return Optional.empty();
            }
            String content = limit(documentText, MAX_CONTENT_CHARS);
            return Optional.of(new OpenDartDisclosureDocument(
                    content,
                    sha256Hex(receiptNumber + "\n" + content),
                    OPENDART_PUBLIC_DISCLOSURE_TEXT));
        } catch (RestClientException | IOException | IllegalArgumentException exception) {
            LOGGER.warn("OpenDART document fetch failed. receiptNumber={}, reason={}",
                    receiptNumber, exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private OpenDartDisclosure toDisclosure(JsonNode item) {
        String receiptNumber = item.path("rcept_no").asText();
        return new OpenDartDisclosure(
                receiptNumber,
                item.path("corp_name").asText(),
                item.path("report_nm").asText(),
                LocalDate.parse(item.path("rcept_dt").asText(), DART_DATE),
                "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + receiptNumber);
    }

    private String extractText(byte[] payload) throws IOException {
        if (payload == null || payload.length == 0) {
            return "";
        }
        if (isZip(payload)) {
            return extractZipText(payload);
        }
        return Jsoup.parse(new String(payload, StandardCharsets.UTF_8)).text();
    }

    private boolean isZip(byte[] payload) {
        return payload.length >= 2 && payload[0] == 'P' && payload[1] == 'K';
    }

    private String extractZipText(byte[] payload) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(payload))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory() || !isDocumentEntry(entry.getName())) {
                    continue;
                }
                byte[] entryBytes = readLimited(zipInputStream, MAX_DOCUMENT_BYTES);
                return Jsoup.parse(new String(entryBytes, StandardCharsets.UTF_8)).text();
            }
        }
        return "";
    }

    private boolean isDocumentEntry(String name) {
        String normalized = name == null ? "" : name.toLowerCase();
        return normalized.endsWith(".xml") || normalized.endsWith(".html") || normalized.endsWith(".htm")
                || normalized.endsWith(".txt");
    }

    private byte[] readLimited(ZipInputStream zipInputStream, int maxBytes) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = zipInputStream.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IllegalArgumentException("OpenDART document is too large");
            }
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
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
