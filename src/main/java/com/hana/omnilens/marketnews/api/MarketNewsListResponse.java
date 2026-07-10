package com.hana.omnilens.marketnews.api;

import java.util.List;

import com.hana.omnilens.marketnews.domain.MarketNewsEvent;

public record MarketNewsListResponse(
        int newsCount,
        List<MarketNewsEvent> news,
        String nextCursor
) {

    public MarketNewsListResponse(int newsCount, List<MarketNewsEvent> news) {
        this(newsCount, news, null);
    }

    public MarketNewsListResponse {
        news = news == null ? List.of() : List.copyOf(news);
    }
}
