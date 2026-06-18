package com.hana.omnilens.provider.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.KrxOpenApiProperties;
import com.hana.omnilens.provider.ProviderTestResilience;

class KrxOpenApiDailyTradeClientTest {

    @Test
    void findKospiDailyTradesCallsKrxOpenApiWithAuthKeyAndMapsOutput() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KrxOpenApiDailyTradeClient client = new KrxOpenApiDailyTradeClient(
                builder,
                new KrxOpenApiProperties(URI.create("https://data-dbg.krx.example"), "krx-open-api-key"),
                ProviderTestResilience.disabled());

        server.expect(requestTo(containsString("/svc/apis/sto/stk_bydd_trd")))
                .andExpect(requestTo(containsString("basDd=20250604")))
                .andExpect(header("AUTH_KEY", "krx-open-api-key"))
                .andRespond(withSuccess("""
                        {
                          "OutBlock_1": [
                            {
                              "BAS_DD": "20250604",
                              "ISU_CD": "KR7005930003",
                              "ISU_SRT_CD": "005930",
                              "ISU_NM": "삼성전자",
                              "MKT_NM": "KOSPI",
                              "TDD_CLSPRC": "58,700",
                              "FLUC_RT": "1.91",
                              "ACC_TRDVOL": "19,123,456"
                            }
                          ]
                        }
                        """, APPLICATION_JSON));

        List<KrxOpenApiDailyTrade> trades = client.findKospiDailyTrades(LocalDate.of(2025, 6, 4));

        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).baseDate()).isEqualTo(LocalDate.of(2025, 6, 4));
        assertThat(trades.get(0).isinCode()).isEqualTo("KR7005930003");
        assertThat(trades.get(0).stockCode()).isEqualTo("005930");
        assertThat(trades.get(0).stockName()).isEqualTo("삼성전자");
        assertThat(trades.get(0).market()).isEqualTo("KOSPI");
        assertThat(trades.get(0).closingPriceKrw()).isEqualByComparingTo("58700");
        assertThat(trades.get(0).changeRate()).isEqualByComparingTo("1.91");
        assertThat(trades.get(0).tradingVolume()).isEqualTo(19_123_456L);
        server.verify();
    }

    @Test
    void findKospiDailyTradesFailsClosedWhenAuthKeyIsMissing() {
        KrxOpenApiDailyTradeClient client = new KrxOpenApiDailyTradeClient(
                RestClient.builder(),
                new KrxOpenApiProperties(URI.create("https://data-dbg.krx.example"), ""),
                ProviderTestResilience.disabled());

        assertThatThrownBy(() -> client.findKospiDailyTrades(LocalDate.of(2025, 6, 4)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("omnilens.providers.krx-open-api.auth-key");
    }
}
