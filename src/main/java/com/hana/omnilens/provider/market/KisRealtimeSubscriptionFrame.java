package com.hana.omnilens.provider.market;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisRealtimeSubscriptionFrame(
        Header header,
        Body body
) {
    public record Header(
            @JsonProperty("approval_key")
            String approvalKey,
            @JsonProperty("tr_type")
            String trType,
            String custtype,
            @JsonProperty("content-type")
            String contentType
    ) {
    }

    public record Body(Input input) {
    }

    public record Input(
            @JsonProperty("tr_id")
            String trId,
            @JsonProperty("tr_key")
            String trKey
    ) {
    }
}
