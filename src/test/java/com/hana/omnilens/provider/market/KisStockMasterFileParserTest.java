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
                " ST");
        String kt = line(
                "030200",
                "KR7030200000",
                "KT",
                KisStockMasterMarket.KOSPI.tailWidth(),
                " ST");
        String kodexEtf = line(
                "069500",
                "KR7069500007",
                "KODEX 200",
                KisStockMasterMarket.KOSPI.tailWidth(),
                " EF");
        String fund = line(
                "F70100026",
                "KR5701000261",
                "한투글로벌넥스트웨이브1(A)",
                KisStockMasterMarket.KOSPI.tailWidth(),
                " BC");

        assertThat(parser.parse(KisStockMasterMarket.KOSPI, samsung + "\n" + kt + "\n" + kodexEtf + "\n" + fund))
                .extracting("stockCode", "stockName", "stockNameEn", "market", "isinCode")
                .containsExactly(
                        tuple("005930", "삼성전자", "삼성전자", "KOSPI", "KR7005930003"),
                        tuple("030200", "KT", "KT", "KOSPI", "KR7030200000"));
    }

    @Test
    void parseKonexStockMasterSupportsKonexRows() {
        String konex = line(
                "123456",
                "KR7123456782",
                "코넥스테스트",
                KisStockMasterMarket.KONEX.tailWidth(),
                "ST0");

        assertThat(parser.parse(KisStockMasterMarket.KONEX, konex))
                .extracting("stockCode", "stockName", "market")
                .containsExactly(tuple("123456", "코넥스테스트", "KONEX"));
    }

    private static String line(
            String stockCode,
            String isinCode,
            String stockName,
            int tailWidth,
            String issueGroup) {
        char[] tail = "N".repeat(tailWidth).toCharArray();
        issueGroup.getChars(0, issueGroup.length(), tail, 0);
        return String.format("%-9s", stockCode) + isinCode + stockName + new String(tail);
    }
}
