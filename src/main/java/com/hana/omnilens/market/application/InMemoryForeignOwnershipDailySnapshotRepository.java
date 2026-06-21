package com.hana.omnilens.market.application;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.hana.omnilens.market.domain.ForeignOwnershipDailySnapshot;

public class InMemoryForeignOwnershipDailySnapshotRepository implements ForeignOwnershipDailySnapshotRepository {

    private final Map<String, ForeignOwnershipDailySnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public int upsert(ForeignOwnershipDailySnapshot snapshot) {
        snapshots.put(key(snapshot.stockCode(), snapshot.baseDate()), snapshot);
        return 1;
    }

    @Override
    public List<ForeignOwnershipDailySnapshot> findRecent(String stockCode, LocalDate to, int limit) {
        return snapshots.values().stream()
                .filter(snapshot -> snapshot.stockCode().equals(stockCode))
                .filter(snapshot -> !snapshot.baseDate().isAfter(to))
                .sorted(Comparator.comparing(ForeignOwnershipDailySnapshot::baseDate).reversed())
                .limit(limit)
                .sorted(Comparator.comparing(ForeignOwnershipDailySnapshot::baseDate))
                .toList();
    }

    private String key(String stockCode, LocalDate baseDate) {
        return stockCode + ":" + baseDate;
    }
}
