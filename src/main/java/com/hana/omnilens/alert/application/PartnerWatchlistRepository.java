package com.hana.omnilens.alert.application;

import java.util.List;

public interface PartnerWatchlistRepository {

    List<PartnerWatchlist> findAll();

    List<String> findStockCodes(String partnerId);

    List<String> replace(String partnerId, List<String> stockCodes);
}
