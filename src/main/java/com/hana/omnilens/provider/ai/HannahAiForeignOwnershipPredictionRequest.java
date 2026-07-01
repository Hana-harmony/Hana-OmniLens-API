package com.hana.omnilens.provider.ai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;

public record HannahAiForeignOwnershipPredictionRequest(
        @JsonProperty("stock_code") String stockCode,
        String side,
        long quantity,
        @JsonProperty("foreign_owned_quantity") long foreignOwnedQuantity,
        @JsonProperty("foreign_ownership_rate") BigDecimal foreignOwnershipRate,
        @JsonProperty("foreign_limit_quantity") long foreignLimitQuantity,
        @JsonProperty("foreign_limit_exhaustion_rate") BigDecimal foreignLimitExhaustionRate,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @JsonProperty("base_date") LocalDate baseDate,
        @JsonProperty("observed_intraday_volume") long observedIntradayVolume,
        List<HannahAiForeignOwnershipHistoryPoint> history
) {
}
