package com.hana.omnilens.provider.ai;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HannahAiGlobalPeerMatch(
        int rank,
        String ticker,
        @JsonProperty("company_name") String companyName,
        String exchange,
        String country,
        @JsonProperty("similarity_score") BigDecimal similarityScore,
        @JsonProperty("business_tags") List<String> businessTags,
        String sector,
        String industry,
        @JsonProperty("business_model") String businessModel,
        @JsonProperty("scale_bucket") String scaleBucket,
        @JsonProperty("matched_factors") List<String> matchedFactors,
        String rationale
) {
}
