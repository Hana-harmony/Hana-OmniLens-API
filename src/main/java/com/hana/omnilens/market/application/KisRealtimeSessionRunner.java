package com.hana.omnilens.market.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.hana.omnilens.config.ExternalProviderProperties;
import com.hana.omnilens.config.KisRealtimeProperties;
import com.hana.omnilens.provider.market.KisRealtimeSubscriptionFrame;
import com.hana.omnilens.provider.market.KisRealtimeSubscriptionFrameFactory;
import com.hana.omnilens.provider.market.KisRealtimeSubscriptionType;
import com.hana.omnilens.provider.market.KisRealtimeTransaction;
import com.hana.omnilens.provider.market.KisRealtimeWebSocketConnection;
import com.hana.omnilens.provider.market.KisRealtimeApprovalKeyProvider;

@Component
public class KisRealtimeSessionRunner {

    private static final Logger log = LoggerFactory.getLogger(KisRealtimeSessionRunner.class);

    private final KisRealtimeProperties kisRealtimeProperties;
    private final ExternalProviderProperties externalProviderProperties;
    private final KisRealtimeSubscriptionFrameFactory frameFactory;
    private final KisRealtimeWebSocketConnection webSocketConnection;
    private final RealtimeMarketDataIngestionService ingestionService;
    private final KisRealtimeApprovalKeyProvider approvalKeyProvider;

    public KisRealtimeSessionRunner(
            KisRealtimeProperties kisRealtimeProperties,
            ExternalProviderProperties externalProviderProperties,
            KisRealtimeSubscriptionFrameFactory frameFactory,
            KisRealtimeWebSocketConnection webSocketConnection,
            RealtimeMarketDataIngestionService ingestionService,
            KisRealtimeApprovalKeyProvider approvalKeyProvider) {
        this.kisRealtimeProperties = kisRealtimeProperties;
        this.externalProviderProperties = externalProviderProperties;
        this.frameFactory = frameFactory;
        this.webSocketConnection = webSocketConnection;
        this.ingestionService = ingestionService;
        this.approvalKeyProvider = approvalKeyProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!kisRealtimeProperties.enabled()) {
            log.info("KIS realtime session runner is disabled");
            return;
        }
        if (kisRealtimeProperties.stockCodes().isEmpty()) {
            log.warn("KIS realtime session runner is enabled but stock code list is empty");
            return;
        }
        List<KisRealtimeSubscriptionFrame> frames = subscriptionFrames();
        log.info(
                "Starting KIS realtime session for stockCodes={} subscriptionFrameCount={}",
                kisRealtimeProperties.stockCodes(),
                frames.size());
        webSocketConnection.connect(
                externalProviderProperties.kis().websocketUrl(),
                frames,
                ingestionService::ingestKisMessage);
    }

    List<KisRealtimeSubscriptionFrame> subscriptionFrames() {
        String approvalKey = approvalKeyProvider.approvalKey();
        List<KisRealtimeSubscriptionFrame> frames = new ArrayList<>();
        for (String stockCode : kisRealtimeProperties.stockCodes()) {
            frames.add(frameFactory.create(
                    approvalKey,
                    KisRealtimeTransaction.TRADE,
                    KisRealtimeSubscriptionType.SUBSCRIBE,
                    stockCode));
            frames.add(frameFactory.create(
                    approvalKey,
                    KisRealtimeTransaction.ORDERBOOK,
                    KisRealtimeSubscriptionType.SUBSCRIBE,
                    stockCode));
        }
        return List.copyOf(frames);
    }
}
