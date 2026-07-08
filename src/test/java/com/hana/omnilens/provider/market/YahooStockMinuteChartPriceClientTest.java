package com.hana.omnilens.provider.market;

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

import com.hana.omnilens.market.domain.StockSummary;
import com.hana.omnilens.provider.ProviderTestResilience;

class YahooStockMinuteChartPriceClientTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-07T06:45:00Z"),
            ZoneId.of("Asia/Seoul"));

    @Test
    void findMinutePricesParsesYahooStockChart() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        YahooStockMinuteChartPriceClient client = new YahooStockMinuteChartPriceClient(
                builder,
                ProviderTestResilience.disabled(),
                FIXED_CLOCK);

        server.expect(requestTo(containsString("/v8/finance/chart/005930.KS")))
                .andExpect(requestTo(containsString("interval=1m")))
                .andExpect(requestTo(containsString("range=8d")))
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
                                      "open": [81200, 81300, 82000],
                                      "high": [81300, 81400, 82100],
                                      "low": [81100, 81200, 81900],
                                      "close": [81250, 81350, 82050],
                                      "volume": [1000, 2000, 3000]
                                    }
                                  ]
                                }
                              }
                            ],
                            "error": null
                          }
                        }
                        """, APPLICATION_JSON));

        List<KisMinuteChartPrice> prices = client.findMinutePrices(
                stock("005930", "KOSPI"),
                LocalDate.of(2026, 7, 7),
                390);

        assertThat(prices).hasSize(3);
        assertThat(prices.get(0).bucketStart().toString()).isEqualTo("2026-07-07T09:00");
        assertThat(prices.get(2).bucketStart().toString()).isEqualTo("2026-07-07T15:30");
        assertThat(prices.get(2).closePriceKrw()).isEqualByComparingTo("82050");
        assertThat(prices.get(2).tradingValueKrw()).isEqualByComparingTo("246150000");
        server.verify();
    }

    @Test
    void findMinutePricesUsesLatestSessionBeforeMarketOpen() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        YahooStockMinuteChartPriceClient client = new YahooStockMinuteChartPriceClient(
                builder,
                ProviderTestResilience.disabled(),
                Clock.fixed(Instant.parse("2026-07-07T18:45:00Z"), ZoneId.of("Asia/Seoul")));

        server.expect(requestTo(containsString("/v8/finance/chart/005930.KS")))
                .andRespond(withSuccess("""
                        {
                          "chart": {
                            "result": [
                              {
                                "timestamp": [1783382400, 1783382460, 1783405800],
                                "indicators": {
                                  "quote": [
                                    {
                                      "open": [81200, 81300, 82000],
                                      "high": [81300, 81400, 82100],
                                      "low": [81100, 81200, 81900],
                                      "close": [81250, 81350, 82050],
                                      "volume": [1000, 2000, 3000]
                                    }
                                  ]
                                }
                              }
                            ],
                            "error": null
                          }
                        }
                        """, APPLICATION_JSON));

        List<KisMinuteChartPrice> prices = client.findMinutePrices(
                stock("005930", "KOSPI"),
                LocalDate.of(2026, 7, 8),
                390);

        assertThat(prices).hasSize(3);
        assertThat(prices.get(0).bucketStart().toString()).isEqualTo("2026-07-07T09:00");
        assertThat(prices.get(2).bucketStart().toString()).isEqualTo("2026-07-07T15:30");
        server.verify();
    }

    @Test
    void findMinutePricesSkipsUnsupportedMarket() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        YahooStockMinuteChartPriceClient client = new YahooStockMinuteChartPriceClient(
                builder,
                ProviderTestResilience.disabled(),
                FIXED_CLOCK);

        List<KisMinuteChartPrice> prices = client.findMinutePrices(
                stock("005930", "KONEX"),
                LocalDate.of(2026, 7, 7),
                390);

        assertThat(prices).isEmpty();
        server.verify();
    }

    private static StockSummary stock(String stockCode, String market) {
        return new StockSummary(
                stockCode,
                "삼성전자",
                "Samsung Electronics",
                market,
                "KR7005930003",
                "00126380");
    }
}
