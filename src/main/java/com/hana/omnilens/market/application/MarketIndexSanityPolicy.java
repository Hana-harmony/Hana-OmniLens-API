package com.hana.omnilens.market.application;

import java.math.BigDecimal;

final class MarketIndexSanityPolicy {

    private MarketIndexSanityPolicy() {
    }

    static boolean isPlausibleCurrentValue(String indexCode, BigDecimal value) {
        if (value == null) {
            return false;
        }
        return switch (indexCode) {
            case "0001" -> isBetween(value, "1000", "6000");
            case "1001" -> isBetween(value, "300", "2000");
            case "2001" -> isBetween(value, "100", "700");
            default -> value.signum() > 0;
        };
    }

    private static boolean isBetween(BigDecimal value, String lowerInclusive, String upperInclusive) {
        return value.compareTo(new BigDecimal(lowerInclusive)) >= 0
                && value.compareTo(new BigDecimal(upperInclusive)) <= 0;
    }
}
