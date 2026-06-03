package com.hana.omnilens.provider.market;

import java.math.BigDecimal;
import java.time.LocalDate;

public record KoreaEximExchangeRateSnapshot(
        String localCurrency,
        BigDecimal krwToLocalRate,
        LocalDate baseDate
) {
}
