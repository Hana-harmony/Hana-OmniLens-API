package com.hana.omnilens.market.application;

import java.time.LocalDate;
import java.util.List;

import com.hana.omnilens.market.domain.MarketIntradayPrice;
import com.hana.omnilens.market.domain.MarketIntradayRealtimeTick;

public interface MarketIntradayPriceRepository {

    int upsertAll(List<MarketIntradayPrice> prices);

    void recordRealtimeTick(MarketIntradayRealtimeTick tick);

    List<MarketIntradayPrice> findByStockCodeAndDate(String stockCode, LocalDate date, int limit);
}
