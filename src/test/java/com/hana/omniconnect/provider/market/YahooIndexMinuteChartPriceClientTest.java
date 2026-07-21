package com.hana.omniconnect.provider.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hana.omniconnect.provider.ProviderTestResilience;

class YahooIndexMinuteChartPriceClientTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-07T06:45:00Z"),
            ZoneId.of("Asia/Seoul"));

    @Test
    void findMinutePricesParsesYahooIndexChart() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        YahooIndexMinuteChartPriceClient client = new YahooIndexMinuteChartPriceClient(
                builder,
                ProviderTestResilience.disabled(),
                FIXED_CLOCK);

        server.expect(requestTo(containsString("/v8/finance/chart/%5EKS11")))
                .andExpect(requestTo(containsString("interval=1m")))
                .andExpect(requestTo(containsString("range=5d")))
                .andExpect(header("User-Agent", "Mozilla/5.0"))
                .andRespond(withSuccess("""
                        {
                          "chart": {
                            "result": [
                              {
                                "timestamp": [1783382400, 1783382460, 1783405800],
                                "indicators": {
                                  "quote": [
                                    {
                                      "open": [7919.20, 7918.10, 7656.31],
                                      "high": [7920.00, 7919.00, 7656.31],
                                      "low": [7910.00, 7909.00, 7656.31],
                                      "close": [7915.50, 7912.25, 7656.31],
                                      "volume": [10, 20, 30]
                                    }
                                  ]
                                }
                              }
                            ],
                            "error": null
                          }
                        }
                        """, APPLICATION_JSON));

        List<KisIndexMinuteChartPrice> prices =
                client.findMinutePrices("0001", LocalDate.of(2026, 7, 7), 390);

        assertThat(prices).hasSize(3);
        assertThat(prices.get(0).bucketStart().toString()).isEqualTo("2026-07-07T09:00");
        assertThat(prices.get(2).bucketStart().toString()).isEqualTo("2026-07-07T15:30");
        assertThat(prices.get(2).closeValue()).isEqualByComparingTo("7656.31");
        server.verify();
    }

    @Test
    void findMinutePricesUsesLatestSessionBeforeMarketOpen() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        YahooIndexMinuteChartPriceClient client = new YahooIndexMinuteChartPriceClient(
                builder,
                ProviderTestResilience.disabled(),
                Clock.fixed(Instant.parse("2026-07-07T18:45:00Z"), ZoneId.of("Asia/Seoul")));

        server.expect(requestTo(containsString("/v8/finance/chart/%5EKS11")))
                .andRespond(withSuccess("""
                        {
                          "chart": {
                            "result": [
                              {
                                "timestamp": [1783382400, 1783382460, 1783405800],
                                "indicators": {
                                  "quote": [
                                    {
                                      "open": [7919.20, 7918.10, 7656.31],
                                      "high": [7920.00, 7919.00, 7656.31],
                                      "low": [7910.00, 7909.00, 7656.31],
                                      "close": [7915.50, 7912.25, 7656.31],
                                      "volume": [10, 20, 30]
                                    }
                                  ]
                                }
                              }
                            ],
                            "error": null
                          }
                        }
                        """, APPLICATION_JSON));

        List<KisIndexMinuteChartPrice> prices =
                client.findMinutePrices("0001", LocalDate.of(2026, 7, 8), 390);

        assertThat(prices).hasSize(3);
        assertThat(prices.get(0).bucketStart().toString()).isEqualTo("2026-07-07T09:00");
        assertThat(prices.get(2).bucketStart().toString()).isEqualTo("2026-07-07T15:30");
        server.verify();
    }

    @Test
    void findMinutePricesUsesPreviousSessionForWeekendDate() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        YahooIndexMinuteChartPriceClient client = new YahooIndexMinuteChartPriceClient(
                builder,
                ProviderTestResilience.disabled(),
                FIXED_CLOCK);

        server.expect(requestTo(containsString("range=5d")))
                .andRespond(withSuccess("""
                        {
                          "chart": {
                            "result": [{
                              "timestamp": [1783123200, 1783146600, 1783382400],
                              "indicators": {"quote": [{
                                "open": [7700.00, 7720.00, 7919.20],
                                "high": [7710.00, 7730.00, 7920.00],
                                "low": [7690.00, 7710.00, 7910.00],
                                "close": [7705.00, 7725.00, 7915.50],
                                "volume": [10, 20, 30]
                              }]}
                            }],
                            "error": null
                          }
                        }
                        """, APPLICATION_JSON));

        List<KisIndexMinuteChartPrice> prices =
                client.findMinutePrices("0001", LocalDate.of(2026, 7, 5), 390);

        assertThat(prices).isNotEmpty();
        assertThat(prices.get(prices.size() - 1).bucketStart().toLocalDate())
                .isEqualTo(LocalDate.of(2026, 7, 4));
        assertThat(prices.get(prices.size() - 1).closeValue()).isEqualByComparingTo("7725.00");
        server.verify();
    }
}
