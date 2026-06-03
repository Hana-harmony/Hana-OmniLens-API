package com.hana.omnilens.market.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record ExchangeRateUpdateRequest(
        @NotNull
        @DecimalMin("0.000001")
        BigDecimal fxRate
) {
}
