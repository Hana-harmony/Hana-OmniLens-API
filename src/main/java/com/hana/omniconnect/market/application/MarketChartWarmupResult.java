package com.hana.omniconnect.market.application;

import java.time.LocalDate;
import java.util.List;

public record MarketChartWarmupResult(
        LocalDate baseDate,
        LocalDate dailyFrom,
        LocalDate dailyTo,
        int requestedStockCount,
        int dailyPointCount,
        int intradayPointCount,
        String status,
        List<StockResult> stockResults
) {

    public record StockResult(
            String stockCode,
            int dailyPointCount,
            int intradayPointCount,
            String status,
            String errorMessage
    ) {
    }
}
