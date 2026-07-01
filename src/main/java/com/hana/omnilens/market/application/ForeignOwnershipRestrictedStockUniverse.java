package com.hana.omnilens.market.application;

import java.util.List;
import java.util.Set;

public final class ForeignOwnershipRestrictedStockUniverse {

    private static final List<String> STOCK_CODES = List.of(
            "015760",
            "030200",
            "032640",
            "017670",
            "031310",
            "036630",
            "065530",
            "036460",
            "034120",
            "058400",
            "033830",
            "040300",
            "057050",
            "126560",
            "053210",
            "037560",
            "039340",
            "039290",
            "033130",
            "036030",
            "066790",
            "035760",
            "122450",
            "036420",
            "127710",
            "003490",
            "020560",
            "003495",
            "091810",
            "298690",
            "272450",
            "089590");

    private static final Set<String> STOCK_CODE_SET = Set.copyOf(STOCK_CODES);
    private static final Set<String> ZERO_LIMIT_STOCK_CODE_SET = Set.of(
            "033830",
            "034120",
            "058400");

    private ForeignOwnershipRestrictedStockUniverse() {
    }

    public static List<String> stockCodes() {
        return STOCK_CODES;
    }

    public static boolean isRestrictedStockCode(String stockCode) {
        return STOCK_CODE_SET.contains(stockCode);
    }

    public static boolean isZeroLimitRestrictedStockCode(String stockCode) {
        return ZERO_LIMIT_STOCK_CODE_SET.contains(stockCode);
    }
}
