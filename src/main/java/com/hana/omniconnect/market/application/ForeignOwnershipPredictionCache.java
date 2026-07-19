package com.hana.omniconnect.market.application;

import java.time.LocalDate;
import java.util.Optional;

import com.hana.omniconnect.market.domain.ForeignOwnershipPrediction;

public interface ForeignOwnershipPredictionCache {

    Optional<ForeignOwnershipPrediction> find(String stockCode, LocalDate baseDate);

    void put(String stockCode, ForeignOwnershipPrediction prediction);
}
