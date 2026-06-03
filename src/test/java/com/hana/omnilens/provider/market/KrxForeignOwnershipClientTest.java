package com.hana.omnilens.provider.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.ExternalProviderProperties;

class KrxForeignOwnershipClientTest {

    @Test
    void findForeignOwnershipMapsKrxOutput() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KrxForeignOwnershipClient client = new KrxForeignOwnershipClient(builder, properties());

        server.expect(requestTo(containsString("/comm/bldAttendant/getJsonData.cmd")))
                .andExpect(header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(content().string(containsString("bld=dbms%2FMDC%2FSTAT%2Fstandard%2FMDCSTAT03702")))
                .andExpect(content().string(containsString("trdDd=20250604")))
                .andExpect(content().string(containsString("isuCd=KR7005930003")))
                .andRespond(withSuccess("""
                        {
                          "output": [
                            {
                              "ISU_SRT_CD": "005930",
                              "FORN_HD_QTY": "3,642,091,300",
                              "FORN_SHR_RT": "54.19",
                              "FORN_ORD_LMT_QTY": "6,720,000,000",
                              "FORN_ORD_LMT_RT": "54.21"
                            }
                          ]
                        }
                        """, APPLICATION_JSON));

        Optional<KrxForeignOwnershipSnapshot> snapshot = client.findForeignOwnership(
                "005930",
                "삼성전자",
                "KR7005930003",
                LocalDate.of(2025, 6, 4));

        assertThat(snapshot).isPresent();
        assertThat(snapshot.orElseThrow().foreignOwnedQuantity()).isEqualTo(3_642_091_300L);
        assertThat(snapshot.orElseThrow().foreignOwnershipRate()).isEqualByComparingTo("54.19");
        assertThat(snapshot.orElseThrow().foreignLimitQuantity()).isEqualTo(6_720_000_000L);
        assertThat(snapshot.orElseThrow().foreignLimitExhaustionRate()).isEqualByComparingTo("54.21");
        server.verify();
    }

    private ExternalProviderProperties properties() {
        return new ExternalProviderProperties(
                null,
                null,
                null,
                new ExternalProviderProperties.Krx(URI.create("https://data.krx.co.kr")));
    }
}
