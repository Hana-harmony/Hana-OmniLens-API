package com.hana.omnilens.market.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.hana.omnilens.config.ExchangeRateRefreshProperties;

@Component
public class ExchangeRateRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateRefreshScheduler.class);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final ExchangeRateProviderRefreshService refreshService;
    private final ExchangeRateRefreshProperties properties;
    private final Clock clock;

    @Autowired
    public ExchangeRateRefreshScheduler(
            ExchangeRateProviderRefreshService refreshService,
            ExchangeRateRefreshProperties properties) {
        this(refreshService, properties, Clock.system(KOREA_ZONE));
    }

    ExchangeRateRefreshScheduler(
            ExchangeRateProviderRefreshService refreshService,
            ExchangeRateRefreshProperties properties,
            Clock clock) {
        this.refreshService = refreshService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${omnilens.market.exchange-rate-refresh.fixed-delay-ms:300000}")
    public void refreshConfiguredCurrencies() {
        if (!properties.enabled() || properties.currencies().isEmpty()) {
            return;
        }

        LocalDate baseDate = LocalDate.now(clock).minusDays(properties.baseDateOffsetDays());
        for (String currency : properties.currencies()) {
            refreshCurrency(currency, baseDate);
        }
    }

    private void refreshCurrency(String currency, LocalDate baseDate) {
        try {
            refreshService.refresh(currency, baseDate);
        } catch (RuntimeException exception) {
            // 한 통화의 provider 장애가 전체 환율 refresh를 중단하지 않도록 격리한다.
            log.warn("Scheduled exchange rate refresh failed for currency={} baseDate={}", currency, baseDate, exception);
        }
    }
}
