package com.hana.omnilens.market.domain;

import java.math.BigDecimal;
import java.util.List;

public record GlobalPeerMatch(
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
        List<String> matchedFactors,
        String rationale
) {
}
