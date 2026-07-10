package com.hana.omnilens.provider.ai;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hana.omnilens.market.domain.GlobalPeerContractPolicy;

public record HannahAiGlobalPeerMatchResponse(
        @JsonProperty("stock_code") String stockCode,
        @JsonProperty("stock_name") String stockName,
        @JsonProperty("stock_name_en") String stockNameEn,
        String headline,
        String summary,
        @JsonProperty("primary_peer") HannahAiGlobalPeerMatch primaryPeer,
        List<HannahAiGlobalPeerMatch> peers,
        List<HannahAiGlobalPeerComparison> comparisons,
        @JsonProperty("key_strengths") List<HannahAiGlobalPeerKeyStrength> keyStrengths,
        @JsonProperty("confidence_score") BigDecimal confidenceScore,
        @JsonProperty("confidence_level") String confidenceLevel,
        @JsonProperty("model_version") String modelVersion,
        String source
) {
    public HannahAiGlobalPeerMatchResponse {
        peers = GlobalPeerContractPolicy.copyRequiredList("peers", peers);
        comparisons = GlobalPeerContractPolicy.copyRequiredList("comparisons", comparisons);
        keyStrengths = GlobalPeerContractPolicy.copyRequiredList("keyStrengths", keyStrengths);
        GlobalPeerContractPolicy.validateStrictCardinality(comparisons, keyStrengths);
    }
}
