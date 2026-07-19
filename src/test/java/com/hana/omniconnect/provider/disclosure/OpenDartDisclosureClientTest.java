package com.hana.omniconnect.provider.disclosure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hana.omniconnect.config.ExternalProviderProperties;
import com.hana.omniconnect.provider.ProviderTestResilience;

class OpenDartDisclosureClientTest {

    @Test
    void fetchDocumentContentExtractsZipXmlText() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenDartDisclosureClient client = new OpenDartDisclosureClient(
                builder,
                properties(),
                ProviderTestResilience.disabled());

        server.expect(requestTo("https://opendart.fss.or.kr/api/document.xml?crtfc_key=dart-secret&rcept_no=20260604000123"))
                .andRespond(withSuccess(zip("""
                        <DOCUMENT>
                          <SECTION>삼성전자 주요사항보고서 전문이다.</SECTION>
                          <SECTION>자기주식 취득과 소각 결정으로 주주환원 영향이 있다.</SECTION>
                        </DOCUMENT>
                        """), APPLICATION_OCTET_STREAM));

        Optional<OpenDartDisclosureDocument> document =
                client.fetchDocumentContent("20260604000123");

        assertThat(document).isPresent();
        assertThat(document.orElseThrow().content()).contains("자기주식 취득과 소각 결정");
        assertThat(document.orElseThrow().contentHash()).hasSize(64);
        assertThat(document.orElseThrow().sourceLicensePolicy())
                .isEqualTo(OpenDartDisclosureClient.OPENDART_PUBLIC_DISCLOSURE_TEXT);
        server.verify();
    }

    @Test
    void fetchDocumentContentChoosesLongestDocumentEntry() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenDartDisclosureClient client = new OpenDartDisclosureClient(
                builder,
                properties(),
                ProviderTestResilience.disabled());

        server.expect(requestTo("https://opendart.fss.or.kr/api/document.xml?crtfc_key=dart-secret&rcept_no=20260604000456"))
                .andRespond(withSuccess(zip(
                        new ZipPart("cover.xml", "<DOCUMENT>정정신고서</DOCUMENT>"),
                        new ZipPart("body.xml", """
                                <DOCUMENT>
                                  <SECTION>SK하이닉스 증권신고서 본문이다.</SECTION>
                                  <SECTION>DR 발행금액과 SEC F-1 정정 사유가 포함되어 있다.</SECTION>
                                  <SECTION>투자자가 확인해야 하는 공시 원문 세부사항이다.</SECTION>
                                </DOCUMENT>
                                """)),
                        APPLICATION_OCTET_STREAM));

        Optional<OpenDartDisclosureDocument> document =
                client.fetchDocumentContent("20260604000456");

        assertThat(document).isPresent();
        assertThat(document.orElseThrow().content()).contains("DR 발행금액과 SEC F-1 정정 사유");
        assertThat(document.orElseThrow().content()).contains("\n\n");
        assertThat(document.orElseThrow().content()).doesNotContain("정정신고서 정정신고서");
        server.verify();
    }

    @Test
    void fetchDocumentContentPreservesDocumentBodyAboveLegacySixtyThousandChars() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenDartDisclosureClient client = new OpenDartDisclosureClient(
                builder,
                properties(),
                ProviderTestResilience.disabled());
        String longBody = "삼성전자는 임원 주요주주 특정증권 소유상황과 변동 사유를 공시 원문에서 설명했다. ".repeat(1_700);

        server.expect(requestTo("https://opendart.fss.or.kr/api/document.xml?crtfc_key=dart-secret&rcept_no=20260604000789"))
                .andRespond(withSuccess(zip("""
                        <DOCUMENT>
                          <SECTION>%s</SECTION>
                        </DOCUMENT>
                        """.formatted(longBody)), APPLICATION_OCTET_STREAM));

        Optional<OpenDartDisclosureDocument> document =
                client.fetchDocumentContent("20260604000789");

        assertThat(longBody.length()).isGreaterThan(60_000);
        assertThat(document).isPresent();
        assertThat(document.orElseThrow().content()).hasSizeGreaterThan(60_000);
        assertThat(document.orElseThrow().content()).endsWith("설명했다.");
        server.verify();
    }

    @Test
    void listedCorpCodesByStockCodeExtractsListedRowsFromOpenDartZip() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenDartDisclosureClient client = new OpenDartDisclosureClient(
                builder,
                properties(),
                ProviderTestResilience.disabled());

        server.expect(requestTo("https://opendart.fss.or.kr/api/corpCode.xml?crtfc_key=dart-secret"))
                .andRespond(withSuccess(zip(new ZipPart("CORPCODE.xml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <result>
                          <list>
                            <corp_code>00126380</corp_code>
                            <corp_name>삼성전자</corp_name>
                            <stock_code>005930</stock_code>
                            <modify_date>20260709</modify_date>
                          </list>
                          <list>
                            <corp_code>00190321</corp_code>
                            <corp_name>KT</corp_name>
                            <stock_code>030200</stock_code>
                            <modify_date>20260709</modify_date>
                          </list>
                          <list>
                            <corp_code>00434003</corp_code>
                            <corp_name>비상장회사</corp_name>
                            <stock_code> </stock_code>
                            <modify_date>20260709</modify_date>
                          </list>
                        </result>
                        """)), APPLICATION_OCTET_STREAM));

        assertThat(client.listedCorpCodesByStockCode())
                .containsEntry("005930", "00126380")
                .containsEntry("030200", "00190321")
                .doesNotContainKey("");
        server.verify();
    }

    private byte[] zip(String xml) throws Exception {
        return zip(new ZipPart("document.xml", xml));
    }

    private byte[] zip(ZipPart... parts) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (ZipPart part : parts) {
                zipOutputStream.putNextEntry(new ZipEntry(part.name()));
                zipOutputStream.write(part.xml().getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
        }
        return outputStream.toByteArray();
    }

    private record ZipPart(String name, String xml) {
    }

    private ExternalProviderProperties properties() {
        return new ExternalProviderProperties(
                null,
                null,
                new ExternalProviderProperties.OpenDart(
                        URI.create("https://opendart.fss.or.kr"),
                        "dart-secret"),
                null,
                null,
                null);
    }
}
