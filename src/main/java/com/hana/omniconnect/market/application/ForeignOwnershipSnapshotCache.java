package com.hana.omniconnect.market.application;

import java.util.Optional;

import com.hana.omniconnect.provider.market.ForeignOwnershipSnapshot;

public interface ForeignOwnershipSnapshotCache {

    Optional<ForeignOwnershipSnapshot> find(String stockCode);

    void put(ForeignOwnershipSnapshot snapshot);
}
