package com.hana.omnilens.market.application;

import java.time.LocalDate;
import java.util.List;

import com.hana.omnilens.market.domain.ForeignOwnershipDailySnapshot;

public interface ForeignOwnershipDailySnapshotRepository {

    int upsert(ForeignOwnershipDailySnapshot snapshot);

    List<ForeignOwnershipDailySnapshot> findRecent(String stockCode, LocalDate to, int limit);
}
