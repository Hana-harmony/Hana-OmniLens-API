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

import com.hana.omnilens.config.FrankfurterProperties;
import com.hana.omnilens.provider.ProviderTestResilience;

class FrankfurterExchangeRateClientTest {

    @Test
    void findKrwToLocalRateMapsFrankfurterV2Response() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FrankfurterExchangeRateClient client = new FrankfurterExchangeRateClient(
                builder,
                new FrankfurterProperties(URI.create("https://api.frankfurter.example")),
                ProviderTestResilience.disabled());

        server.expect(requestTo(containsString("/v2/rates")))
                .andExpect(requestTo(containsString("base=KRW")))
                .andExpect(requestTo(containsString("quotes=USD")))
                .andRespond(withSuccess("""
                        [
                          {
                            "date": "2026-06-18",
                            "base": "KRW",
                            "quote": "USD",
                            "rate": 0.00066
                          }
                        ]
                        """, APPLICATION_JSON));

        Optional<ProviderExchangeRateSnapshot> snapshot =
                client.findKrwToLocalRate("usd", LocalDate.of(2026, 6, 18));

        assertThat(snapshot).isPresent();
        assertThat(snapshot.orElseThrow().localCurrency()).isEqualTo("USD");
        assertThat(snapshot.orElseThrow().krwToLocalRate()).isEqualByComparingTo("0.00066");
        assertThat(snapshot.orElseThrow().baseDate()).isEqualTo(LocalDate.of(2026, 6, 18));
        assertThat(snapshot.orElseThrow().source()).isEqualTo("FRANKFURTER_DAILY");
        server.verify();
    }
}
