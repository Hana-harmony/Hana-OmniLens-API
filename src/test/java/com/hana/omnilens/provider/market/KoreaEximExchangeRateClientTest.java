package com.hana.omnilens.provider.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.ExternalProviderProperties;

class KoreaEximExchangeRateClientTest {

    @Test
    void findKrwToLocalRateMapsDealBasisRateToKrwConversionRate() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KoreaEximExchangeRateClient client = new KoreaEximExchangeRateClient(builder, properties());

        server.expect(requestTo(containsString("/site/program/financial/exchangeJSON")))
                .andExpect(requestTo(containsString("authkey=exim-key")))
                .andExpect(requestTo(containsString("searchdate=20250604")))
                .andExpect(requestTo(containsString("data=AP01")))
                .andRespond(withSuccess("""
                        [
                          { "cur_unit": "USD", "deal_bas_r": "1,350.00" },
                          { "cur_unit": "JPY(100)", "deal_bas_r": "920.00" }
                        ]
                        """, APPLICATION_JSON));

        Optional<KoreaEximExchangeRateSnapshot> snapshot =
                client.findKrwToLocalRate("usd", LocalDate.of(2025, 6, 4));

        assertThat(snapshot).isPresent();
        assertThat(snapshot.orElseThrow().localCurrency()).isEqualTo("USD");
        assertThat(snapshot.orElseThrow().krwToLocalRate()).isEqualByComparingTo("0.0007407407407407407");
        assertThat(snapshot.orElseThrow().baseDate()).isEqualTo(LocalDate.of(2025, 6, 4));
        server.verify();
    }

    @Test
    void findKrwToLocalRateSupportsCurrencyUnitMultiplier() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KoreaEximExchangeRateClient client = new KoreaEximExchangeRateClient(builder, properties());

        server.expect(requestTo(containsString("/site/program/financial/exchangeJSON")))
                .andRespond(withSuccess("""
                        [
                          { "cur_unit": "JPY(100)", "deal_bas_r": "920.00" }
                        ]
                        """, APPLICATION_JSON));

        Optional<KoreaEximExchangeRateSnapshot> snapshot =
                client.findKrwToLocalRate("JPY", LocalDate.of(2025, 6, 4));

        assertThat(snapshot).isPresent();
        assertThat(snapshot.orElseThrow().krwToLocalRate()).isEqualByComparingTo("0.1086956521739130");
        server.verify();
    }

    @Test
    void findKrwToLocalRateReturnsEmptyWhenCurrencyIsMissing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KoreaEximExchangeRateClient client = new KoreaEximExchangeRateClient(builder, properties());

        server.expect(requestTo(containsString("/site/program/financial/exchangeJSON")))
                .andRespond(withSuccess("""
                        [
                          { "cur_unit": "EUR", "deal_bas_r": "1,460.00" }
                        ]
                        """, APPLICATION_JSON));

        Optional<KoreaEximExchangeRateSnapshot> snapshot =
                client.findKrwToLocalRate("USD", LocalDate.of(2025, 6, 4));

        assertThat(snapshot).isEmpty();
        server.verify();
    }

    private ExternalProviderProperties properties() {
        return new ExternalProviderProperties(
                null,
                null,
                null,
                null,
                null,
                new ExternalProviderProperties.KoreaExim(
                        URI.create("https://oapi.koreaexim.go.kr"),
                        "exim-key"),
                null);
    }
}
