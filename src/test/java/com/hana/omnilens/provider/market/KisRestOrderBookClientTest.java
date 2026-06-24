package com.hana.omnilens.provider.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.ExternalProviderProperties;
import com.hana.omnilens.provider.ProviderTestResilience;

class KisRestOrderBookClientTest {

    @Test
    void findOrderBookUsesKisDomesticOrderBookContract() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisRestOrderBookClient client = new KisRestOrderBookClient(
                builder,
                properties(),
                new KisAccessTokenProvider(builder, properties(), ProviderTestResilience.disabled()),
                ProviderTestResilience.disabled());

        server.expect(requestTo(containsString("/uapi/domestic-stock/v1/quotations/inquire-asking-price-exp-ccn")))
                .andExpect(requestTo(containsString("FID_COND_MRKT_DIV_CODE=J")))
                .andExpect(requestTo(containsString("FID_INPUT_ISCD=005930")))
                .andExpect(header("authorization", "Bearer kis-access-token"))
                .andExpect(header("appkey", "kis-app-key"))
                .andExpect(header("appsecret", "kis-app-secret"))
                .andExpect(header("tr_id", "FHKST01010200"))
                .andRespond(withSuccess("""
                        {
                          "rt_cd": "0",
                          "msg_cd": "MCA00000",
                          "output1": {
                            "askp1": "354,000",
                            "askp2": "354500",
                            "bidp1": "353500",
                            "bidp2": "353000",
                            "askp_rsqn1": "1,066,476",
                            "askp_rsqn2": "1200",
                            "bidp_rsqn1": "210884",
                            "bidp_rsqn2": "3300"
                          }
                        }
                        """, APPLICATION_JSON));

        Optional<KisRestOrderBookSnapshot> snapshot = client.findOrderBook("005930");

        assertThat(snapshot).isPresent();
        assertThat(snapshot.orElseThrow().asks()).hasSize(2);
        assertThat(snapshot.orElseThrow().bids()).hasSize(2);
        assertThat(snapshot.orElseThrow().asks().get(0).priceKrw()).isEqualByComparingTo("354000");
        assertThat(snapshot.orElseThrow().asks().get(0).quantity()).isEqualTo(1_066_476L);
        assertThat(snapshot.orElseThrow().bids().get(0).priceKrw()).isEqualByComparingTo("353500");
        assertThat(snapshot.orElseThrow().bids().get(0).quantity()).isEqualTo(210_884L);
        server.verify();
    }

    private ExternalProviderProperties properties() {
        return new ExternalProviderProperties(
                null,
                null,
                null,
                null,
                new ExternalProviderProperties.Kis(
                        URI.create("https://openapi.koreainvestment.com:9443"),
                        URI.create("wss://openapi.koreainvestment.com:9443/tryitout"),
                        "00000000",
                        "kis-app-key",
                        "kis-app-secret",
                        "kis-access-token",
                        "kis-approval-key"),
                null);
    }
}
