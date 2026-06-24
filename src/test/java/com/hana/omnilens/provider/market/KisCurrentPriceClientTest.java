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

class KisCurrentPriceClientTest {

    @Test
    void findCurrentPriceUsesKisDomesticStockQuoteContract() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisCurrentPriceClient client = new KisCurrentPriceClient(
                builder,
                properties(),
                new KisAccessTokenProvider(builder, properties(), ProviderTestResilience.disabled()),
                ProviderTestResilience.disabled());

        server.expect(requestTo(containsString("/uapi/domestic-stock/v1/quotations/inquire-price")))
                .andExpect(requestTo(containsString("FID_COND_MRKT_DIV_CODE=J")))
                .andExpect(requestTo(containsString("FID_INPUT_ISCD=005930")))
                .andExpect(header("authorization", "Bearer kis-access-token"))
                .andExpect(header("appkey", "kis-app-key"))
                .andExpect(header("appsecret", "kis-app-secret"))
                .andExpect(header("tr_id", "FHKST01010100"))
                .andRespond(withSuccess("""
                        {
                          "rt_cd": "0",
                          "msg_cd": "MCA00000",
                          "output": {
                            "stck_shrn_iscd": "005930",
                            "hts_kor_isnm": "삼성전자",
                            "stck_prpr": "81200",
                            "prdy_ctrt": "1.87",
                            "acml_vol": "15500000",
                            "frgn_hldn_qty": "3,642,091,300",
                            "hts_frgn_ehrt": "54.21",
                            "lstn_stcn": "5,969,782,550"
                          }
                        }
                        """, APPLICATION_JSON));

        Optional<KisCurrentPriceSnapshot> snapshot = client.findCurrentPrice("005930");

        assertThat(snapshot).isPresent();
        assertThat(snapshot.orElseThrow().stockName()).isEqualTo("삼성전자");
        assertThat(snapshot.orElseThrow().currentPriceKrw()).isEqualByComparingTo("81200");
        assertThat(snapshot.orElseThrow().changeRate()).isEqualByComparingTo("1.87");
        assertThat(snapshot.orElseThrow().volume()).isEqualTo(15_500_000L);
        assertThat(snapshot.orElseThrow().foreignOwnedQuantity()).isEqualTo(3_642_091_300L);
        assertThat(snapshot.orElseThrow().foreignOwnershipRate()).isEqualByComparingTo("61.008777");
        assertThat(snapshot.orElseThrow().foreignLimitQuantity()).isEqualTo(6_718_486_073L);
        assertThat(snapshot.orElseThrow().foreignLimitExhaustionRate()).isEqualByComparingTo("54.21");
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
