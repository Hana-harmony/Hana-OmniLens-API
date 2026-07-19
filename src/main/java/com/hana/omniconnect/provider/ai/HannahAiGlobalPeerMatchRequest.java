package com.hana.omniconnect.provider.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HannahAiGlobalPeerMatchRequest(
        @JsonProperty("stock_code") String stockCode,
        @JsonProperty("stock_name") String stockName,
        @JsonProperty("stock_name_en") String stockNameEn,
        String market,
        List<String> aliases,
        String description,
        @JsonProperty("peer_count") int peerCount
) {
}
