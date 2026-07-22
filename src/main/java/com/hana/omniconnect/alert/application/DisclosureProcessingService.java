package com.hana.omniconnect.alert.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.hana.omniconnect.alert.api.AlertAnalysisPublishRequest;
import com.hana.omniconnect.alert.api.AlertPublishRequest;
import com.hana.omniconnect.alert.domain.AlertEvent;
import com.hana.omniconnect.market.application.StockMasterRepository;
import com.hana.omniconnect.market.domain.StockSummary;
import com.hana.omniconnect.provider.disclosure.OpenDartDisclosure;
import com.hana.omniconnect.provider.disclosure.OpenDartDisclosureClient;
import com.hana.omniconnect.provider.disclosure.OpenDartDisclosureDocument;

@Service
public class DisclosureProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DisclosureProcessingService.class);
    private static final Duration PROCESSING_LEASE = Duration.ofMinutes(45);
    private static final int DISCLOSURE_FEED_EXCERPT_MAX_CHARS = 1_200;
    private static final long[] RETRY_MINUTES = {1, 2, 5, 10, 30, 60, 180, 360};

    private final DisclosureProcessingRepository processingRepository;
    private final OpenDartDisclosureClient openDartDisclosureClient;
    private final StockMasterRepository stockMasterRepository;
    private final AlertAnalysisPublishingService publishingService;
    private final AlertEventRepository alertEventRepository;
    private final Clock clock;

    @Autowired
    public DisclosureProcessingService(
            DisclosureProcessingRepository processingRepository,
            OpenDartDisclosureClient openDartDisclosureClient,
            StockMasterRepository stockMasterRepository,
            AlertAnalysisPublishingService publishingService,
            AlertEventRepository alertEventRepository) {
        this(
                processingRepository,
                openDartDisclosureClient,
                stockMasterRepository,
                publishingService,
                alertEventRepository,
                Clock.systemUTC());
    }

    DisclosureProcessingService(
            DisclosureProcessingRepository processingRepository,
            OpenDartDisclosureClient openDartDisclosureClient,
            StockMasterRepository stockMasterRepository,
            AlertAnalysisPublishingService publishingService,
            AlertEventRepository alertEventRepository,
            Clock clock) {
        this.processingRepository = processingRepository;
        this.openDartDisclosureClient = openDartDisclosureClient;
        this.stockMasterRepository = stockMasterRepository;
        this.publishingService = publishingService;
        this.alertEventRepository = alertEventRepository;
        this.clock = clock;
    }

    public boolean enqueue(
            String partnerId,
            StockSummary stock,
            OpenDartDisclosure disclosure,
            Instant publishedAt) {
        Instant now = clock.instant();
        DisclosureProcessingJob job = new DisclosureProcessingJob(
                jobId(partnerId, stock.stockCode(), disclosure.originalUrl()),
                partnerId,
                stock.stockCode(),
                disclosure.receiptNumber(),
                disclosure.corporationName(),
                disclosure.reportName(),
                disclosure.originalUrl(),
                publishedAt,
                null,
                null,
                null,
                "PENDING",
                0,
                null);
        boolean inserted = processingRepository.enqueue(job, now);
        if (inserted) {
            log.info(
                    "OpenDART disclosure queued: stockCode={}, receiptNumber={}",
                    stock.stockCode(),
                    disclosure.receiptNumber());
        }
        return inserted;
    }

    public Optional<AlertEvent> processNext() {
        Instant now = clock.instant();
        return processingRepository.claimNext(now, PROCESSING_LEASE)
                .flatMap(this::processClaimed);
    }

    private Optional<AlertEvent> processClaimed(DisclosureProcessingJob job) {
        Instant now = clock.instant();
        try {
            Optional<AlertEvent> existing = alertEventRepository.findBySourceIdentity(
                    job.partnerId(),
                    job.stockCode(),
                    "DISCLOSURE",
                    job.originalUrl());
            if (existing.filter(publishingService::isDisplayableFullArticle).isPresent()) {
                AlertEvent event = existing.orElseThrow();
                processingRepository.markReady(job, event.alertId(), now);
                return Optional.of(event);
            }

            StockSummary stock = stockMasterRepository.findByCode(job.stockCode())
                    .orElseThrow(() -> new IllegalStateException(
                            "Stock master missing for disclosure job: " + job.stockCode()));
            OpenDartDisclosureDocument document = sourceDocument(job, now);
            String sourceContent = disclosureFeedContent(document.content());
            AlertPublishRequest analyzed = publishingService.analyzeForCollection(
                    new AlertAnalysisPublishRequest(
                            job.partnerId(),
                            "DISCLOSURE",
                            job.corporationName() + " " + job.reportName(),
                            job.reportName(),
                            sourceContent,
                            List.of(),
                            "",
                            document.contentHash(),
                            document.sourceLicensePolicy() + ":feed-excerpt-v1",
                            job.originalUrl(),
                            job.publishedAt(),
                            List.of(new AlertAnalysisPublishRequest.StockCandidateRequest(
                                    stock.stockCode(),
                                    stock.stockName(),
                                    stock.stockNameEn(),
                                    List.of(stock.stockName(), stock.stockNameEn())))));
            if (!job.stockCode().equals(analyzed.stockCode())) {
                processingRepository.markRejected(
                        job,
                        "AI analysis matched a different stock: " + analyzed.stockCode(),
                        clock.instant());
                return Optional.empty();
            }
            if (!publishingService.isPublishReady(analyzed)) {
                throw new IllegalStateException("Qwen disclosure output is not publish-ready");
            }

            AlertEvent event = publishingService.publishAnalyzed(analyzed);
            processingRepository.markReady(job, event.alertId(), clock.instant());
            log.info(
                    "OpenDART disclosure published: stockCode={}, receiptNumber={}, alertId={}",
                    job.stockCode(),
                    job.receiptNumber(),
                    event.alertId());
            return Optional.of(event);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                processingRepository.markRejected(job, exception.getReason(), clock.instant());
                return Optional.empty();
            }
            scheduleRetry(job, exception);
            return Optional.empty();
        } catch (RuntimeException exception) {
            scheduleRetry(job, exception);
            return Optional.empty();
        }
    }

    private OpenDartDisclosureDocument sourceDocument(DisclosureProcessingJob job, Instant now) {
        if (job.hasSourceContent()) {
            return new OpenDartDisclosureDocument(
                    job.sourceContent(),
                    textOrEmpty(job.contentHash()),
                    textOrEmpty(job.sourceLicensePolicy()));
        }
        OpenDartDisclosureDocument fetched = openDartDisclosureClient
                .fetchDocumentContent(job.receiptNumber())
                .orElseThrow(() -> new IllegalStateException(
                        "OpenDART document body is unavailable: " + job.receiptNumber()));
        String sourceContent = textOrEmpty(fetched.content()).replace("\u0000", "");
        if (!StringUtils.hasText(sourceContent)) {
            throw new IllegalStateException("OpenDART document body is empty: " + job.receiptNumber());
        }
        processingRepository.saveSourceDocument(
                job,
                sourceContent,
                textOrEmpty(fetched.contentHash()),
                textOrEmpty(fetched.sourceLicensePolicy()),
                now);
        return new OpenDartDisclosureDocument(
                sourceContent,
                textOrEmpty(fetched.contentHash()),
                textOrEmpty(fetched.sourceLicensePolicy()));
    }

    private void scheduleRetry(DisclosureProcessingJob job, RuntimeException exception) {
        Instant now = clock.instant();
        int retryIndex = Math.max(0, Math.min(job.attemptCount() - 1, RETRY_MINUTES.length - 1));
        long delayMinutes = RETRY_MINUTES[retryIndex];
        String reason = exception.getClass().getSimpleName() + ": " + textOrEmpty(exception.getMessage());
        processingRepository.scheduleRetry(job, reason, now.plus(Duration.ofMinutes(delayMinutes)), now);
        log.warn(
                "OpenDART disclosure processing deferred: stockCode={}, receiptNumber={}, attempt={}, retryMinutes={}, reason={}",
                job.stockCode(),
                job.receiptNumber(),
                job.attemptCount(),
                delayMinutes,
                reason);
    }

    private static String disclosureFeedContent(String content) {
        if (!StringUtils.hasText(content) || content.length() <= DISCLOSURE_FEED_EXCERPT_MAX_CHARS) {
            return textOrEmpty(content);
        }
        int split = content.lastIndexOf(' ', DISCLOSURE_FEED_EXCERPT_MAX_CHARS);
        if (split < DISCLOSURE_FEED_EXCERPT_MAX_CHARS / 2) {
            split = DISCLOSURE_FEED_EXCERPT_MAX_CHARS;
        }
        return content.substring(0, split).strip();
    }

    private static String jobId(String partnerId, String stockCode, String originalUrl) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((partnerId + ":" + stockCode + ":" + originalUrl)
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String textOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
