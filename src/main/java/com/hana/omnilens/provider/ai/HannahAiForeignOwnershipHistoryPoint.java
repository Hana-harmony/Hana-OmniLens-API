package com.hana.omnilens.provider.ai;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HannahAiForeignOwnershipHistoryPoint(
        @JsonProperty("base_date") LocalDate baseDate,
        @JsonProperty("foreign_owned_quantity") long foreignOwnedQuantity,
        @JsonProperty("foreign_ownership_rate") BigDecimal foreignOwnershipRate,
        @JsonProperty("foreign_limit_quantity") long foreignLimitQuantity,
        @JsonProperty("foreign_limit_exhaustion_rate") BigDecimal foreignLimitExhaustionRate
) {
}
