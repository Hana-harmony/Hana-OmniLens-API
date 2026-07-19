package com.hana.omniconnect.alert.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hana.omniconnect.alert.application.PartnerWatchlistRepository;

@SpringBootTest(properties = {
        "omni-connect.alert.dedupe.mode=in-memory",
        "omni-connect.market.exchange-rate-cache.mode=in-memory",
        "omni-connect.market.foreign-ownership-cache.mode=in-memory"
})
class JdbcPartnerWatchlistRepositoryTest {

    @Autowired
    private PartnerWatchlistRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void deleteWatchlists() {
        jdbcTemplate.update("DELETE FROM partner_watchlist_subscription");
    }

    @Test
    void replaceStoresPartnerWatchlistWithRequestOrder() {
        List<String> savedStockCodes = repository.replace(
                "partner-a",
                List.of("035420", "005930", "000660"));

        assertThat(savedStockCodes).containsExactly("035420", "005930", "000660");
        assertThat(repository.findStockCodes("partner-a"))
                .containsExactly("035420", "005930", "000660");
    }

    @Test
    void replaceRemovesOldPartnerRows() {
        repository.replace("partner-a", List.of("005930", "000660"));

        List<String> savedStockCodes = repository.replace("partner-a", List.of("035420"));

        assertThat(savedStockCodes).containsExactly("035420");
        assertThat(repository.findStockCodes("partner-a")).containsExactly("035420");
    }

    @Test
    void findAllGroupsRowsByPartner() {
        repository.replace("partner-a", List.of("005930", "000660"));
        repository.replace("partner-b", List.of("035420"));

        assertThat(repository.findAll())
                .extracting("partnerId")
                .containsExactly("partner-a", "partner-b");
        assertThat(repository.findAll().get(0).stockCodes())
                .containsExactly("005930", "000660");
        assertThat(repository.findAll().get(1).stockCodes())
                .containsExactly("035420");
    }

    @Test
    void emptyReplaceClearsPartnerWatchlist() {
        repository.replace("partner-a", List.of("005930"));

        List<String> savedStockCodes = repository.replace("partner-a", List.of());

        assertThat(savedStockCodes).isEmpty();
        assertThat(repository.findStockCodes("partner-a")).isEmpty();
    }
}
