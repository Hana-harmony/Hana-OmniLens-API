package com.hana.omnilens.marketnews.domain;

import java.time.Instant;
import java.util.List;

public record MarketNewsEvent(
        String newsId,
        String query,
        String title,
        String summary,
        String originalContent,
        List<String> imageUrls,
        String contentAvailability,
        String originalUrl,
        String canonicalUrl,
        String sourceLicensePolicy,
        String duplicateKey,
        Instant publishedAt,
        Instant createdAt
) {

    public MarketNewsEvent {
        imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
    }
}
