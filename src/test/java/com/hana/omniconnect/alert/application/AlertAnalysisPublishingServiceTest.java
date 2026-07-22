package com.hana.omniconnect.alert.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.hana.omniconnect.alert.api.AlertAnalysisPublishRequest;
import com.hana.omniconnect.config.HannahAiProperties;
import com.hana.omniconnect.provider.ai.HannahAiAnalysisClient;
import com.hana.omniconnect.provider.ai.HannahAiAnalysisProvider;

class AlertAnalysisPublishingServiceTest {

    private final AlertEventRepository alertEventRepository = mock(AlertEventRepository.class);
    private final HannahAiProperties properties = new HannahAiProperties(
            URI.create("http://hannah-montana-ai:8000"),
            Duration.ofSeconds(2),
            Duration.ofMinutes(30),
            Duration.ofSeconds(90),
            "maintenance-secret",
            true,
            5);
    private final AlertAnalysisPublishingService service = new AlertAnalysisPublishingService(
            mock(HannahAiAnalysisClient.class),
            mock(AlertStreamingService.class),
            alertEventRepository,
            properties);

    @Test
    void collectionUsesOpenAiOnlyUntilInitialTargetIsFilled() {
        AlertAnalysisPublishRequest request = request("NEWS");
        when(alertEventRepository.countByPartnerStockAndSourceType("partner", "005930", "NEWS"))
                .thenReturn(4, 5);

        assertThat(service.collectionProvider(request))
                .isEqualTo(HannahAiAnalysisProvider.OPENAI_INITIAL_BACKFILL);
        assertThat(service.collectionProvider(request))
                .isEqualTo(HannahAiAnalysisProvider.QWEN);
    }

    @Test
    void collectionKeepsNonNewsAndDisclosureTrafficOnQwen() {
        assertThat(service.collectionProvider(request("MARKET")))
                .isEqualTo(HannahAiAnalysisProvider.QWEN);
    }

    private static AlertAnalysisPublishRequest request(String sourceType) {
        return new AlertAnalysisPublishRequest(
                "partner",
                sourceType,
                "삼성전자 실적",
                "요약",
                "본문",
                List.of(),
                "",
                "",
                "licensed",
                "https://example.com/news/1",
                Instant.parse("2026-07-23T00:00:00Z"),
                List.of(new AlertAnalysisPublishRequest.StockCandidateRequest(
                        "005930", "삼성전자", "Samsung Electronics", List.of("삼성전자"))));
    }
}
