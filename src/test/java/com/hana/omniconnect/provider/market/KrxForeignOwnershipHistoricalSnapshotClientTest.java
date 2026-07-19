package com.hana.omniconnect.provider.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.TEXT_HTML;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hana.omniconnect.config.ExternalProviderProperties;
import com.hana.omniconnect.market.domain.StockSummary;
import com.hana.omniconnect.provider.ProviderTestResilience;

class KrxForeignOwnershipHistoricalSnapshotClientTest {

    @Test
    void logsInAndMapsForeignOwnershipHistoryRows() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KrxForeignOwnershipHistoricalSnapshotClient client = new KrxForeignOwnershipHistoricalSnapshotClient(
                builder,
                properties(),
                ProviderTestResilience.disabled(),
                new ObjectMapper());

        server.expect(requestTo("https://data.krx.example/contents/MDC/COMS/client/MDCCOMS001D1.cmd"))
                .andExpect(content().string(containsString("mbrId=test-user")))
                .andExpect(content().string(containsString("pw=test-password")))
                .andExpect(content().string(containsString("site=mdc")))
                .andRespond(withSuccess("""
                        {"MBR_NO":"100000","previousMemberYn":false}
                        """, TEXT_HTML)
                        .header(HttpHeaders.SET_COOKIE, "JSESSIONID=session-1; Path=/; HttpOnly"));
        server.expect(requestTo("https://data.krx.example/comm/bldAttendant/getJsonData.cmd"))
                .andExpect(header(HttpHeaders.COOKIE, containsString("JSESSIONID=session-1")))
                .andExpect(content().string(containsString("bld=dbms%2FMDC%2FSTAT%2Fstandard%2FMDCSTAT03702")))
                .andExpect(content().string(containsString("searchType=2")))
                .andExpect(content().string(containsString("strtDd=20250602")))
                .andExpect(content().string(containsString("endDd=20250604")))
                .andExpect(content().string(containsString("isuCd=KR7005930003")))
                .andRespond(withSuccess("""
                        {
                          "output": [
                            {
                              "TRD_DD": "2025/06/02",
                              "LIST_SHRS": "5,969,782,550",
                              "FORN_HD_QTY": "3,642,091,300",
                              "FORN_SHR_RT": "61.01",
                              "FORN_ORD_LMT_QTY": "6,718,486,073",
                              "FORN_LMT_EXHST_RT": "54.21"
                            },
                            {
                              "TRD_DD": "20250604",
                              "LIST_SHRS": "5,969,782,550",
                              "FORN_HD_QTY": "3,650,000,000",
                              "FORN_SHR_RT": "61.14",
                              "FORN_ORD_LMT_QTY": "6,718,486,073",
                              "FORN_LMT_EXHST_RT": "54.33"
                            }
                          ]
                        }
                        """, TEXT_HTML));

        List<ForeignOwnershipSnapshot> snapshots = client.findSnapshots(
                stock(),
                LocalDate.of(2025, 6, 2),
                LocalDate.of(2025, 6, 4));

        assertThat(snapshots).hasSize(2);
        assertThat(snapshots.get(0).stockCode()).isEqualTo("005930");
        assertThat(snapshots.get(0).baseDate()).isEqualTo(LocalDate.of(2025, 6, 2));
        assertThat(snapshots.get(0).foreignOwnedQuantity()).isEqualTo(3_642_091_300L);
        assertThat(snapshots.get(0).foreignOwnershipRate()).isEqualByComparingTo("61.01");
        assertThat(snapshots.get(0).foreignLimitQuantity()).isEqualTo(6_718_486_073L);
        assertThat(snapshots.get(0).foreignLimitExhaustionRate()).isEqualByComparingTo("54.21");
        assertThat(snapshots.get(1).baseDate()).isEqualTo(LocalDate.of(2025, 6, 4));
        server.verify();
    }

    private ExternalProviderProperties properties() {
        return new ExternalProviderProperties(
                null,
                null,
                null,
                new ExternalProviderProperties.Krx(
                        URI.create("https://data.krx.example"),
                        true,
                        "test-user",
                        "test-password",
                        "/contents/MDC/COMS/client/MDCCOMS001D1.cmd",
                        "dbms/MDC/STAT/standard/MDCSTAT03702"),
                null,
                null);
    }

    private StockSummary stock() {
        return new StockSummary(
                "005930",
                "삼성전자",
                "Samsung Electronics",
                "KOSPI",
                "KR7005930003",
                "00126380");
    }
}
