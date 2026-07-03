package com.hana.omnilens.alert.api;

import java.util.List;

import com.hana.omnilens.alert.domain.AlertEvent;

public record AlertEventReprocessResponse(
        int eventCount,
        List<AlertEvent> events
) {

    public AlertEventReprocessResponse {
        events = events == null ? List.of() : List.copyOf(events);
    }
}
