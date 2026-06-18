package com.hana.omnilens.market.application;

import java.util.Optional;

import com.hana.omnilens.provider.market.KrxForeignOwnershipSnapshot;

public interface ForeignOwnershipSnapshotCache {

    Optional<KrxForeignOwnershipSnapshot> find(String stockCode);

    void put(KrxForeignOwnershipSnapshot snapshot);
}
