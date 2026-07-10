package com.hana.omnilens.market.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hana.omnilens.market.application.StockMasterRepository;

@SpringBootTest(properties = {
        "omnilens.alert.dedupe.mode=in-memory",
        "omnilens.market.exchange-rate-cache.mode=in-memory",
        "omnilens.market.foreign-ownership-cache.mode=in-memory"
})
class JdbcStockMasterRepositoryTest {

    @Autowired
    private StockMasterRepository repository;

    @Autowired
    private JdbcStockMasterRepository jdbcRepository;

    @Autowired
    private StockMasterSeedLoader seedLoader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedLoadsStockMasterRowsIntoDatabase() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM stock_master", Integer.class);

        assertThat(count).isNotNull().isGreaterThanOrEqualTo(34);
        assertThat(repository.findByCode("005930")).isPresent()
                .get()
                .extracting("stockName", "stockNameEn", "market", "isinCode", "dartCorpCode")
                .containsExactly(
                        "삼성전자",
                        "Samsung Electronics",
                        "KOSPI",
                        "KR7005930003",
                        "00126380");
    }

    @Test
    void seedContainsForeignOwnershipRestrictedIndustryCandidates() {
        assertThat(repository.findByCode("003490")).isPresent()
                .get()
                .extracting("stockName", "market", "isinCode", "dartCorpCode")
                .containsExactly("대한항공", "KOSPI", "KR7003490000", "00113526");
        assertThat(repository.findByCode("030200")).isPresent()
                .get()
                .extracting("stockName", "market", "isinCode", "dartCorpCode")
                .containsExactly("KT", "KOSPI", "KR7030200000", "00190321");
        assertThat(repository.findByCode("034120")).isPresent()
                .get()
                .extracting("stockName", "market", "isinCode", "dartCorpCode")
                .containsExactly("SBS", "KOSPI", "KR7034120006", "00130772");
        assertThat(repository.findByCode("036460")).isPresent()
                .get()
                .extracting("stockName", "market", "isinCode", "dartCorpCode")
                .containsExactly("한국가스공사", "KOSPI", "KR7036460004", "00261285");
    }

    @Test
    void searchMatchesCodeKoreanNameAndEnglishNameFromDatabase() {
        assertThat(repository.search("086790")).extracting("stockName").containsExactly("하나금융지주");
        assertThat(repository.search("하이닉스")).extracting("stockCode").containsExactly("000660");
        assertThat(repository.search("energy")).extracting("stockCode").contains("373220");
    }

    @Test
    void findAllUsesSeedPriorityForMarketCapTopUniverse() {
        assertThat(repository.findAll(5))
                .extracting("stockCode")
                .containsExactly("005930", "000660", "005380", "000270", "086790");
    }

    @Test
    void seedLoaderDoesNotDuplicateRowsWhenTableAlreadyHasData() throws Exception {
        Integer before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM stock_master", Integer.class);

        seedLoader.run(new DefaultApplicationArguments());

        Integer after = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM stock_master", Integer.class);
        assertThat(after).isEqualTo(before);
    }

    @Test
    void upsertKeepsExistingEnglishNameWhenKisMasterHasOnlyKoreanName() {
        jdbcRepository.upsertAll(List.of(new com.hana.omnilens.market.domain.StockSummary(
                "005930",
                "삼성전자",
                "삼성전자",
                "KOSPI",
                "KR7005930003",
                "")));

        assertThat(repository.findByCode("005930")).isPresent()
                .get()
                .extracting("stockNameEn")
                .isEqualTo("Samsung Electronics");
    }

    @Test
    void updateDartCorpCodesFillsOnlyMissingValues() {
        jdbcTemplate.update("UPDATE stock_master SET dart_corp_code = '' WHERE stock_code = '030200'");
        jdbcTemplate.update("UPDATE stock_master SET dart_corp_code = '00126380' WHERE stock_code = '005930'");

        int updated = jdbcRepository.updateDartCorpCodes(Map.of(
                "030200", "00190321",
                "005930", "99999999"));

        assertThat(updated).isGreaterThanOrEqualTo(1);
        assertThat(repository.findByCode("030200")).isPresent()
                .get()
                .extracting("dartCorpCode")
                .isEqualTo("00190321");
        assertThat(repository.findByCode("005930")).isPresent()
                .get()
                .extracting("dartCorpCode")
                .isEqualTo("00126380");
    }

    @Test
    void updatePreferredShareDartCorpCodesFromCommonShares() {
        jdbcRepository.upsertAll(List.of(
                new com.hana.omnilens.market.domain.StockSummary(
                        "003490",
                        "대한항공",
                        "Korean Air",
                        "KOSPI",
                        "KR7003490000",
                        "00113526"),
                new com.hana.omnilens.market.domain.StockSummary(
                        "003495",
                        "대한항공우",
                        "Korean Air Preferred",
                        "KOSPI",
                        "KR7003491008",
                        "")));

        jdbcRepository.updatePreferredShareDartCorpCodesFromCommonShares();

        assertThat(repository.findByCode("003495")).isPresent()
                .get()
                .extracting("dartCorpCode")
                .isEqualTo("00113526");
    }
}
