package com.hana.omnilens.provider.ai;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HannahAiForeignOwnershipTrainingPoint(
        @JsonProperty("stock_code") String stockCode,
        @JsonProperty("base_date") LocalDate baseDate,
        @JsonProperty("foreign_owned_quantity") long foreignOwnedQuantity,
        @JsonProperty("foreign_limit_quantity") long foreignLimitQuantity
) {
}
