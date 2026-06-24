package com.hana.omnilens.provider.market;

import java.math.BigDecimal;
import java.time.LocalDate;

public record KisRealtimeTradeTick(
        String stockCode,
        String tradeTime,
        BigDecimal currentPriceKrw,
        BigDecimal changeRate,
        BigDecimal askPrice1Krw,
        BigDecimal bidPrice1Krw,
        long executionVolume,
        long accumulatedVolume,
        LocalDate businessDate,
        String marketSession,
        String viStatusCode,
        String singlePriceTradingCode,
        String tradingHaltCode
) {
    public static final String REGULAR_SESSION = "REGULAR";
    public static final String AFTER_HOURS_SESSION = "AFTER_HOURS";

    public KisRealtimeTradeTick(
            String stockCode,
            String tradeTime,
            BigDecimal currentPriceKrw,
            BigDecimal changeRate,
            BigDecimal askPrice1Krw,
            BigDecimal bidPrice1Krw,
            long executionVolume,
            long accumulatedVolume,
            LocalDate businessDate
    ) {
        this(
                stockCode,
                tradeTime,
                currentPriceKrw,
                changeRate,
                askPrice1Krw,
                bidPrice1Krw,
                executionVolume,
                accumulatedVolume,
                businessDate,
                REGULAR_SESSION,
                "",
                "",
                "");
    }

    public KisRealtimeTradeTick(
            String stockCode,
            String tradeTime,
            BigDecimal currentPriceKrw,
            BigDecimal changeRate,
            BigDecimal askPrice1Krw,
            BigDecimal bidPrice1Krw,
            long executionVolume,
            long accumulatedVolume,
            LocalDate businessDate,
            String viStatusCode,
            String singlePriceTradingCode,
            String tradingHaltCode
    ) {
        this(
                stockCode,
                tradeTime,
                currentPriceKrw,
                changeRate,
                askPrice1Krw,
                bidPrice1Krw,
                executionVolume,
                accumulatedVolume,
                businessDate,
                REGULAR_SESSION,
                viStatusCode,
                singlePriceTradingCode,
                tradingHaltCode);
    }

    public boolean afterHours() {
        return AFTER_HOURS_SESSION.equals(marketSession);
    }
}
