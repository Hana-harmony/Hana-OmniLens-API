package com.hana.omniconnect.market.application;

import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.hana.omniconnect.config.ExternalProviderProperties;
import com.hana.omniconnect.config.KisRealtimeProperties;
import com.hana.omniconnect.provider.ExternalProviderResiliencePolicy;
import com.hana.omniconnect.provider.market.KisProviderSupport;
import com.hana.omniconnect.provider.market.KisRealtimeApprovalKeyProvider;
import com.hana.omniconnect.provider.market.KisRealtimeSubscriptionFrame;
import com.hana.omniconnect.provider.market.KisRealtimeSubscriptionFrameFactory;
import com.hana.omniconnect.provider.market.KisRealtimeSubscriptionType;
import com.hana.omniconnect.provider.market.KisRealtimeTransaction;
import com.hana.omniconnect.provider.market.KisRealtimeWebSocketConnection;
import com.hana.omniconnect.provider.market.StandardKisRealtimeWebSocketConnection;

@Component
public class KisRealtimeIndexSessionRunner {

    private static final Logger log = LoggerFactory.getLogger(KisRealtimeIndexSessionRunner.class);
    private static final int MAX_STARTUP_RETRY_DELAY_SECONDS = 30;
    private static final int STARTUP_RETRY_JITTER_BOUND_SECONDS = 5;

    private final KisRealtimeProperties kisRealtimeProperties;
    private final ExternalProviderProperties externalProviderProperties;
    private final KisRealtimeSubscriptionFrameFactory frameFactory;
    private final RealtimeMarketDataIngestionService ingestionService;
    private final Optional<ExternalProviderProperties.Kis> realIndexProvider;
    private final KisRealtimeApprovalKeyProvider approvalKeyProvider;
    private final KisRealtimeWebSocketConnection webSocketConnection;
    private final TaskScheduler taskScheduler;
    private final Clock clock;
    private final AtomicBoolean startupRetryScheduled = new AtomicBoolean(false);

    @Autowired
    public KisRealtimeIndexSessionRunner(
            KisRealtimeProperties kisRealtimeProperties,
            ExternalProviderProperties externalProviderProperties,
            KisRealtimeSubscriptionFrameFactory frameFactory,
            RealtimeMarketDataIngestionService ingestionService,
            ExternalProviderResiliencePolicy resiliencePolicy,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            TaskScheduler taskScheduler) {
        this(
                kisRealtimeProperties,
                externalProviderProperties,
                frameFactory,
                ingestionService,
                KisProviderSupport.realIndexRealtimeProvider(externalProviderProperties)
                        .filter(provider -> !KisProviderSupport.isSameRealtimeIdentity(
                                provider,
                                externalProviderProperties.kis())),
                restClientBuilder,
                resiliencePolicy,
                objectMapper,
                taskScheduler);
    }

    private KisRealtimeIndexSessionRunner(
            KisRealtimeProperties kisRealtimeProperties,
            ExternalProviderProperties externalProviderProperties,
            KisRealtimeSubscriptionFrameFactory frameFactory,
            RealtimeMarketDataIngestionService ingestionService,
            Optional<ExternalProviderProperties.Kis> realIndexProvider,
            RestClient.Builder restClientBuilder,
            ExternalProviderResiliencePolicy resiliencePolicy,
            ObjectMapper objectMapper,
            TaskScheduler taskScheduler) {
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
                        .orElse(null),
                taskScheduler,
                Clock.systemUTC());
    }

    KisRealtimeIndexSessionRunner(
            KisRealtimeProperties kisRealtimeProperties,
            ExternalProviderProperties externalProviderProperties,
            KisRealtimeSubscriptionFrameFactory frameFactory,
            RealtimeMarketDataIngestionService ingestionService,
            Optional<ExternalProviderProperties.Kis> realIndexProvider,
            KisRealtimeApprovalKeyProvider approvalKeyProvider,
            KisRealtimeWebSocketConnection webSocketConnection) {
        this(
                kisRealtimeProperties,
                externalProviderProperties,
                frameFactory,
                ingestionService,
                realIndexProvider,
                approvalKeyProvider,
                webSocketConnection,
                null,
                Clock.systemUTC());
    }

    KisRealtimeIndexSessionRunner(
            KisRealtimeProperties kisRealtimeProperties,
            ExternalProviderProperties externalProviderProperties,
            KisRealtimeSubscriptionFrameFactory frameFactory,
            RealtimeMarketDataIngestionService ingestionService,
            Optional<ExternalProviderProperties.Kis> realIndexProvider,
            KisRealtimeApprovalKeyProvider approvalKeyProvider,
            KisRealtimeWebSocketConnection webSocketConnection,
            TaskScheduler taskScheduler,
            Clock clock) {
        this.kisRealtimeProperties = kisRealtimeProperties;
        this.externalProviderProperties = externalProviderProperties;
        this.frameFactory = frameFactory;
        this.ingestionService = ingestionService;
        this.realIndexProvider = realIndexProvider;
        this.approvalKeyProvider = approvalKeyProvider;
        this.webSocketConnection = webSocketConnection;
        this.taskScheduler = taskScheduler;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!kisRealtimeProperties.enabled() || kisRealtimeProperties.indexCodes().isEmpty()) {
            return;
        }
        if (KisProviderSupport.realIndexRealtimeProvider(externalProviderProperties)
                .filter(provider -> KisProviderSupport.isSameRealtimeIdentity(
                        provider,
                        externalProviderProperties.kis()))
                .isPresent()) {
            return;
        }
        if (realIndexProvider.isEmpty() || approvalKeyProvider == null || webSocketConnection == null) {
            log.warn("KIS realtime index session is disabled because real KIS websocket credential is not configured");
            return;
        }
        startSession(0);
    }

    private void startSession(int retryAttempt) {
        try {
            String approvalKey = approvalKeyProvider.approvalKey();
            List<KisRealtimeSubscriptionFrame> frames = indexFrames(approvalKey, kisRealtimeProperties.indexCodes());
            URI websocketUrl = realIndexProvider.orElseThrow().websocketUrl();
            log.info(
                    "Starting real KIS realtime index session indexCount={} subscriptionFrameCount={}",
                    kisRealtimeProperties.indexCodes().size(),
                    frames.size());
            webSocketConnection.connect(websocketUrl, frames, ingestionService::ingestKisMessage);
            startupRetryScheduled.set(false);
        } catch (RuntimeException exception) {
            // 지수 실시간 연결 실패가 API 기동 실패로 전파되지 않도록 격리한다.
            log.warn("Real KIS realtime index session start failed: {}", exception.toString());
            scheduleStartupRetry(retryAttempt + 1);
        }
    }

    private void scheduleStartupRetry(int retryAttempt) {
        if (taskScheduler == null || !startupRetryScheduled.compareAndSet(false, true)) {
            return;
        }
        long delaySeconds = startupRetryDelaySeconds(retryAttempt);
        log.info("Scheduling real KIS realtime index session startup retry delay={}s retryAttempt={}",
                delaySeconds,
                retryAttempt);
        taskScheduler.schedule(
                () -> {
                    startupRetryScheduled.set(false);
                    startSession(retryAttempt);
                },
                clock.instant().plusSeconds(delaySeconds));
    }

    static long startupRetryDelaySeconds(int retryAttempt) {
        long exponentialDelay = 1L << Math.min(Math.max(retryAttempt - 1, 0), 5);
        long jitterBound = Math.min(STARTUP_RETRY_JITTER_BOUND_SECONDS, exponentialDelay);
        long jitter = ThreadLocalRandom.current().nextLong(jitterBound + 1);
        return Math.min(exponentialDelay + jitter, MAX_STARTUP_RETRY_DELAY_SECONDS);
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
