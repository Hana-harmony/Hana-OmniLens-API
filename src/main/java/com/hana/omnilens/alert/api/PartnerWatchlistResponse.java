package com.hana.omnilens.alert.api;

import java.util.List;

public record PartnerWatchlistResponse(
        String partnerId,
        List<String> stockCodes
) {

    public PartnerWatchlistResponse {
        stockCodes = stockCodes == null ? List.of() : List.copyOf(stockCodes);
    }
}
