package com.hana.omniconnect.provider.ai;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hana.omniconnect.market.domain.GlobalPeerContractPolicy;

public record HannahAiGlobalPeerMatchResponse(
        @JsonProperty("stock_code") String stockCode,
        @JsonProperty("stock_name") String stockName,
        @JsonProperty("stock_name_en") String stockNameEn,
        @JsonProperty("source_sector") String sourceSector,
        @JsonProperty("source_industry") String sourceIndustry,
        @JsonProperty("source_business_model") String sourceBusinessModel,
        @JsonProperty("source_business_tags") List<String> sourceBusinessTags,
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
        sourceSector = GlobalPeerContractPolicy.requireText("sourceSector", sourceSector);
        sourceIndustry = GlobalPeerContractPolicy.requireText("sourceIndustry", sourceIndustry);
        sourceBusinessModel = GlobalPeerContractPolicy.requireText(
                "sourceBusinessModel", sourceBusinessModel);
        sourceBusinessTags = GlobalPeerContractPolicy.copyRequiredList(
                "sourceBusinessTags", sourceBusinessTags);
        peers = GlobalPeerContractPolicy.copyRequiredList("peers", peers);
        comparisons = GlobalPeerContractPolicy.copyRequiredList("comparisons", comparisons);
        keyStrengths = GlobalPeerContractPolicy.copyRequiredList("keyStrengths", keyStrengths);
        GlobalPeerContractPolicy.validateStrictCardinality(comparisons, keyStrengths);
        GlobalPeerContractPolicy.validatePeerDomain(
                sourceSector,
                sourceIndustry,
                sourceBusinessTags,
                primaryPeer.sector(),
                primaryPeer.industry(),
                primaryPeer.businessTags());
        for (HannahAiGlobalPeerMatch peer : peers) {
            GlobalPeerContractPolicy.validatePeerDomain(
                    sourceSector,
                    sourceIndustry,
                    sourceBusinessTags,
                    peer.sector(),
                    peer.industry(),
                    peer.businessTags());
        }
        for (HannahAiGlobalPeerComparison comparison : comparisons) {
            GlobalPeerContractPolicy.validatePeerDomain(
                    sourceSector,
                    sourceIndustry,
                    sourceBusinessTags,
                    comparison.peer().sector(),
                    comparison.peer().industry(),
                    comparison.peer().businessTags());
        }
    }
}
