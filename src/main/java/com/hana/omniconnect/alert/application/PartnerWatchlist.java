package com.hana.omniconnect.alert.application;

import java.util.List;

public record PartnerWatchlist(
        String partnerId,
        List<String> stockCodes
) {

    public PartnerWatchlist {
        stockCodes = stockCodes == null ? List.of() : List.copyOf(stockCodes);
    }
}
