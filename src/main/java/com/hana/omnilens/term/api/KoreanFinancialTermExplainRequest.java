package com.hana.omnilens.term.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KoreanFinancialTermExplainRequest(
        @NotBlank @Size(max = 80) String term,
        @Pattern(regexp = "en") String locale,
        @Pattern(regexp = "NEWS|DISCLOSURE") String sourceType,
        @Size(max = 300) String title,
        @Size(max = 4000) String context,
        @Pattern(regexp = "\\d{6}") String stockCode,
        @Size(max = 80) String stockName,
        @Size(max = 120) String articleId,
        @Size(max = 1000) String articleUrl,
        @Size(max = 160) String userKey,
        @Size(max = 160) String sessionKey,
        Boolean allowWebSearch
) {
    public KoreanFinancialTermExplainRequest {
        locale = locale == null || locale.isBlank() ? "en" : locale;
        sourceType = sourceType == null || sourceType.isBlank() ? "NEWS" : sourceType;
        title = title == null ? "" : title;
        context = context == null ? "" : context;
        stockName = stockName == null ? "" : stockName;
        articleId = articleId == null ? "" : articleId;
        articleUrl = articleUrl == null ? "" : articleUrl;
        userKey = userKey == null ? "" : userKey;
        sessionKey = sessionKey == null ? "" : sessionKey;
        allowWebSearch = allowWebSearch == null || allowWebSearch;
    }
}
