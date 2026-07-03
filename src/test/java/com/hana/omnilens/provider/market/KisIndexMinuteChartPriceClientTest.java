package com.hana.omnilens.provider.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.ExternalProviderProperties;
import com.hana.omnilens.provider.ProviderTestResilience;

class KisIndexMinuteChartPriceClientTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-03T01:46:00Z"),
            ZoneId.of("Asia/Seoul"));

    @Test
    void findMinutePricesSkipsWhenOnlyVirtualTradingProviderHasNoCredential() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ExternalProviderProperties properties = properties(vtsKisWithoutCredential(), null);
        KisIndexMinuteChartPriceClient client = new KisIndexMinuteChartPriceClient(
                builder,
                properties,
                new KisAccessTokenProvider(builder, properties, ProviderTestResilience.disabled()),
                ProviderTestResilience.disabled(),
                FIXED_CLOCK);

        List<KisIndexMinuteChartPrice> prices = client.findMinutePrices("0001", LocalDate.of(2026, 7, 3), 390);

        assertThat(prices).isEmpty();
        server.verify();
    }

    @Test
    void selectAvailableTradingDateUsesRequestedDateWhenTodayDataExists() {
        LocalDate selectedDate = KisIndexMinuteChartPriceClient.selectAvailableTradingDate(
                LocalDate.of(2026, 7, 3),
                List.of(
                        price(LocalDateTime.of(2026, 7, 2, 15, 30)),
                        price(LocalDateTime.of(2026, 7, 3, 9, 1))));

        assertThat(selectedDate).isEqualTo(LocalDate.of(2026, 7, 3));
    }

    @Test
    void selectAvailableTradingDateUsesPreviousTradingDateWhenMarketHasNotOpened() {
        LocalDate selectedDate = KisIndexMinuteChartPriceClient.selectAvailableTradingDate(
                LocalDate.of(2026, 7, 3),
                List.of(
                        price(LocalDateTime.of(2026, 7, 2, 15, 29)),
                        price(LocalDateTime.of(2026, 7, 2, 15, 30))));

        assertThat(selectedDate).isEqualTo(LocalDate.of(2026, 7, 2));
    }

    private KisIndexMinuteChartPrice price(LocalDateTime bucketStart) {
        return new KisIndexMinuteChartPrice(
                bucketStart,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                1L,
                BigDecimal.ONE);
    }

    private ExternalProviderProperties properties(
            ExternalProviderProperties.Kis kis,
            ExternalProviderProperties.Kis realKis) {
        return new ExternalProviderProperties(
                null,
                null,
                null,
                null,
                kis,
                realKis,
                null);
    }

    private ExternalProviderProperties.Kis vtsKisWithoutCredential() {
        return new ExternalProviderProperties.Kis(
                URI.create("https://openapivts.koreainvestment.com:29443"),
                URI.create("ws://ops.koreainvestment.com:31000"),
                "00000000",
                "",
                "",
                "",
                "");
    }
}
