package com.hana.omnilens.marketnews.api;

import java.util.List;

import jakarta.validation.constraints.Size;

public record MarketNewsCollectRequest(
        @Size(max = 10) List<@Size(min = 1, max = 120) String> queries,
        int display
) {
}
