package com.hana.omnilens.alert.api;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.hana.omnilens.alert.domain.AlertGlossaryTerm;

public record AlertPublishRequest(
        @NotBlank @Size(max = 80) String partnerId,
        @NotBlank @Pattern(regexp = "\\d{6}") String stockCode,
        @NotBlank @Size(max = 80) String stockName,
        @NotBlank @Pattern(regexp = "NEWS|DISCLOSURE") String sourceType,
        @NotBlank @Size(max = 300) String originalTitle,
        @NotBlank @Size(max = 300) String translatedTitle,
        @NotBlank @Size(max = 1000) String summary,
        @NotBlank @Size(max = 500) String originalUrl,
        @NotNull Instant publishedAt,
        List<String> eventTags,
        @NotBlank @Pattern(regexp = "POSITIVE|NEUTRAL|NEGATIVE") String sentiment,
        @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL") String importance,
        List<String> relatedStocks,
        boolean holderTarget,
        boolean watchlistTarget,
        List<AlertGlossaryTerm> glossaryTerms,
        List<String> translationQualityFlags,
        @Size(max = 128) String duplicateKey,
        @Size(max = 120) String modelVersion,
        Double eventConfidence,
        Double sentimentConfidence,
        Double importanceConfidence,
        Double stockMatchConfidence
) {
}
