package com.hana.omnilens.market.application;

import java.time.LocalDate;
import java.util.List;

import com.hana.omnilens.market.domain.MarketDailyPrice;

public interface MarketDailyPriceRepository {

    int upsertAll(List<MarketDailyPrice> prices);

    List<MarketDailyPrice> findByStockCode(String stockCode, LocalDate from, LocalDate to, int limit);

    List<LocalDate> findTradingDates(LocalDate from, LocalDate to);
}
