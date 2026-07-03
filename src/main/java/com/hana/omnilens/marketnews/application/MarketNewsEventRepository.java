package com.hana.omnilens.marketnews.application;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

import com.hana.omnilens.marketnews.domain.MarketNewsEvent;

public interface MarketNewsEventRepository {

    MarketNewsEvent save(MarketNewsEvent event);

    MarketNewsEvent update(MarketNewsEvent event);

    Optional<MarketNewsEvent> findByNewsId(String newsId);

    Optional<MarketNewsEvent> findByDuplicateKey(String duplicateKey);

    List<MarketNewsEvent> findLatest(int limit);

    void recordView(String newsId, Instant viewedAt);

    List<MarketNewsEvent> findTrending(Instant since, int limit);
}
