package com.hana.omnilens.provider.market;

import java.util.List;
import java.util.Locale;

public record KisRealtimeMarketStatus(
        String stockCode,
        String tradingHaltCode,
        String tradingHaltReason,
        String marketOperationCode,
        String expectedMarketOperationCode,
        String marketTradingMethodCode,
        String dividendApplicationCode,
        String issueStatusCode,
        String viCode,
        String afterHoursViCode,
        String exchangeCode
) {
    private static final List<String> INACTIVE_CODES = List.of(
            "", "0", "00", "N", "NO", "NORMAL", "NONE", "FALSE", "INACTIVE", "OFF");

    public boolean tradingHalted() {
        return active(tradingHaltCode);
    }

    public boolean viActive() {
        return active(viCode) || active(afterHoursViCode);
    }

    public boolean singlePriceTrading() {
        return active(marketTradingMethodCode);
    }

    public boolean circuitBreakerActive() {
        if (!tradingHalted()) {
            return false;
        }
        String reason = tradingHaltReason == null ? "" : tradingHaltReason
                .replace(" ", "")
                .toUpperCase(Locale.ROOT);
        return reason.contains("서킷") || reason.contains("CIRCUITBREAKER") || reason.contains("CB발동");
    }

    private boolean active(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return !INACTIVE_CODES.contains(normalized);
    }
}
