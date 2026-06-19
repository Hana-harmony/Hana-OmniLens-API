package com.hana.omnilens.market.application;

import java.time.LocalDate;
import java.util.Optional;

import com.hana.omnilens.provider.market.KrxForeignOwnershipSnapshot;

public record ForeignOwnershipRefreshResult(
        String stockCode,
        LocalDate baseDate,
        Optional<KrxForeignOwnershipSnapshot> snapshot,
        String source
) {

    public boolean refreshed() {
        return snapshot.isPresent();
    }
}
