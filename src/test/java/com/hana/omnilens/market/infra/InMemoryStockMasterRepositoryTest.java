package com.hana.omnilens.market.infra;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InMemoryStockMasterRepositoryTest {

    private final InMemoryStockMasterRepository repository = new InMemoryStockMasterRepository();

    @Test
    void searchMatchesCodeKoreanNameAndEnglishName() {
        assertThat(repository.search("005930")).extracting("stockName").containsExactly("삼성전자");
        assertThat(repository.search("하이닉스")).extracting("stockCode").containsExactly("000660");
        assertThat(repository.search("hyundai")).extracting("stockCode").containsExactly("005380");
    }
}
