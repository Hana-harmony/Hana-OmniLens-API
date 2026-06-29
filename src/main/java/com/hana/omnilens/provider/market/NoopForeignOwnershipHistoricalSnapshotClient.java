package com.hana.omnilens.provider.market;

import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.hana.omnilens.market.domain.StockSummary;

@Component
@ConditionalOnProperty(
        prefix = "omnilens.providers.krx",
        name = "scraping-enabled",
        havingValue = "false",
        matchIfMissing = true)
public class NoopForeignOwnershipHistoricalSnapshotClient implements ForeignOwnershipHistoricalSnapshotClient {

    @Override
    public List<ForeignOwnershipSnapshot> findSnapshots(StockSummary stock, LocalDate from, LocalDate to) {
        return List.of();
    }
}
