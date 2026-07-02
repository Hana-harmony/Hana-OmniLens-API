package com.hana.omnilens.provider.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class KisIndexMinuteChartPriceClientTest {

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
}
