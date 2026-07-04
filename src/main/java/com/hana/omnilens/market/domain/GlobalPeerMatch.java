package com.hana.omnilens.market.domain;

import java.math.BigDecimal;
import java.util.List;

public record GlobalPeerMatch(
        int rank,
        String ticker,
        String companyName,
        String logoUrl,
        String exchange,
        String country,
        BigDecimal similarityScore,
        List<String> businessTags,
        String sector,
        String industry,
        String businessModel,
        String scaleBucket,
        Integer fiscalYear,
        BigDecimal marketCapUsd,
        BigDecimal revenueUsd,
        BigDecimal operatingIncomeUsd,
        BigDecimal netIncomeUsd,
        String financialDataSource,
        BigDecimal financialSimilarityScore,
        List<String> matchedFactors,
        String rationale
) {
    public GlobalPeerMatch(
            int rank,
            String ticker,
            String companyName,
            String exchange,
            String country,
            BigDecimal similarityScore,
            List<String> businessTags,
            String sector,
            String industry,
            String businessModel,
            String scaleBucket,
            Integer fiscalYear,
            BigDecimal marketCapUsd,
            BigDecimal revenueUsd,
            BigDecimal operatingIncomeUsd,
            BigDecimal netIncomeUsd,
            String financialDataSource,
            BigDecimal financialSimilarityScore,
            List<String> matchedFactors,
            String rationale
    ) {
        this(
                rank,
                ticker,
                companyName,
                StockLogoUrlResolver.usStockLogoUrl(ticker),
                exchange,
                country,
                similarityScore,
                businessTags,
                sector,
                industry,
                businessModel,
                scaleBucket,
                fiscalYear,
                marketCapUsd,
                revenueUsd,
                operatingIncomeUsd,
                netIncomeUsd,
                financialDataSource,
                financialSimilarityScore,
                matchedFactors,
                rationale);
    }

    public GlobalPeerMatch {
        logoUrl = logoUrl == null ? "" : logoUrl;
        businessTags = businessTags == null ? List.of() : List.copyOf(businessTags);
        matchedFactors = matchedFactors == null ? List.of() : List.copyOf(matchedFactors);
    }
}
