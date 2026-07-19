package com.hana.omniconnect.provider.market;

import java.time.LocalDate;
import java.util.List;

import com.hana.omniconnect.market.domain.StockSummary;

public interface ForeignOwnershipHistoricalSnapshotClient {

    List<ForeignOwnershipSnapshot> findSnapshots(StockSummary stock, LocalDate from, LocalDate to);
}
