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

@Service
public class NewsProcessingService {

    private static final Logger log = LoggerFactory.getLogger(NewsProcessingService.class);
    private static final Duration PROCESSING_LEASE = Duration.ofMinutes(45);
    private static final long[] RETRY_MINUTES = {1, 2, 5, 10, 30, 60, 180, 360};

    private final NewsProcessingRepository processingRepository;
    private final StockMasterRepository stockMasterRepository;
    private final AlertAnalysisPublishingService publishingService;
    private final AlertEventRepository alertEventRepository;
    private final Clock clock;

    @Autowired
    public NewsProcessingService(
            NewsProcessingRepository processingRepository,
            StockMasterRepository stockMasterRepository,
            AlertAnalysisPublishingService publishingService,
            AlertEventRepository alertEventRepository) {
        this(processingRepository, stockMasterRepository, publishingService, alertEventRepository, Clock.systemUTC());
    }

    NewsProcessingService(
            NewsProcessingRepository processingRepository,
            StockMasterRepository stockMasterRepository,
            AlertAnalysisPublishingService publishingService,
            AlertEventRepository alertEventRepository,
            Clock clock) {
        this.processingRepository = processingRepository;
        this.stockMasterRepository = stockMasterRepository;
        this.publishingService = publishingService;
        this.alertEventRepository = alertEventRepository;
        this.clock = clock;
    }

    public boolean enqueue(
            String partnerId,
            StockSummary stock,
            String title,
            String snippet,
            String originalUrl,
            Instant publishedAt,
            String sourceContent,
            List<String> imageUrls,
            String canonicalUrl,
            String contentHash,
            String sourceLicensePolicy) {
        NewsProcessingJob job = new NewsProcessingJob(
                jobId(partnerId, stock.stockCode(), originalUrl), partnerId, stock.stockCode(),
                textOrEmpty(title), textOrEmpty(snippet), originalUrl, publishedAt,
                sourceContent, String.join("\n", imageUrls == null ? List.of() : imageUrls),
                textOrEmpty(canonicalUrl), textOrEmpty(contentHash), textOrEmpty(sourceLicensePolicy),
                "PENDING", 0, null);
        boolean inserted = processingRepository.enqueue(job, clock.instant());
        if (inserted) {
            log.info("Stock news queued: stockCode={}, originalUrl={}", stock.stockCode(), originalUrl);
        }
        return inserted;
    }

    public Optional<AlertEvent> processNext() {
        Instant now = clock.instant();
        return processingRepository.claimNext(now, PROCESSING_LEASE).flatMap(this::processClaimed);
    }

    private Optional<AlertEvent> processClaimed(NewsProcessingJob job) {
        try {
            Optional<AlertEvent> existing = alertEventRepository.findBySourceIdentity(
                    job.partnerId(), job.stockCode(), "NEWS", job.originalUrl());
            if (existing.filter(publishingService::isDisplayableFullArticle).isPresent()) {
                AlertEvent event = existing.orElseThrow();
                processingRepository.markReady(job, event.alertId(), clock.instant());
                return Optional.of(event);
            }
            StockSummary stock = stockMasterRepository.findByCode(job.stockCode())
                    .orElseThrow(() -> new IllegalStateException("Stock master missing for news job: " + job.stockCode()));
            AlertPublishRequest analyzed = publishingService.analyzeForCollection(
                    new AlertAnalysisPublishRequest(
                            job.partnerId(), "NEWS", job.title(), job.snippet(), job.sourceContent(),
                            imageUrls(job.imageUrls()), job.canonicalUrl(), job.contentHash(),
                            job.sourceLicensePolicy(), job.originalUrl(), job.publishedAt(),
                            List.of(new AlertAnalysisPublishRequest.StockCandidateRequest(
                                    stock.stockCode(), stock.stockName(), stock.stockNameEn(),
                                    List.of(stock.stockName(), stock.stockNameEn())))));
            if (!job.stockCode().equals(analyzed.stockCode())) {
                processingRepository.markRejected(
                        job, "AI analysis matched a different stock: " + analyzed.stockCode(), clock.instant());
                return Optional.empty();
            }
            if (!publishingService.isPublishReady(analyzed)) {
                throw new IllegalStateException("AI news output is not publish-ready");
            }
            AlertEvent event = publishingService.publishAnalyzed(analyzed);
            processingRepository.markReady(job, event.alertId(), clock.instant());
            log.info("Stock news published: stockCode={}, alertId={}", job.stockCode(), event.alertId());
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

    private void scheduleRetry(NewsProcessingJob job, RuntimeException exception) {
        int retryIndex = Math.max(0, Math.min(job.attemptCount() - 1, RETRY_MINUTES.length - 1));
        long delayMinutes = RETRY_MINUTES[retryIndex];
        String reason = exception.getClass().getSimpleName() + ": " + textOrEmpty(exception.getMessage());
        Instant now = clock.instant();
        processingRepository.scheduleRetry(job, reason, now.plus(Duration.ofMinutes(delayMinutes)), now);
        log.warn(
                "Stock news processing deferred: stockCode={}, attempt={}, retryMinutes={}, reason={}",
                job.stockCode(), job.attemptCount(), delayMinutes, reason);
    }

    private static List<String> imageUrls(String value) {
        return StringUtils.hasText(value)
                ? value.lines().map(String::strip).filter(StringUtils::hasText).distinct().toList()
                : List.of();
    }

    private static String jobId(String partnerId, String stockCode, String originalUrl) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((partnerId + ":" + stockCode + ":" + originalUrl).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String textOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
