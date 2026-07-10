package com.hana.omnilens.market.domain;

import java.util.Objects;

public record GlobalPeerComparison(
        String dimension,
        String description,
        GlobalPeerMatch peer
) {
    public GlobalPeerComparison {
        dimension = GlobalPeerContractPolicy.requireDimension(dimension);
        description = GlobalPeerContractPolicy.requireText("description", description);
        peer = Objects.requireNonNull(peer, "peer must not be null");
    }
}
