package com.hana.omnilens.provider.ai;

import java.util.Objects;

import com.hana.omnilens.market.domain.GlobalPeerContractPolicy;

public record HannahAiGlobalPeerComparison(
        String dimension,
        String description,
        HannahAiGlobalPeerMatch peer
) {
    public HannahAiGlobalPeerComparison {
        dimension = GlobalPeerContractPolicy.requireDimension(dimension);
        description = GlobalPeerContractPolicy.requireText("description", description);
        peer = Objects.requireNonNull(peer, "peer must not be null");
    }
}
