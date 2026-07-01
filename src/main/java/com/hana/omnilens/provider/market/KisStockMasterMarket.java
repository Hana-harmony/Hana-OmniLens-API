package com.hana.omnilens.provider.market;

public enum KisStockMasterMarket {
    KOSPI("KOSPI", "kospi_code.mst", 228),
    KOSDAQ("KOSDAQ", "kosdaq_code.mst", 222),
    KONEX("KONEX", "konex_code.mst", 184);

    private final String marketName;
    private final String entryName;
    private final int tailWidth;

    KisStockMasterMarket(
            String marketName,
            String entryName,
            int tailWidth) {
        this.marketName = marketName;
        this.entryName = entryName;
        this.tailWidth = tailWidth;
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
}
