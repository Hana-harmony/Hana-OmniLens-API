package com.hana.omniconnect.provider.market;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.hana.omniconnect.config.ExternalProviderProperties;
import com.hana.omniconnect.market.domain.StockSummary;
import com.hana.omniconnect.provider.ExternalProviderResiliencePolicy;

@Component
@ConditionalOnProperty(prefix = "omni-connect.providers.krx", name = "scraping-enabled", havingValue = "true")
public class KrxForeignOwnershipHistoricalSnapshotClient implements ForeignOwnershipHistoricalSnapshotClient {

    private static final DateTimeFormatter KRX_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter KRX_SLASH_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final String JSON_DATA_PATH = "/comm/bldAttendant/getJsonData.cmd";

    private final RestClient restClient;
    private final ExternalProviderProperties.Krx properties;
    private final ExternalProviderResiliencePolicy resiliencePolicy;
    private final ObjectMapper objectMapper;
    private final AtomicReference<Map<String, String>> cookies = new AtomicReference<>(Map.of());

    public KrxForeignOwnershipHistoricalSnapshotClient(
            RestClient.Builder restClientBuilder,
            ExternalProviderProperties properties,
            ExternalProviderResiliencePolicy resiliencePolicy,
            ObjectMapper objectMapper) {
        this.restClient = restClientBuilder
                .baseUrl(properties.krx().baseUrl().toString())
                .build();
        this.properties = properties.krx();
        this.resiliencePolicy = resiliencePolicy;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ForeignOwnershipSnapshot> findSnapshots(StockSummary stock, LocalDate from, LocalDate to) {
        if (!StringUtils.hasText(stock.isinCode())) {
            return List.of();
        }
        ensureLoggedIn();
        JsonNode root = requestForeignOwnershipHistory(stock, from, to);
        JsonNode output = root == null ? null : root.path("output");
        if (output == null || !output.isArray()) {
            return List.of();
        }
        return StreamSupport.stream(output.spliterator(), false)
                .map(row -> toSnapshot(stock.stockCode(), row))
                .filter(snapshot -> !snapshot.baseDate().isBefore(from) && !snapshot.baseDate().isAfter(to))
                .toList();
    }

    private void ensureLoggedIn() {
        if (!cookies.get().isEmpty()) {
            return;
        }
        resiliencePolicy.execute("krx-login", () -> {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("mbrId", properties.requiredId());
            form.add("pw", properties.requiredPassword());
            form.add("site", "mdc");
            JsonNode root = restClient.post()
                    .uri(properties.loginPath())
                    .headers(this::applyCommonHeaders)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .exchange((request, response) -> {
                        rememberCookies(response.getHeaders());
                        return parseJson(response.bodyTo(String.class));
                    });
            if (isLoginRejected(root)) {
                throw new IllegalStateException("KRX login failed");
            }
            return root;
        });
    }

    private JsonNode requestForeignOwnershipHistory(StockSummary stock, LocalDate from, LocalDate to) {
        return resiliencePolicy.execute("krx-foreign-ownership-history", () -> {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("bld", properties.foreignOwnershipHistoryBld());
            form.add("locale", "ko_KR");
            form.add("searchType", "2");
            form.add("strtDd", KRX_DATE.format(from));
            form.add("endDd", KRX_DATE.format(to));
            form.add("isuCd", stock.isinCode());
            JsonNode root = restClient.post()
                    .uri(JSON_DATA_PATH)
                    .headers(this::applyCommonHeaders)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .exchange((request, response) -> {
                        rememberCookies(response.getHeaders());
                        return parseJson(response.bodyTo(String.class));
                    });
            if (isLoginPage(root)) {
                cookies.set(Map.of());
                throw new IllegalStateException("KRX session expired");
            }
            return root;
        });
    }

    private void applyCommonHeaders(HttpHeaders headers) {
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0");
        headers.set(HttpHeaders.REFERER, properties.baseUrl() + "/contents/MDC/MDI/outerLoader/index.cmd");
        headers.set("X-Requested-With", "XMLHttpRequest");
        String cookieHeader = cookieHeader();
        if (StringUtils.hasText(cookieHeader)) {
            headers.set(HttpHeaders.COOKIE, cookieHeader);
        }
    }

    private void rememberCookies(HttpHeaders headers) {
        List<String> setCookieHeaders = headers.get(HttpHeaders.SET_COOKIE);
        if (setCookieHeaders == null || setCookieHeaders.isEmpty()) {
            return;
        }
        Map<String, String> updatedCookies = new LinkedHashMap<>(cookies.get());
        for (String setCookie : setCookieHeaders) {
            String cookiePair = setCookie.split(";", 2)[0];
            int separator = cookiePair.indexOf('=');
            if (separator > 0) {
                updatedCookies.put(cookiePair.substring(0, separator), cookiePair.substring(separator + 1));
            }
        }
        cookies.set(Map.copyOf(updatedCookies));
    }

    private String cookieHeader() {
        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, String> entry : cookies.get().entrySet()) {
            pairs.add(entry.getKey() + "=" + entry.getValue());
        }
        return String.join("; ", pairs);
    }

    private boolean isLoginRejected(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return true;
        }
        String errorCode = root.path("errorCode").asText("");
        String resultCode = root.path("resultCode").asText("");
        String code = root.path("code").asText("");
        return StringUtils.hasText(errorCode)
                || "ERROR".equalsIgnoreCase(resultCode)
                || "ERROR".equalsIgnoreCase(code);
    }

    private JsonNode parseJson(String body) {
        if (!StringUtils.hasText(body)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("KRX returned non-JSON response", exception);
        }
    }

    private boolean isLoginPage(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return true;
        }
        return root.has("errorCode") || root.has("loginErrCnt");
    }

    private ForeignOwnershipSnapshot toSnapshot(String stockCode, JsonNode row) {
        BigDecimal ownershipRate = parseDecimal(row.path("FORN_SHR_RT").asText());
        BigDecimal exhaustionRate = parseDecimal(row.path("FORN_LMT_EXHST_RT").asText());
        return new ForeignOwnershipSnapshot(
                stockCode,
                parseLong(row.path("FORN_HD_QTY").asText()),
                ownershipRate,
                parseLong(row.path("FORN_ORD_LMT_QTY").asText()),
                exhaustionRate,
                parseDate(row.path("TRD_DD").asText()));
    }

    private static LocalDate parseDate(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.contains("/")) {
            return LocalDate.parse(normalized, KRX_SLASH_DATE);
        }
        return LocalDate.parse(normalized, KRX_DATE);
    }

    private static long parseLong(String value) {
        String normalized = normalizeNumeric(value);
        if (normalized.isBlank() || "-".equals(normalized)) {
            return 0L;
        }
        return Long.parseLong(normalized);
    }

    private static BigDecimal parseDecimal(String value) {
        String normalized = normalizeNumeric(value);
        if (normalized.isBlank() || "-".equals(normalized)) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(normalized);
    }

    private static String normalizeNumeric(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(",", "").replace("%", "").trim();
    }
}
