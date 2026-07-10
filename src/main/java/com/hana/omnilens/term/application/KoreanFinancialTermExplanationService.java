package com.hana.omnilens.term.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;

import com.hana.omnilens.provider.ProviderCircuitOpenException;
import com.hana.omnilens.provider.ai.HannahAiFinancialTermEvidence;
import com.hana.omnilens.provider.ai.HannahAiKoreanFinancialTermClient;
import com.hana.omnilens.provider.ai.HannahAiKoreanFinancialTermExplainRequest;
import com.hana.omnilens.provider.ai.HannahAiKoreanFinancialTermExplainResponse;
import com.hana.omnilens.term.api.KoreanFinancialTermExplainRequest;
import com.hana.omnilens.term.domain.FinancialTermEvidence;
import com.hana.omnilens.term.domain.KoreanFinancialTermClickStat;
import com.hana.omnilens.term.domain.KoreanFinancialTermExplanation;

@Service
public class KoreanFinancialTermExplanationService {

    private static final Pattern UNSAFE_TERM_CHARS = Pattern.compile("[^0-9A-Za-z가-힣%+_.\\-]");
    private static final String EMPTY_CONTEXT_HASH = "none";
    private static final String CACHE_SCHEMA_VERSION = "term-rag-display-v3";

    private final KoreanFinancialTermExplanationRepository repository;
    private final HannahAiKoreanFinancialTermClient hannahAiClient;
    private final Clock clock;
    private final String analyticsHashSalt;

    @Autowired
    public KoreanFinancialTermExplanationService(
            KoreanFinancialTermExplanationRepository repository,
            HannahAiKoreanFinancialTermClient hannahAiClient,
            @Value("${omnilens.term.analytics.hash-salt:local-term-analytics-salt}") String analyticsHashSalt) {
        this(repository, hannahAiClient, Clock.systemUTC(), analyticsHashSalt);
    }

    KoreanFinancialTermExplanationService(
            KoreanFinancialTermExplanationRepository repository,
            HannahAiKoreanFinancialTermClient hannahAiClient,
            Clock clock,
            String analyticsHashSalt) {
        this.repository = repository;
        this.hannahAiClient = hannahAiClient;
        this.clock = clock;
        this.analyticsHashSalt = analyticsHashSalt == null ? "" : analyticsHashSalt;
    }

    public KoreanFinancialTermExplanation explain(KoreanFinancialTermExplainRequest request) {
        Instant now = Instant.now(clock);
        String normalizedTerm = normalizeTerm(request.term());
        String effectiveArticleId = effectiveArticleId(request);
        String cacheKey = cacheKey(normalizedTerm, request.locale(), request.sourceType());

        Optional<KoreanFinancialTermExplanationCacheEntry> cached = repository.findValidCache(cacheKey, now);
        KoreanFinancialTermExplanation explanation = cached
                .map(KoreanFinancialTermExplanationCacheEntry::response)
                .orElseGet(() -> generateAndCache(request, normalizedTerm, effectiveArticleId, cacheKey, now));
        String canonicalNormalizedTerm = canonicalNormalizedTerm(explanation, normalizedTerm);

        boolean cacheHit = cached.isPresent();
        KoreanFinancialTermClickLog clickLog = clickLog(
                request,
                canonicalNormalizedTerm,
                effectiveArticleId,
                explanation,
                cacheHit,
                now);
        long clickCount = repository.recordClick(clickLog);
        return explanation.withAnalytics(cacheHit, clickCount);
    }

    public List<KoreanFinancialTermClickStat> stats(int limit) {
        return repository.findTopStats(limit);
    }

    private KoreanFinancialTermExplanation generateAndCache(
            KoreanFinancialTermExplainRequest request,
            String normalizedTerm,
            String effectiveArticleId,
            String cacheKey,
            Instant now) {
        KoreanFinancialTermExplanation explanation = explainWithAi(request);
        if (shouldCache(explanation)) {
            String canonicalNormalizedTerm = canonicalNormalizedTerm(explanation, normalizedTerm);
            repository.upsertCache(new KoreanFinancialTermExplanationCacheEntry(
                    cacheKey,
                    explanation.term(),
                    canonicalNormalizedTerm,
                    request.locale(),
                    effectiveArticleId,
                    blankToNull(request.stockCode()),
                    explanation.source(),
                    explanation.displayMode(),
                    true,
                    now.plusSeconds(Math.max(explanation.cacheTtlSeconds(), 3600)),
                    explanation.withAnalytics(false, 0)), now);
        }
        return explanation;
    }

    private KoreanFinancialTermExplanation explainWithAi(KoreanFinancialTermExplainRequest request) {
        try {
            HannahAiKoreanFinancialTermExplainResponse response = hannahAiClient.explain(
                    new HannahAiKoreanFinancialTermExplainRequest(
                            request.term(),
                            request.locale(),
                            request.sourceType(),
                            request.title(),
                            request.context(),
                            request.stockCode(),
                            request.stockName(),
                            request.articleId(),
                            request.articleUrl()));
            return toDomain(response);
        } catch (ProviderCircuitOpenException | RestClientException | IllegalStateException exception) {
            return reviewRequired(request.term(), normalizeTerm(request.term()), Instant.now(clock), "AI_UNAVAILABLE");
        }
    }

    private boolean shouldCache(KoreanFinancialTermExplanation explanation) {
        return explanation.cacheable()
                && "EXPLANATION".equals(explanation.displayMode())
                && !"AI_UNAVAILABLE".equals(explanation.source());
    }

    private KoreanFinancialTermClickLog clickLog(
            KoreanFinancialTermExplainRequest request,
            String normalizedTerm,
            String effectiveArticleId,
            KoreanFinancialTermExplanation explanation,
            boolean cacheHit,
            Instant now) {
        return new KoreanFinancialTermClickLog(
                UUID.randomUUID().toString(),
                now,
                request.term().trim(),
                normalizedTerm,
                request.locale(),
                request.sourceType(),
                effectiveArticleId,
                request.articleUrl(),
                blankToNull(request.stockCode()),
                request.stockName(),
                saltedHash(request.userKey()),
                saltedHash(request.sessionKey()),
                cacheHit,
                explanation.source(),
                explanation.displayMode());
    }

    private KoreanFinancialTermExplanation toDomain(HannahAiKoreanFinancialTermExplainResponse response) {
        return new KoreanFinancialTermExplanation(
                response.term(),
                response.normalizedTerm(),
                response.englishTerm(),
                response.category(),
                response.definition(),
                response.explanation(),
                response.example(),
                response.confidenceScore(),
                response.confidenceLevel(),
                response.displayMode(),
                response.source(),
                response.cacheable(),
                response.cacheTtlSeconds(),
                response.evidence().stream().map(this::toDomainEvidence).toList(),
                response.qualityFlags(),
                response.modelVersion(),
                response.generatedAt(),
                false,
                0);
    }

    private FinancialTermEvidence toDomainEvidence(HannahAiFinancialTermEvidence evidence) {
        return new FinancialTermEvidence(
                evidence.title(),
                evidence.snippet(),
                evidence.url(),
                evidence.sourceType());
    }

    private KoreanFinancialTermExplanation reviewRequired(
            String term,
            String normalizedTerm,
            Instant now,
            String source) {
        return new KoreanFinancialTermExplanation(
                term.trim(),
                normalizedTerm,
                "",
                "unverified",
                "",
                "This term needs human review before we show an automated explanation.",
                "",
                new BigDecimal("0.10"),
                "LOW",
                "REVIEW_REQUIRED",
                source,
                false,
                0,
                List.of(),
                List.of("REVIEW_REQUIRED"),
                "k-finance-term-rag-v2",
                now,
                false,
                0);
    }

    private String normalizeTerm(String term) {
        String stripped = UNSAFE_TERM_CHARS.matcher(term == null ? "" : term.trim()).replaceAll("");
        return stripped.toLowerCase(Locale.ROOT);
    }

    private String cacheKey(String normalizedTerm, String locale, String sourceType) {
        return saltedHash("cache|" + CACHE_SCHEMA_VERSION + "|" + normalizedTerm + "|" + locale + "|" + sourceType);
    }

    private String canonicalNormalizedTerm(KoreanFinancialTermExplanation explanation, String fallback) {
        if (explanation != null && StringUtils.hasText(explanation.normalizedTerm())) {
            return normalizeTerm(explanation.normalizedTerm());
        }
        return fallback;
    }

    private String effectiveArticleId(KoreanFinancialTermExplainRequest request) {
        if (StringUtils.hasText(request.articleId())) {
            return request.articleId().trim();
        }
        String contextHash = StringUtils.hasText(request.context())
                ? saltedHash(request.context().substring(0, Math.min(request.context().length(), 500)))
                : EMPTY_CONTEXT_HASH;
        return saltedHash("article|" + request.sourceType() + "|" + request.title() + "|" + request.articleUrl() + "|" + contextHash);
    }

    private String saltedHash(String rawValue) {
        return sha256Hex(analyticsHashSalt + "|" + (rawValue == null ? "" : rawValue));
    }

    private String sha256Hex(String rawValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawValue.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
