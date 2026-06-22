package com.hana.omnilens.provider.news;

import java.util.List;

public record OriginalArticleContent(
        String content,
        List<String> imageUrls,
        String canonicalUrl,
        String contentHash,
        String sourceLicensePolicy
) {
}
