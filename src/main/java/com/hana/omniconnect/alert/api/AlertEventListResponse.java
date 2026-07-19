package com.hana.omniconnect.alert.api;

import java.util.List;

import com.hana.omniconnect.alert.domain.AlertEvent;

public record AlertEventListResponse(
        String stockCode,
        List<AlertEvent> events,
        String nextCursor
) {

    public AlertEventListResponse(String stockCode, List<AlertEvent> events) {
        this(stockCode, events, null);
    }
}
