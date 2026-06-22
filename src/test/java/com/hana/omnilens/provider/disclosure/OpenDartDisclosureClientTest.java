package com.hana.omnilens.provider.disclosure;

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

import com.hana.omnilens.config.ExternalProviderProperties;
import com.hana.omnilens.provider.ProviderTestResilience;

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

    private byte[] zip(String xml) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            zipOutputStream.putNextEntry(new ZipEntry("document.xml"));
            zipOutputStream.write(xml.getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
        }
        return outputStream.toByteArray();
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
