package com.hana.omniconnect.alert.application;

import java.util.List;
import java.util.Optional;

import com.hana.omniconnect.alert.domain.AlertEvent;
import com.hana.omniconnect.common.api.KeysetCursor;

public interface AlertEventRepository {

    AlertEvent save(AlertEvent event);

    Optional<AlertEvent> findByAlertId(String alertId);

    Optional<AlertEvent> findBySourceIdentity(
            String partnerId,
            String stockCode,
            String sourceType,
            String originalUrl);

    Optional<AlertEvent> findByDuplicateIdentity(
            String partnerId,
            String stockCode,
            String sourceType,
            String duplicateKey);

    int countByPartnerStockAndSourceType(
            String partnerId,
            String stockCode,
            String sourceType);

    Optional<AlertEvent> findLatestByPartnerStockAndSourceType(
            String partnerId,
            String stockCode,
            String sourceType);

    List<AlertEvent> findByStockCode(String stockCode, int limit);

    List<AlertEvent> findByStockCodeBefore(String stockCode, KeysetCursor cursor, int limit);

    List<AlertEvent> findByStockCodeAndSourceType(String stockCode, String sourceType, int limit);

    List<AlertEvent> findSummaryQualityIssues(int limit);
}
