package com.hana.omniconnect.market.application;

import java.time.LocalDate;
import java.util.List;

import com.hana.omniconnect.market.domain.ForeignOwnershipDailySnapshot;

public interface ForeignOwnershipDailySnapshotRepository {

    int upsert(ForeignOwnershipDailySnapshot snapshot);

    List<ForeignOwnershipDailySnapshot> findRecent(String stockCode, LocalDate to, int limit);

    List<ForeignOwnershipDailySnapshot> findAllByStockCodes(List<String> stockCodes);

    List<LocalDate> findBaseDates(String stockCode, LocalDate from, LocalDate to);
}
