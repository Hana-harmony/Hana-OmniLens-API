package com.hana.omnilens.provider.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ProviderExchangeRateSnapshot(
        String localCurrency,
        BigDecimal krwToLocalRate,
        LocalDate baseDate,
        Instant providerTimestamp,
        String source
) {
}
