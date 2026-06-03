package com.hana.omnilens.provider.disclosure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.ExternalProviderProperties;

class OpenDartDisclosureClientTest {

    @Test
    void searchMapsDisclosureList() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenDartDisclosureClient client = new OpenDartDisclosureClient(builder, properties());

        server.expect(requestTo(containsString("/api/list.json")))
                .andExpect(requestTo(containsString("corp_code=00126380")))
                .andExpect(requestTo(containsString("bgn_de=20250601")))
                .andExpect(requestTo(containsString("end_de=20250604")))
                .andRespond(withSuccess("""
                        {
                          "status": "000",
                          "list": [
                            {
                              "rcept_no": "20250604000123",
                              "corp_name": "삼성전자",
                              "report_nm": "주요사항보고서",
                              "rcept_dt": "20250604"
                            }
                          ]
                        }
                        """, APPLICATION_JSON));

        List<OpenDartDisclosure> disclosures = client.search(
                "00126380",
                LocalDate.of(2025, 6, 1),
                LocalDate.of(2025, 6, 4));

        assertThat(disclosures).hasSize(1);
        assertThat(disclosures.get(0).receiptNumber()).isEqualTo("20250604000123");
        assertThat(disclosures.get(0).corporationName()).isEqualTo("삼성전자");
        assertThat(disclosures.get(0).receivedAt()).isEqualTo(LocalDate.of(2025, 6, 4));
        assertThat(disclosures.get(0).originalUrl())
                .isEqualTo("https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20250604000123");
        server.verify();
    }

    private ExternalProviderProperties properties() {
        return new ExternalProviderProperties(
                null,
                null,
                new ExternalProviderProperties.OpenDart(
                        URI.create("https://opendart.fss.or.kr"),
                        "dart-api-key"),
                null,
                null,
                null);
    }
}
