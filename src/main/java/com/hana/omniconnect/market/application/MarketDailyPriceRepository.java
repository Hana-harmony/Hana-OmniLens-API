package com.hana.omniconnect.market.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.hana.omniconnect.market.domain.MarketDailyPrice;

public interface MarketDailyPriceRepository {

    int upsertAll(List<MarketDailyPrice> prices);

    List<MarketDailyPrice> findByStockCode(String stockCode, LocalDate from, LocalDate to, int limit);

    Optional<MarketDailyPrice> findLatestBefore(String stockCode, LocalDate beforeDate);

    List<LocalDate> findTradingDates(LocalDate from, LocalDate to);
}
