package com.hana.omniconnect.market.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.hana.omniconnect.market.application.ForeignOwnershipRefreshResult;
import com.hana.omniconnect.provider.market.ForeignOwnershipSnapshot;

public record ForeignOwnershipRefreshResponse(
        String stockCode,
        LocalDate baseDate,
        boolean refreshed,
        Long foreignOwnedQuantity,
        BigDecimal foreignOwnershipRate,
        Long foreignLimitQuantity,
        BigDecimal foreignLimitExhaustionRate,
        String source
) {

    public static ForeignOwnershipRefreshResponse from(ForeignOwnershipRefreshResult result) {
        return result.snapshot()
                .map(snapshot -> fromSnapshot(result, snapshot))
                .orElseGet(() -> new ForeignOwnershipRefreshResponse(
                        result.stockCode(),
                        result.baseDate(),
                        false,
                        null,
                        null,
                        null,
                        null,
                        result.source()));
    }

    private static ForeignOwnershipRefreshResponse fromSnapshot(
            ForeignOwnershipRefreshResult result,
            ForeignOwnershipSnapshot snapshot) {
        return new ForeignOwnershipRefreshResponse(
                result.stockCode(),
                result.baseDate(),
                true,
                snapshot.foreignOwnedQuantity(),
                snapshot.foreignOwnershipRate(),
                snapshot.foreignLimitQuantity(),
                snapshot.foreignLimitExhaustionRate(),
                result.source());
    }
}
