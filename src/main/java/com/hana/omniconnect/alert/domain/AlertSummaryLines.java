package com.hana.omniconnect.alert.domain;

public record AlertSummaryLines(
        String what,
        String why,
        String impact
) {
    public static AlertSummaryLines fromSummary(String summary) {
        return new AlertSummaryLines(summary, "", "");
    }
}
