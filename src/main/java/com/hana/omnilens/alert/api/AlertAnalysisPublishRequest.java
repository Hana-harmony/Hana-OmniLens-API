package com.hana.omnilens.alert.api;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AlertAnalysisPublishRequest(
        @NotBlank @Size(max = 80) String partnerId,
        @NotBlank @Pattern(regexp = "NEWS|DISCLOSURE") String sourceType,
        @NotBlank @Size(max = 300) String title,
        @Size(max = 1000) String snippet,
        @Size(max = 1000000) String content,
        List<@Size(max = 1000) String> imageUrls,
        @Size(max = 1000) String canonicalUrl,
        @Size(max = 128) String contentHash,
        @Size(max = 80) String sourceLicensePolicy,
        @NotBlank @Size(max = 500) String originalUrl,
        @NotNull Instant publishedAt,
        @Valid List<StockCandidateRequest> stockUniverse
) {
    public record StockCandidateRequest(
            @NotBlank @Pattern(regexp = "\\d{6}") String stockCode,
            @NotBlank @Size(max = 80) String stockName,
            @NotBlank @Size(max = 120) String stockNameEn,
            List<@Size(max = 80) String> aliases
    ) {
    }
}
