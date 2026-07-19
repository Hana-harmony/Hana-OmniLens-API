package com.hana.omniconnect.provider.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.hana.omniconnect.config.FrankfurterProperties;
import com.hana.omniconnect.provider.ExternalProviderResiliencePolicy;

@Component
public class FrankfurterExchangeRateClient implements ExchangeRateProviderClient {

    private final RestClient restClient;
    private final ExternalProviderResiliencePolicy resiliencePolicy;

    public FrankfurterExchangeRateClient(
            RestClient.Builder restClientBuilder,
            FrankfurterProperties properties,
            ExternalProviderResiliencePolicy resiliencePolicy) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl().toString())
                .build();
        this.resiliencePolicy = resiliencePolicy;
    }

    @Override
    public Optional<ProviderExchangeRateSnapshot> findKrwToLocalRate(String localCurrency, LocalDate baseDate) {
        String normalizedCurrency = localCurrency.toUpperCase(Locale.ROOT);
        JsonNode root = resiliencePolicy.execute("frankfurter-exchange-rate", () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/rates")
                        .queryParam("base", "KRW")
                        .queryParam("quotes", normalizedCurrency)
                        .build())
                .retrieve()
                .body(JsonNode.class));

        JsonNode item = firstRate(root);
        if (item == null || !normalizedCurrency.equals(item.path("quote").asText(""))) {
            return Optional.empty();
        }
        String rateValue = item.path("rate").asText("");
        if (rateValue.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ProviderExchangeRateSnapshot(
                normalizedCurrency,
                new BigDecimal(rateValue),
                LocalDate.parse(item.path("date").asText()),
                Instant.now(),
                "FRANKFURTER_DAILY"));
    }

    private JsonNode firstRate(JsonNode root) {
        if (root == null || !root.isArray() || root.isEmpty()) {
            return null;
        }
        return root.get(0);
    }
}
