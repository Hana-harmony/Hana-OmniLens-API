package com.hana.omniconnect.market.application;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.hana.omniconnect.provider.market.ForeignOwnershipSnapshot;

public class InMemoryForeignOwnershipSnapshotCache implements ForeignOwnershipSnapshotCache {

    private final Map<String, ForeignOwnershipSnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public Optional<ForeignOwnershipSnapshot> find(String stockCode) {
        return Optional.ofNullable(snapshots.get(stockCode));
    }

    @Override
    public void put(ForeignOwnershipSnapshot snapshot) {
        snapshots.put(snapshot.stockCode(), snapshot);
    }
}
