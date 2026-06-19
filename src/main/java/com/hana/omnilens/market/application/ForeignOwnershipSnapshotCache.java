package com.hana.omnilens.market.application;

import java.util.Optional;

import com.hana.omnilens.provider.market.ForeignOwnershipSnapshot;

public interface ForeignOwnershipSnapshotCache {

    Optional<ForeignOwnershipSnapshot> find(String stockCode);

    void put(ForeignOwnershipSnapshot snapshot);
}
