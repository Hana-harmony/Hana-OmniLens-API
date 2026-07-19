package com.hana.omniconnect.provider.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HannahAiFinancialTermEvidence(
        String title,
        String snippet,
        String url,
        @JsonProperty("source_type") String sourceType
) {
}
