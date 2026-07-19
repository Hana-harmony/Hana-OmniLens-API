package com.hana.omniconnect.provider.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hana.omniconnect.market.domain.GlobalPeerContractPolicy;

public record HannahAiGlobalPeerKeyStrength(
        String title,
        String description,
        @JsonProperty("icon_key") String iconKey
) {
    public HannahAiGlobalPeerKeyStrength {
        title = GlobalPeerContractPolicy.requireText("title", title);
        description = GlobalPeerContractPolicy.requireText("description", description);
        iconKey = GlobalPeerContractPolicy.requireIconKey(iconKey);
    }
}
