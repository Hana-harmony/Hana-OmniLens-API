package com.hana.omnilens.provider.market;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.ExternalProviderProperties;

@Component
public class KoreaEximExchangeRateClient {

    private static final DateTimeFormatter SEARCH_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Pattern UNIT_MULTIPLIER_PATTERN = Pattern.compile("\\((\\d+)\\)$");
    private static final MathContext RATE_MATH_CONTEXT = MathContext.DECIMAL64;

    private final RestClient restClient;
    private final ExternalProviderProperties.KoreaExim koreaEximProperties;

    public KoreaEximExchangeRateClient(RestClient.Builder restClientBuilder, ExternalProviderProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.koreaExim().baseUrl().toString())
                .build();
        this.koreaEximProperties = properties.koreaExim();
    }

    public Optional<KoreaEximExchangeRateSnapshot> findKrwToLocalRate(String localCurrency, LocalDate baseDate) {
        String normalizedCurrency = localCurrency.toUpperCase(Locale.ROOT);
        JsonNode root = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/site/program/financial/exchangeJSON")
                        .queryParam("authkey", koreaEximProperties.requiredAuthKey())
                        .queryParam("searchdate", baseDate.format(SEARCH_DATE_FORMATTER))
                        .queryParam("data", "AP01")
                        .build())
                .retrieve()
                .body(JsonNode.class);

        if (root == null || !root.isArray()) {
            return Optional.empty();
        }

        for (JsonNode item : root) {
            String unit = item.path("cur_unit").asText("");
            if (!currencyCode(unit).equals(normalizedCurrency)) {
                continue;
            }
            String dealBasisRate = item.path("deal_bas_r").asText("");
            if (dealBasisRate.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new KoreaEximExchangeRateSnapshot(
                    normalizedCurrency,
                    krwToLocalRate(unit, dealBasisRate),
                    baseDate));
        }
        return Optional.empty();
    }

    private String currencyCode(String unit) {
        int unitSuffixStart = unit.indexOf('(');
        String code = unitSuffixStart < 0 ? unit : unit.substring(0, unitSuffixStart);
        return code.toUpperCase(Locale.ROOT);
    }

    private BigDecimal krwToLocalRate(String unit, String dealBasisRate) {
        BigDecimal currencyUnit = new BigDecimal(unitMultiplier(unit));
        BigDecimal krwPerCurrencyUnit = new BigDecimal(dealBasisRate.replace(",", ""));
        return currencyUnit.divide(krwPerCurrencyUnit, RATE_MATH_CONTEXT);
    }

    private String unitMultiplier(String unit) {
        Matcher matcher = UNIT_MULTIPLIER_PATTERN.matcher(unit);
        return matcher.find() ? matcher.group(1) : "1";
    }
}
