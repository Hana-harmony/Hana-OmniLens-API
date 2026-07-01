package com.hana.omnilens.marketnews.application;

import java.util.List;
import java.util.Optional;

import com.hana.omnilens.marketnews.domain.MarketNewsEvent;

public interface MarketNewsEventRepository {

    MarketNewsEvent save(MarketNewsEvent event);

    Optional<MarketNewsEvent> findByNewsId(String newsId);

    Optional<MarketNewsEvent> findByDuplicateKey(String duplicateKey);

    List<MarketNewsEvent> findLatest(int limit);
}
