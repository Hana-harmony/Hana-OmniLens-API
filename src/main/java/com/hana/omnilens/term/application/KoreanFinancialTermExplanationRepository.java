package com.hana.omnilens.term.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.hana.omnilens.term.domain.KoreanFinancialTermClickStat;
import com.hana.omnilens.term.domain.KoreanFinancialTermClickPoint;

public interface KoreanFinancialTermExplanationRepository {

    Optional<KoreanFinancialTermExplanationCacheEntry> findValidCache(String cacheKey, Instant now);

    void upsertCache(KoreanFinancialTermExplanationCacheEntry entry, Instant now);

    long recordClick(KoreanFinancialTermClickLog clickLog);

    List<KoreanFinancialTermClickStat> findTopStats(int limit);

    List<KoreanFinancialTermClickPoint> findClickSeries(String period);
}
