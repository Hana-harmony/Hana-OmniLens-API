package com.hana.omnilens.market.application;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.ExternalProviderProperties;
import com.hana.omnilens.config.KisRealtimeProperties;
import com.hana.omnilens.provider.ExternalProviderResiliencePolicy;
import com.hana.omnilens.provider.market.KisProviderSupport;
import com.hana.omnilens.provider.market.KisRealtimeApprovalKeyProvider;
import com.hana.omnilens.provider.market.KisRealtimeSubscriptionFrame;
import com.hana.omnilens.provider.market.KisRealtimeSubscriptionFrameFactory;
import com.hana.omnilens.provider.market.KisRealtimeSubscriptionType;
import com.hana.omnilens.provider.market.KisRealtimeTransaction;
import com.hana.omnilens.provider.market.KisRealtimeWebSocketConnection;
import com.hana.omnilens.provider.market.StandardKisRealtimeWebSocketConnection;

@Component
public class KisRealtimeIndexSessionRunner {

    private static final Logger log = LoggerFactory.getLogger(KisRealtimeIndexSessionRunner.class);

    private final KisRealtimeProperties kisRealtimeProperties;
    private final ExternalProviderProperties externalProviderProperties;
    private final KisRealtimeSubscriptionFrameFactory frameFactory;
    private final RealtimeMarketDataIngestionService ingestionService;
    private final Optional<ExternalProviderProperties.Kis> realIndexProvider;
    private final KisRealtimeApprovalKeyProvider approvalKeyProvider;
    private final KisRealtimeWebSocketConnection webSocketConnection;

    @Autowired
    public KisRealtimeIndexSessionRunner(
            KisRealtimeProperties kisRealtimeProperties,
            ExternalProviderProperties externalProviderProperties,
            KisRealtimeSubscriptionFrameFactory frameFactory,
            RealtimeMarketDataIngestionService ingestionService,
            ExternalProviderResiliencePolicy resiliencePolicy,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {
        this(
                kisRealtimeProperties,
                externalProviderProperties,
                frameFactory,
                ingestionService,
                KisProviderSupport.realIndexRealtimeProvider(externalProviderProperties)
                        .filter(provider -> !KisProviderSupport.isSameProvider(
                                provider,
                                externalProviderProperties.kis())),
                restClientBuilder,
                resiliencePolicy,
                objectMapper);
    }

    private KisRealtimeIndexSessionRunner(
            KisRealtimeProperties kisRealtimeProperties,
            ExternalProviderProperties externalProviderProperties,
            KisRealtimeSubscriptionFrameFactory frameFactory,
            RealtimeMarketDataIngestionService ingestionService,
            Optional<ExternalProviderProperties.Kis> realIndexProvider,
            RestClient.Builder restClientBuilder,
            ExternalProviderResiliencePolicy resiliencePolicy,
            ObjectMapper objectMapper) {
        this(
                kisRealtimeProperties,
                externalProviderProperties,
                frameFactory,
                ingestionService,
                realIndexProvider,
                realIndexProvider
                        .map(provider -> new KisRealtimeApprovalKeyProvider(
                                restClientBuilder,
                                provider,
                                resiliencePolicy))
                        .orElse(null),
                realIndexProvider
                        .map(provider -> new StandardKisRealtimeWebSocketConnection(objectMapper))
                        .orElse(null));
    }

    KisRealtimeIndexSessionRunner(
            KisRealtimeProperties kisRealtimeProperties,
            ExternalProviderProperties externalProviderProperties,
            KisRealtimeSubscriptionFrameFactory frameFactory,
            RealtimeMarketDataIngestionService ingestionService,
            Optional<ExternalProviderProperties.Kis> realIndexProvider,
            KisRealtimeApprovalKeyProvider approvalKeyProvider,
            KisRealtimeWebSocketConnection webSocketConnection) {
        this.kisRealtimeProperties = kisRealtimeProperties;
        this.externalProviderProperties = externalProviderProperties;
        this.frameFactory = frameFactory;
        this.ingestionService = ingestionService;
        this.realIndexProvider = realIndexProvider;
        this.approvalKeyProvider = approvalKeyProvider;
        this.webSocketConnection = webSocketConnection;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!kisRealtimeProperties.enabled() || kisRealtimeProperties.indexCodes().isEmpty()) {
            return;
        }
        if (KisProviderSupport.realIndexRealtimeProvider(externalProviderProperties)
                .filter(provider -> KisProviderSupport.isSameProvider(provider, externalProviderProperties.kis()))
                .isPresent()) {
            return;
        }
        if (realIndexProvider.isEmpty() || approvalKeyProvider == null || webSocketConnection == null) {
            log.warn("KIS realtime index session is disabled because real KIS websocket credential is not configured");
            return;
        }
        try {
            String approvalKey = approvalKeyProvider.approvalKey();
            List<KisRealtimeSubscriptionFrame> frames = indexFrames(approvalKey, kisRealtimeProperties.indexCodes());
            URI websocketUrl = realIndexProvider.orElseThrow().websocketUrl();
            log.info(
                    "Starting real KIS realtime index session indexCount={} subscriptionFrameCount={}",
                    kisRealtimeProperties.indexCodes().size(),
                    frames.size());
            webSocketConnection.connect(websocketUrl, frames, ingestionService::ingestKisMessage);
        } catch (RuntimeException exception) {
            // 지수 실시간 연결 실패가 API 기동 실패로 전파되지 않도록 격리한다.
            log.warn("Real KIS realtime index session start failed: {}", exception.toString());
        }
    }

    private List<KisRealtimeSubscriptionFrame> indexFrames(String approvalKey, List<String> indexCodes) {
        return indexCodes.stream()
                .map(indexCode -> frameFactory.create(
                        approvalKey,
                        KisRealtimeTransaction.INDEX_TRADE,
                        KisRealtimeSubscriptionType.SUBSCRIBE,
                        indexCode))
                .toList();
    }
}
