package com.hana.omnilens.market.stream;

public record MarketIndexReplayRequest(String type) {

    public boolean isReplayRequest() {
        return "INDEX_STREAM_REPLAY".equalsIgnoreCase(type);
    }
}
