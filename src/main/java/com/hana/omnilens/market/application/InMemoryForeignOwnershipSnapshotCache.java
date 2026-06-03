package com.hana.omnilens.market.application;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.hana.omnilens.provider.market.KrxForeignOwnershipSnapshot;

public class InMemoryForeignOwnershipSnapshotCache implements ForeignOwnershipSnapshotCache {

    private final Map<String, KrxForeignOwnershipSnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public Optional<KrxForeignOwnershipSnapshot> find(String stockCode) {
        return Optional.ofNullable(snapshots.get(stockCode));
    }

    @Override
    public void put(KrxForeignOwnershipSnapshot snapshot) {
        snapshots.put(snapshot.stockCode(), snapshot);
    }
}
