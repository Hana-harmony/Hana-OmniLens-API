package com.hana.omnilens.market.domain;

public record StockSummary(
        String stockCode,
        String stockName,
        String stockNameEn,
        String market,
        String dartCorpCode
) {
}
