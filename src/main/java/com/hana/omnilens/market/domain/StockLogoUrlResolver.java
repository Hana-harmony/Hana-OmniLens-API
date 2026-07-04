package com.hana.omnilens.market.domain;

import java.util.Locale;

public final class StockLogoUrlResolver {

    private static final String KOREA_STOCK_LOGO_BASE =
            "https://static.toss.im/png-icons/securities/icn-sec-fill-";
    private static final String US_STOCK_LOGO_BASE =
            "https://financialmodelingprep.com/image-stock/";

    private StockLogoUrlResolver() {
    }

    public static String koreanStockLogoUrl(String stockCode) {
        String normalized = stockCode == null ? "" : stockCode.trim();
        if (!normalized.matches("\\d{6}")) {
            return "";
        }
        return KOREA_STOCK_LOGO_BASE + normalized + ".png";
    }

    public static String usStockLogoUrl(String ticker) {
        String normalized = ticker == null ? "" : ticker.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9.\\-]{1,20}")) {
            return "";
        }
        return US_STOCK_LOGO_BASE + normalized + ".png";
    }
}
