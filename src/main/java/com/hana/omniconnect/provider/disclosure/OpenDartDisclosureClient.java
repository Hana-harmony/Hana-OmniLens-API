package com.hana.omniconnect.provider.disclosure;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.hana.omniconnect.config.ExternalProviderProperties;
import com.hana.omniconnect.provider.ExternalProviderResiliencePolicy;

@Component
public class OpenDartDisclosureClient {

    public static final String OPENDART_PUBLIC_DISCLOSURE_TEXT = "opendart_public_disclosure_text_v1";

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenDartDisclosureClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DART_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int MAX_DOCUMENT_BYTES = 12_000_000;
    private static final int MAX_CORP_CODE_BYTES = 60_000_000;
    private static final int MAX_CONTENT_CHARS = 1_000_000;

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
            Optional<OpenDartApiError> apiError = parseApiError(payload);
            if (apiError.isPresent()) {
                OpenDartApiError error = apiError.orElseThrow();
                LOGGER.info(
                        "OpenDART document is not ready. receiptNumber={}, status={}, message={}",
                        receiptNumber,
                        error.status(),
                        error.message());
                return Optional.empty();
            }
            String documentText = normalize(extractText(payload));
            if (!StringUtils.hasText(documentText)) {
                return Optional.empty();
            }
            String content = limitDocumentContent(documentText, receiptNumber);
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

    public Map<String, String> listedCorpCodesByStockCode() {
        String apiKey = properties.requiredApiKey();
        try {
            byte[] payload = resiliencePolicy.execute("open-dart-corp-code", () -> restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/corpCode.xml")
                            .queryParam("crtfc_key", apiKey)
                            .build())
                    .retrieve()
                    .body(byte[].class));
            return parseListedCorpCodes(payload);
        } catch (RestClientException | IOException | IllegalArgumentException exception) {
            LOGGER.warn("OpenDART corp code fetch failed. reason={}", exception.getClass().getSimpleName());
            return Map.of();
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
        return documentText(new String(payload, StandardCharsets.UTF_8));
    }

    private Optional<OpenDartApiError> parseApiError(byte[] payload) {
        if (payload == null || payload.length == 0 || isZip(payload)) {
            return Optional.empty();
        }
        String source = new String(payload, StandardCharsets.UTF_8).strip();
        if (!StringUtils.hasText(source)) {
            return Optional.empty();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(source);
            String status = root.path("status").asText("").strip();
            if (StringUtils.hasText(status) && !"000".equals(status)) {
                return Optional.of(new OpenDartApiError(status, root.path("message").asText("")));
            }
        } catch (IOException ignored) {
            // document.xml은 오류 시 XML을 반환하므로 JSON 파싱 실패 후 XML을 확인한다.
        }
        org.jsoup.nodes.Document document = Jsoup.parse(source, "", Parser.xmlParser());
        org.jsoup.nodes.Element statusElement = document.selectFirst("status");
        String status = statusElement == null ? "" : statusElement.text().strip();
        if (!StringUtils.hasText(status) || "000".equals(status)) {
            return Optional.empty();
        }
        org.jsoup.nodes.Element messageElement = document.selectFirst("message");
        String message = messageElement == null ? "" : messageElement.text().strip();
        return Optional.of(new OpenDartApiError(status, message));
    }

    private Map<String, String> parseListedCorpCodes(byte[] payload) throws IOException {
        String xml = isZip(payload) ? extractFirstXml(payload) : new String(payload, StandardCharsets.UTF_8);
        if (!StringUtils.hasText(xml)) {
            return Map.of();
        }
        org.jsoup.nodes.Document document = Jsoup.parse(xml, "", Parser.xmlParser());
        Map<String, String> corpCodeByStockCode = new LinkedHashMap<>();
        for (org.jsoup.nodes.Element item : document.select("list")) {
            String corpCode = item.selectFirst("corp_code") == null ? "" : item.selectFirst("corp_code").text().trim();
            String stockCode = item.selectFirst("stock_code") == null ? "" : item.selectFirst("stock_code").text().trim();
            if (stockCode.matches("\\d{6}") && corpCode.matches("\\d{8}")) {
                corpCodeByStockCode.put(stockCode, corpCode);
            }
        }
        return Map.copyOf(corpCodeByStockCode);
    }

    private String extractFirstXml(byte[] payload) throws IOException {
        if (payload == null || payload.length == 0) {
            return "";
        }
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(payload))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().toLowerCase().endsWith(".xml")) {
                    continue;
                }
                LimitedBytes entryBytes = readLimited(zipInputStream, MAX_CORP_CODE_BYTES);
                if (entryBytes.truncated()) {
                    LOGGER.warn("OpenDART corp code entry was truncated before parsing. entryName={}, maxBytes={}",
                            entry.getName(), MAX_CORP_CODE_BYTES);
                }
                return new String(entryBytes.bytes(), StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private boolean isZip(byte[] payload) {
        return payload.length >= 2 && payload[0] == 'P' && payload[1] == 'K';
    }

    private String extractZipText(byte[] payload) throws IOException {
        String bestText = "";
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(payload))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory() || !isDocumentEntry(entry.getName())) {
                    continue;
                }
                LimitedBytes entryBytes = readLimited(zipInputStream, MAX_DOCUMENT_BYTES);
                String text = documentText(new String(entryBytes.bytes(), StandardCharsets.UTF_8));
                if (entryBytes.truncated()) {
                    LOGGER.warn("OpenDART document entry was truncated before parsing. entryName={}, maxBytes={}",
                            entry.getName(), MAX_DOCUMENT_BYTES);
                    return text;
                }
                if (text.length() > bestText.length()) {
                    bestText = text;
                }
            }
        }
        return bestText;
    }

    private boolean isDocumentEntry(String name) {
        String normalized = name == null ? "" : name.toLowerCase();
        return normalized.endsWith(".xml") || normalized.endsWith(".html") || normalized.endsWith(".htm")
                || normalized.endsWith(".txt");
    }

    private LimitedBytes readLimited(ZipInputStream zipInputStream, int maxBytes) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        boolean truncated = false;
        while ((read = zipInputStream.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                int remaining = Math.max(0, maxBytes - (total - read));
                if (remaining > 0) {
                    outputStream.write(buffer, 0, remaining);
                }
                truncated = true;
                break;
            }
            outputStream.write(buffer, 0, read);
        }
        return new LimitedBytes(outputStream.toByteArray(), truncated);
    }

    private String normalize(String value) {
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

    private String documentText(String source) {
        org.jsoup.nodes.Document document = Jsoup.parse(source);
        List<String> sections = document.select("section,p,li,blockquote,h1,h2,h3,h4").stream()
                .map(org.jsoup.nodes.Element::text)
                .filter(StringUtils::hasText)
                .toList();
        return sections.size() >= 2
                ? String.join("\n\n", sections)
                : document.wholeText();
    }

    private String limitDocumentContent(String value, String receiptNumber) {
        if (value.length() <= MAX_CONTENT_CHARS) {
            return value;
        }
        LOGGER.warn("OpenDART document content exceeded local safety limit. receiptNumber={}, sourceChars={}, maxChars={}",
                receiptNumber, value.length(), MAX_CONTENT_CHARS);
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

    private record LimitedBytes(byte[] bytes, boolean truncated) {
    }

    private record OpenDartApiError(String status, String message) {
    }
}
