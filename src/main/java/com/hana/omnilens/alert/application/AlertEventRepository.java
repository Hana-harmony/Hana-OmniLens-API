package com.hana.omnilens.alert.application;

import java.util.List;
import java.util.Optional;

import com.hana.omnilens.alert.domain.AlertEvent;

public interface AlertEventRepository {

    AlertEvent save(AlertEvent event);

    Optional<AlertEvent> findByAlertId(String alertId);

    List<AlertEvent> findByStockCode(String stockCode, int limit);

    List<AlertEvent> findSummaryQualityIssues(int limit);
}
