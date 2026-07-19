package com.hana.omniconnect.provider.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HannahAiKoreanFinancialTermExplainRequest(
        String term,
        String locale,
        @JsonProperty("source_type") String sourceType,
        String title,
        String context,
        @JsonProperty("stock_code") String stockCode,
        @JsonProperty("stock_name") String stockName,
        @JsonProperty("article_id") String articleId,
        @JsonProperty("article_url") String articleUrl
) {
}
