package com.hana.omnilens.provider.market;

import java.time.LocalDate;
import java.util.List;

import com.hana.omnilens.market.domain.StockSummary;

public interface ForeignOwnershipHistoricalSnapshotClient {

    List<ForeignOwnershipSnapshot> findSnapshots(StockSummary stock, LocalDate from, LocalDate to);
}
