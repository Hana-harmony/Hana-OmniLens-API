package com.hana.omnilens.market.domain;

public record StockSummary(
        String stockCode,
        String stockName,
        String stockNameEn,
        String logoUrl,
        String market,
        String isinCode,
        String dartCorpCode
) {
    public StockSummary(
            String stockCode,
            String stockName,
            String stockNameEn,
            String market,
            String isinCode,
            String dartCorpCode
    ) {
        this(
                stockCode,
                stockName,
                stockNameEn,
                StockLogoUrlResolver.koreanStockLogoUrl(stockCode),
                market,
                isinCode,
                dartCorpCode);
    }

    public StockSummary {
        logoUrl = logoUrl == null ? "" : logoUrl;
    }
}
