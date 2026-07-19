package com.hana.omniconnect.market.application;

import java.time.LocalDate;
import java.util.Optional;

import com.hana.omniconnect.provider.market.ForeignOwnershipSnapshot;

public record ForeignOwnershipRefreshResult(
        String stockCode,
        LocalDate baseDate,
        Optional<ForeignOwnershipSnapshot> snapshot,
        String source
) {

    public boolean refreshed() {
        return snapshot.isPresent();
    }
}
