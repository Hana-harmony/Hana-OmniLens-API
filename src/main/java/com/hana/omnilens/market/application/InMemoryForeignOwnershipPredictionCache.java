package com.hana.omnilens.market.application;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.hana.omnilens.market.domain.ForeignOwnershipPrediction;

public class InMemoryForeignOwnershipPredictionCache implements ForeignOwnershipPredictionCache {

    private final Map<String, ForeignOwnershipPrediction> predictions = new ConcurrentHashMap<>();

    @Override
    public Optional<ForeignOwnershipPrediction> find(String stockCode, LocalDate baseDate) {
        return Optional.ofNullable(predictions.get(key(stockCode, baseDate)));
    }

    @Override
    public void put(String stockCode, ForeignOwnershipPrediction prediction) {
        if (prediction.baseDate() == null) {
            return;
        }
        predictions.put(key(stockCode, prediction.baseDate()), prediction);
    }

    private String key(String stockCode, LocalDate baseDate) {
        return stockCode + ":" + baseDate;
    }
}
