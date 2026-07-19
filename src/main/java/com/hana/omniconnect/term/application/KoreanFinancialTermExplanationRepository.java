package com.hana.omniconnect.term.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.hana.omniconnect.term.domain.KoreanFinancialTermClickStat;
import com.hana.omniconnect.term.domain.KoreanFinancialTermClickPoint;

public interface KoreanFinancialTermExplanationRepository {

    Optional<KoreanFinancialTermExplanationCacheEntry> findValidCache(String cacheKey, Instant now);

    void upsertCache(KoreanFinancialTermExplanationCacheEntry entry, Instant now);

    long recordClick(KoreanFinancialTermClickLog clickLog);

    List<KoreanFinancialTermClickStat> findTopStats(int limit);

    List<KoreanFinancialTermClickPoint> findClickSeries(String period);
}
