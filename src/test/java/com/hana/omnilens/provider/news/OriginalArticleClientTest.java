package com.hana.omnilens.provider.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.provider.ProviderTestResilience;

class OriginalArticleClientTest {

    @Test
    void fetchExtractsArticleTextCanonicalUrlAndImages() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OriginalArticleClient client = new OriginalArticleClient(builder, ProviderTestResilience.disabled());

        server.expect(requestTo("https://news.example.com/article"))
                .andRespond(withSuccess("""
                        <html>
                          <head>
                            <link rel="canonical" href="https://news.example.com/canonical">
                            <meta property="og:image" content="/images/main.jpg">
                          </head>
                          <body>
                            <nav>메뉴</nav>
                            <article>
                              <h1>삼성전자 실적 개선</h1>
                              <p>삼성전자는 AI 서버 투자 확대로 반도체 실적 개선 기대가 커졌다.</p>
                              <p>메모리 가격 반등과 HBM 공급 확대가 주요 배경이다.</p>
                              <p>투자자는 영업이익 회복 속도와 수요 지속성을 확인해야 한다.</p>
                            </article>
                          </body>
                        </html>
                        """, MediaType.parseMediaType("text/html;charset=UTF-8")));

        Optional<OriginalArticleContent> content = client.fetch("https://news.example.com/article");

        assertThat(content).isPresent();
        assertThat(content.orElseThrow().content()).contains("AI 서버 투자 확대");
        assertThat(content.orElseThrow().canonicalUrl()).isEqualTo("https://news.example.com/canonical");
        assertThat(content.orElseThrow().imageUrls()).containsExactly("https://news.example.com/images/main.jpg");
        assertThat(content.orElseThrow().sourceLicensePolicy())
                .isEqualTo(OriginalArticleClient.LICENSED_NAVER_ORIGINAL_FULL_TEXT);
        assertThat(content.orElseThrow().contentHash()).hasSize(64);
        server.verify();
    }

    @Test
    void fetchRejectsLocalhostUrl() {
        OriginalArticleClient client = new OriginalArticleClient(
                RestClient.builder(),
                ProviderTestResilience.disabled());

        assertThat(client.fetch("http://localhost/internal")).isEmpty();
    }
}
