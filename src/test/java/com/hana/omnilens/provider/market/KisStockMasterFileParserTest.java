package com.hana.omnilens.provider.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.Test;

class KisStockMasterFileParserTest {

    private final KisStockMasterFileParser parser = new KisStockMasterFileParser();

    @Test
    void parseKospiStockMasterKeepsOnlyEquityRows() {
        String samsung = line(
                "005930",
                "KR7005930003",
                "삼성전자",
                KisStockMasterMarket.KOSPI.tailWidth(),
                KisStockMasterMarket.KOSPI.etpFlagOffset(),
                'N');
        String kodexEtf = line(
                "069500",
                "KR7069500007",
                "KODEX 200",
                KisStockMasterMarket.KOSPI.tailWidth(),
                KisStockMasterMarket.KOSPI.etpFlagOffset(),
                'Y');
        String fund = line(
                "F70100026",
                "KR5701000261",
                "한투글로벌넥스트웨이브1(A)",
                KisStockMasterMarket.KOSPI.tailWidth(),
                KisStockMasterMarket.KOSPI.etpFlagOffset(),
                'N');

        assertThat(parser.parse(KisStockMasterMarket.KOSPI, samsung + "\n" + kodexEtf + "\n" + fund))
                .extracting("stockCode", "stockName", "stockNameEn", "market", "isinCode")
                .containsExactly(tuple("005930", "삼성전자", "삼성전자", "KOSPI", "KR7005930003"));
    }

    @Test
    void parseKonexStockMasterSupportsKonexRows() {
        String konex = line(
                "123456",
                "KR7123456782",
                "코넥스테스트",
                KisStockMasterMarket.KONEX.tailWidth(),
                -1,
                'N');

        assertThat(parser.parse(KisStockMasterMarket.KONEX, konex))
                .extracting("stockCode", "stockName", "market")
                .containsExactly(tuple("123456", "코넥스테스트", "KONEX"));
    }

    private static String line(
            String stockCode,
            String isinCode,
            String stockName,
            int tailWidth,
            int etpOffset,
            char etpFlag) {
        char[] tail = "N".repeat(tailWidth).toCharArray();
        if (etpOffset >= 0) {
            tail[etpOffset] = etpFlag;
        }
        return String.format("%-9s", stockCode) + isinCode + stockName + new String(tail);
    }
}
