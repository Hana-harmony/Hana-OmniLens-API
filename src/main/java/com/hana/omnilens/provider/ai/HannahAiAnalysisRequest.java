package com.hana.omnilens.provider.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HannahAiAnalysisRequest(
        @JsonProperty("source_type") String sourceType,
        String title,
        String snippet,
        String content,
        @JsonProperty("image_urls") List<String> imageUrls,
        @JsonProperty("canonical_url") String canonicalUrl,
        @JsonProperty("content_hash") String contentHash,
        @JsonProperty("source_license_policy") String sourceLicensePolicy,
        @JsonProperty("original_url") String originalUrl,
        @JsonProperty("stock_universe") List<HannahAiStockCandidate> stockUniverse
) {
    public HannahAiAnalysisRequest(
            String sourceType,
            String title,
            String snippet,
            String originalUrl,
            List<HannahAiStockCandidate> stockUniverse) {
        this(sourceType, title, snippet, "", List.of(), originalUrl, "", "DISCOVERY_ONLY", originalUrl, stockUniverse);
    }
}
