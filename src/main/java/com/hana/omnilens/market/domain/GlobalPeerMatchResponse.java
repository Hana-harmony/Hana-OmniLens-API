package com.hana.omnilens.market.domain;

import java.math.BigDecimal;
import java.util.List;

public record GlobalPeerMatchResponse(
        String stockCode,
        String stockName,
        String stockNameEn,
        String logoUrl,
        String headline,
        String summary,
        GlobalPeerMatch primaryPeer,
        List<GlobalPeerMatch> peers,
        List<GlobalPeerComparison> comparisons,
        List<GlobalPeerKeyStrength> keyStrengths,
        BigDecimal confidenceScore,
        String confidenceLevel,
        String modelVersion,
        String source
) {
    public GlobalPeerMatchResponse(
            String stockCode,
            String stockName,
            String stockNameEn,
            String headline,
            String summary,
            GlobalPeerMatch primaryPeer,
            List<GlobalPeerMatch> peers,
            List<GlobalPeerComparison> comparisons,
            List<GlobalPeerKeyStrength> keyStrengths,
            BigDecimal confidenceScore,
            String confidenceLevel,
            String modelVersion,
            String source
    ) {
        this(
                stockCode,
                stockName,
                stockNameEn,
                StockLogoUrlResolver.koreanStockLogoUrl(stockCode),
                headline,
                summary,
                primaryPeer,
                peers,
                comparisons,
                keyStrengths,
                confidenceScore,
                confidenceLevel,
                modelVersion,
                source);
    }

    public GlobalPeerMatchResponse {
        logoUrl = logoUrl == null ? "" : logoUrl;
        peers = peers == null ? List.of() : List.copyOf(peers);
        comparisons = GlobalPeerContractPolicy.copyRequiredList("comparisons", comparisons);
        keyStrengths = GlobalPeerContractPolicy.copyRequiredList("keyStrengths", keyStrengths);
        GlobalPeerContractPolicy.validateCardinality(source, comparisons, keyStrengths);
    }
}
