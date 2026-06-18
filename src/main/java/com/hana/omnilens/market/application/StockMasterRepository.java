package com.hana.omnilens.market.application;

import java.util.List;
import java.util.Optional;

import com.hana.omnilens.market.domain.StockSummary;

public interface StockMasterRepository {

    Optional<StockSummary> findByCode(String stockCode);

    List<StockSummary> findAll(int limit);

    List<StockSummary> search(String query);
}
