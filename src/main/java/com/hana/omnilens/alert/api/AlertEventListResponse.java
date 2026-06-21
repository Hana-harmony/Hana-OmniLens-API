package com.hana.omnilens.alert.api;

import java.util.List;

import com.hana.omnilens.alert.domain.AlertEvent;

public record AlertEventListResponse(
        String stockCode,
        List<AlertEvent> events
) {
}
