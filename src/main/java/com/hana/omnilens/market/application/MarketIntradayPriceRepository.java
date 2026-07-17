package com.hana.omnilens.market.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.hana.omnilens.market.domain.MarketIntradayPrice;
import com.hana.omnilens.market.domain.MarketIntradayRealtimeTick;

public interface MarketIntradayPriceRepository {

    int upsertAll(List<MarketIntradayPrice> prices);

    void recordRealtimeTick(MarketIntradayRealtimeTick tick);

    Optional<MarketIntradayPrice> findLatestByStockCodeAndDate(String stockCode, LocalDate date);

    Optional<MarketIntradayPrice> findLatestByStockCodeAtOrBefore(String stockCode, LocalDate date);

    long sumTradingVolumeByStockCodeAndDate(String stockCode, LocalDate date);

    List<MarketIntradayPrice> findByStockCodeAndDate(String stockCode, LocalDate date, int limit);
}
