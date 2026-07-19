package com.hana.omniconnect.provider.ai;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HannahAiKoreanFinancialTermExplainResponse(
        String term,
        @JsonProperty("normalized_term") String normalizedTerm,
        @JsonProperty("english_term") String englishTerm,
        String category,
        String definition,
        String explanation,
        String example,
        @JsonProperty("confidence_score") BigDecimal confidenceScore,
        @JsonProperty("confidence_level") String confidenceLevel,
        @JsonProperty("display_mode") String displayMode,
        String source,
        boolean cacheable,
        @JsonProperty("cache_ttl_seconds") int cacheTtlSeconds,
        List<HannahAiFinancialTermEvidence> evidence,
        @JsonProperty("quality_flags") List<String> qualityFlags,
        @JsonProperty("model_version") String modelVersion,
        @JsonProperty("generated_at") Instant generatedAt
) {
    public HannahAiKoreanFinancialTermExplainResponse {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        qualityFlags = qualityFlags == null ? List.of() : List.copyOf(qualityFlags);
    }
}
