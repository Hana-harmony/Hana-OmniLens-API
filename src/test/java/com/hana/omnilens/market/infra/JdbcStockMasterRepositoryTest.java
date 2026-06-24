package com.hana.omnilens.market.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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

        assertThat(count).isNotNull().isGreaterThanOrEqualTo(30);
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
    void searchMatchesCodeKoreanNameAndEnglishNameFromDatabase() {
        assertThat(repository.search("086790")).extracting("stockName").containsExactly("하나금융지주");
        assertThat(repository.search("하이닉스")).extracting("stockCode").containsExactly("000660");
        assertThat(repository.search("energy")).extracting("stockCode").contains("373220");
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
}
