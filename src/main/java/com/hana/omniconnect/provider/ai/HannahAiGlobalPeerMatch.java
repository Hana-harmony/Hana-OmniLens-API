package com.hana.omniconnect.provider.ai;

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
        @JsonProperty("fiscal_year") Integer fiscalYear,
        @JsonProperty("market_cap_usd") BigDecimal marketCapUsd,
        @JsonProperty("revenue_usd") BigDecimal revenueUsd,
        @JsonProperty("operating_income_usd") BigDecimal operatingIncomeUsd,
        @JsonProperty("net_income_usd") BigDecimal netIncomeUsd,
        @JsonProperty("financial_data_source") String financialDataSource,
        @JsonProperty("financial_similarity_score") BigDecimal financialSimilarityScore,
        @JsonProperty("matched_factors") List<String> matchedFactors,
        String rationale
) {
}
