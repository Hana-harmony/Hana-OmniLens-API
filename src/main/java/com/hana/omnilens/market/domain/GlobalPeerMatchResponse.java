package com.hana.omnilens.market.domain;

import java.math.BigDecimal;
import java.util.List;

public record GlobalPeerMatchResponse(
        String stockCode,
        String stockName,
        String stockNameEn,
        String headline,
        String summary,
        GlobalPeerMatch primaryPeer,
        List<GlobalPeerMatch> peers,
        BigDecimal confidenceScore,
        String confidenceLevel,
        String modelVersion,
        String source
) {
}
