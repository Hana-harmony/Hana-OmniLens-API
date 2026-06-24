package com.hana.omnilens.provider.market;

public enum KisStockMasterMarket {
    KOSPI("KOSPI", "kospi_code.mst", 228, 22),
    KOSDAQ("KOSDAQ", "kosdaq_code.mst", 222, 18),
    KONEX("KONEX", "konex_code.mst", 184, -1);

    private final String marketName;
    private final String entryName;
    private final int tailWidth;
    private final int etpFlagOffset;

    KisStockMasterMarket(
            String marketName,
            String entryName,
            int tailWidth,
            int etpFlagOffset) {
        this.marketName = marketName;
        this.entryName = entryName;
        this.tailWidth = tailWidth;
        this.etpFlagOffset = etpFlagOffset;
    }

    public String marketName() {
        return marketName;
    }

    public String entryName() {
        return entryName;
    }

    int tailWidth() {
        return tailWidth;
    }

    int etpFlagOffset() {
        return etpFlagOffset;
    }
}
