package com.hana.omnilens.provider.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.ExternalProviderProperties;
import com.hana.omnilens.provider.ProviderTestResilience;

class KisIndexCurrentPriceClientTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-02T09:59:00Z"),
            ZoneId.of("Asia/Seoul"));

    @Test
    void findCurrentIndexUsesKisIndexQuoteContract() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisIndexCurrentPriceClient client = new KisIndexCurrentPriceClient(
                builder,
                properties(),
                new KisAccessTokenProvider(builder, properties(), ProviderTestResilience.disabled()),
                ProviderTestResilience.disabled(),
                FIXED_CLOCK);

        server.expect(requestTo(containsString("/oauth2/tokenP")))
                .andRespond(withSuccess("""
                        {
                          "access_token": "issued-kis-token",
                          "expires_in": 86400
                        }
                        """, APPLICATION_JSON));
        server.expect(requestTo(containsString("/uapi/domestic-stock/v1/quotations/inquire-index-price")))
                .andExpect(requestTo(containsString("FID_COND_MRKT_DIV_CODE=U")))
                .andExpect(requestTo(containsString("FID_INPUT_ISCD=0001")))
                .andExpect(header("authorization", "Bearer issued-kis-token"))
                .andExpect(header("appkey", "kis-app-key"))
                .andExpect(header("appsecret", "kis-app-secret"))
                .andExpect(header("tr_id", "FHPUP02100000"))
                .andRespond(withSuccess("""
                        {
                          "rt_cd": "0",
                          "msg_cd": "MCA00000",
                          "output": {
                            "hts_kor_isnm": "KOSPI",
                            "bstp_nmix_prpr": "7,648.09",
                            "prdy_vrss_sign": "5",
                            "bstp_nmix_prdy_vrss": "655.32",
                            "bstp_nmix_prdy_ctrt": "7.89",
                            "acml_vol": "922000",
                            "acml_tr_pbmn": "12340000000",
                            "bstp_nmix_oprc": "8,300.00",
                            "bstp_nmix_hgpr": "8,400.00",
                            "bstp_nmix_lwpr": "7,648.09"
                          }
                        }
                        """, APPLICATION_JSON));

        Optional<KisIndexCurrentPriceSnapshot> snapshot = client.findCurrentIndex("0001");

        assertThat(snapshot).isPresent();
        assertThat(snapshot.orElseThrow().indexName()).isEqualTo("KOSPI");
        assertThat(snapshot.orElseThrow().currentValue()).isEqualByComparingTo("7648.09");
        assertThat(snapshot.orElseThrow().changeSign()).isEqualTo("5");
        assertThat(snapshot.orElseThrow().changeValue()).isEqualByComparingTo("-655.32");
        assertThat(snapshot.orElseThrow().changeRate()).isEqualByComparingTo("-7.89");
        assertThat(snapshot.orElseThrow().accumulatedVolume()).isEqualTo(922_000L);
        assertThat(snapshot.orElseThrow().accumulatedTradingValue()).isEqualTo(12_340_000_000L);
        assertThat(snapshot.orElseThrow().source()).isEqualTo("KIS_INDEX_CURRENT_PRICE");
        assertThat(snapshot.orElseThrow().marketDataTime()).isEqualTo(FIXED_CLOCK.instant());
        server.verify();
    }

    @Test
    void findCurrentIndexSkipsWhenOnlyVirtualTradingProviderHasNoCredential() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ExternalProviderProperties properties = properties(vtsKisWithoutCredential(), null);
        KisIndexCurrentPriceClient client = new KisIndexCurrentPriceClient(
                builder,
                properties,
                new KisAccessTokenProvider(builder, properties, ProviderTestResilience.disabled()),
                ProviderTestResilience.disabled(),
                FIXED_CLOCK);

        Optional<KisIndexCurrentPriceSnapshot> snapshot = client.findCurrentIndex("0001");

        assertThat(snapshot).isEmpty();
        server.verify();
    }

    @Test
    void findCurrentIndexPrefersRealKisWhenPrimaryProviderIsVirtualTrading() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ExternalProviderProperties properties = properties(vtsKis(), realKis());
        KisIndexCurrentPriceClient client = new KisIndexCurrentPriceClient(
                builder,
                properties,
                new KisAccessTokenProvider(builder, properties, ProviderTestResilience.disabled()),
                ProviderTestResilience.disabled(),
                FIXED_CLOCK);

        server.expect(requestTo(containsString("openapi.koreainvestment.com:9443/oauth2/tokenP")))
                .andRespond(withSuccess("""
                        {
                          "access_token": "issued-real-token",
                          "expires_in": 86400
                        }
                        """, APPLICATION_JSON));
        server.expect(requestTo(containsString("openapi.koreainvestment.com:9443")))
                .andExpect(requestTo(containsString("/uapi/domestic-stock/v1/quotations/inquire-index-price")))
                .andExpect(header("authorization", "Bearer issued-real-token"))
                .andExpect(header("appkey", "real-kis-app-key"))
                .andExpect(header("appsecret", "real-kis-app-secret"))
                .andRespond(withSuccess("""
                        {
                          "rt_cd": "0",
                          "output": {
                            "hts_kor_isnm": "KOSPI",
                            "bstp_nmix_prpr": "2,891.12"
                          }
                        }
                        """, APPLICATION_JSON));

        Optional<KisIndexCurrentPriceSnapshot> snapshot = client.findCurrentIndex("0001");

        assertThat(snapshot).isPresent();
        assertThat(snapshot.orElseThrow().currentValue()).isEqualByComparingTo("2891.12");
        server.verify();
    }

    @Test
    void findCurrentIndexUsesPrimaryCredentialWithRealEndpointWhenRealKisCredentialIsEmpty() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ExternalProviderProperties properties = properties(vtsKisWithoutPreissuedKeys(), realEndpointOnlyKis());
        KisIndexCurrentPriceClient client = new KisIndexCurrentPriceClient(
                builder,
                properties,
                new KisAccessTokenProvider(builder, properties, ProviderTestResilience.disabled()),
                ProviderTestResilience.disabled(),
                FIXED_CLOCK);

        server.expect(requestTo(containsString("openapi.koreainvestment.com:9443/oauth2/tokenP")))
                .andRespond(withSuccess("""
                        {
                          "access_token": "issued-real-token",
                          "expires_in": 86400
                        }
                        """, APPLICATION_JSON));
        server.expect(requestTo(containsString("openapi.koreainvestment.com:9443")))
                .andExpect(requestTo(containsString("/uapi/domestic-stock/v1/quotations/inquire-index-price")))
                .andExpect(header("authorization", "Bearer issued-real-token"))
                .andExpect(header("appkey", "vts-kis-app-key"))
                .andExpect(header("appsecret", "vts-kis-app-secret"))
                .andRespond(withSuccess("""
                        {
                          "rt_cd": "0",
                          "output": {
                            "hts_kor_isnm": "KOSPI",
                            "bstp_nmix_prpr": "2,891.12"
                          }
                        }
                        """, APPLICATION_JSON));

        Optional<KisIndexCurrentPriceSnapshot> snapshot = client.findCurrentIndex("0001");

        assertThat(snapshot).isPresent();
        assertThat(snapshot.orElseThrow().currentValue()).isEqualByComparingTo("2891.12");
        server.verify();
    }

    private ExternalProviderProperties properties() {
        return properties(primaryRealKis(), null);
    }

    private ExternalProviderProperties properties(
            ExternalProviderProperties.Kis kis,
            ExternalProviderProperties.Kis realKis) {
        return new ExternalProviderProperties(
                null,
                null,
                null,
                null,
                kis,
                realKis,
                null);
    }

    private ExternalProviderProperties.Kis realKis() {
        return new ExternalProviderProperties.Kis(
                URI.create("https://openapi.koreainvestment.com:9443"),
                URI.create("ws://ops.koreainvestment.com:21000"),
                "00000000",
                "real-kis-app-key",
                "real-kis-app-secret",
                "real-kis-access-token",
                "real-kis-approval-key");
    }

    private ExternalProviderProperties.Kis primaryRealKis() {
        return new ExternalProviderProperties.Kis(
                URI.create("https://openapi.koreainvestment.com:9443"),
                URI.create("ws://ops.koreainvestment.com:21000"),
                "00000000",
                "kis-app-key",
                "kis-app-secret",
                "kis-access-token",
                "kis-approval-key");
    }

    private ExternalProviderProperties.Kis vtsKis() {
        return new ExternalProviderProperties.Kis(
                URI.create("https://openapivts.koreainvestment.com:29443"),
                URI.create("ws://ops.koreainvestment.com:31000"),
                "00000000",
                "vts-kis-app-key",
                "vts-kis-app-secret",
                "vts-kis-access-token",
                "vts-kis-approval-key");
    }

    private ExternalProviderProperties.Kis vtsKisWithoutPreissuedKeys() {
        return new ExternalProviderProperties.Kis(
                URI.create("https://openapivts.koreainvestment.com:29443"),
                URI.create("ws://ops.koreainvestment.com:31000"),
                "00000000",
                "vts-kis-app-key",
                "vts-kis-app-secret",
                "",
                "");
    }

    private ExternalProviderProperties.Kis vtsKisWithoutCredential() {
        return new ExternalProviderProperties.Kis(
                URI.create("https://openapivts.koreainvestment.com:29443"),
                URI.create("ws://ops.koreainvestment.com:31000"),
                "00000000",
                "",
                "",
                "",
                "");
    }

    private ExternalProviderProperties.Kis realEndpointOnlyKis() {
        return new ExternalProviderProperties.Kis(
                URI.create("https://openapi.koreainvestment.com:9443"),
                URI.create("ws://ops.koreainvestment.com:21000"),
                "",
                "",
                "",
                "",
                "");
    }
}
