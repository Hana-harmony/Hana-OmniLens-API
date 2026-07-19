package com.hana.omniconnect.alert.api;

import java.util.List;

import com.hana.omniconnect.alert.domain.AlertEvent;

public record AlertEventReprocessResponse(
        int eventCount,
        List<AlertEvent> events
) {

    public AlertEventReprocessResponse {
        events = events == null ? List.of() : List.copyOf(events);
    }
}
