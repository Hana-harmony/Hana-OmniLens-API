package com.hana.omniconnect.provider.news;

import java.time.Instant;

public record NaverNewsArticle(
        String title,
        String snippet,
        String originalUrl,
        Instant publishedAt
) {
}
