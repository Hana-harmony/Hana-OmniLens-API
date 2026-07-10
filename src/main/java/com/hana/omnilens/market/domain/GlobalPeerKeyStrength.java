package com.hana.omnilens.market.domain;

public record GlobalPeerKeyStrength(
        String title,
        String description,
        String iconKey
) {
    public GlobalPeerKeyStrength {
        title = GlobalPeerContractPolicy.requireText("title", title);
        description = GlobalPeerContractPolicy.requireText("description", description);
        iconKey = GlobalPeerContractPolicy.requireIconKey(iconKey);
    }
}
