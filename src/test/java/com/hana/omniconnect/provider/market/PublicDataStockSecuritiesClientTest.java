package com.hana.omniconnect.provider.market;

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

import com.hana.omniconnect.config.ExternalProviderProperties;
import com.hana.omniconnect.provider.ProviderTestResilience;

class PublicDataStockSecuritiesClientTest {

    @Test
    void findPriceMapsFirstStockPriceItem() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PublicDataStockSecuritiesClient client = new PublicDataStockSecuritiesClient(
                builder,
                properties(),
                ProviderTestResilience.disabled());

        server.expect(requestTo(containsString("/getStockPriceInfo")))
                .andExpect(requestTo(containsString("likeSrtnCd=005930")))
                .andExpect(requestTo(containsString("basDt=20250604")))
                .andRespond(withSuccess("""
                        {
                          "response": {
                            "body": {
                              "items": {
                                "item": [
                                  {
                                    "basDt": "20250604",
                                    "srtnCd": "005930",
                                    "itmsNm": "삼성전자",
                                    "mrktCtg": "KOSPI",
                                    "clpr": "78500",
                                    "fltRt": "1.42",
                                    "trqu": "12193000"
                                  }
                                ]
                              }
                            }
                          }
                        }
                        """, APPLICATION_JSON));

        Optional<PublicDataStockPriceSnapshot> snapshot = client.findPrice("005930", LocalDate.of(2025, 6, 4));

        assertThat(snapshot).isPresent();
        assertThat(snapshot.orElseThrow().stockName()).isEqualTo("삼성전자");
        assertThat(snapshot.orElseThrow().closingPriceKrw()).isEqualByComparingTo("78500");
        assertThat(snapshot.orElseThrow().changeRate()).isEqualByComparingTo("1.42");
        assertThat(snapshot.orElseThrow().volume()).isEqualTo(12_193_000L);
        server.verify();
    }

    private ExternalProviderProperties properties() {
        return new ExternalProviderProperties(
                new ExternalProviderProperties.PublicData(
                        URI.create("https://apis.data.go.kr/1160100/service/GetStockSecuritiesInfoService"),
                        "public-data-key"),
                null,
                null,
                null,
                null,
                null);
    }
}
