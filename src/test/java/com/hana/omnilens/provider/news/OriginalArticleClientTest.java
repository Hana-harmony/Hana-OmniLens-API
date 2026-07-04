package com.hana.omnilens.provider.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
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
    void fetchExtractsPublisherMetaAndLazyArticleImages() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OriginalArticleClient client = new OriginalArticleClient(builder, ProviderTestResilience.disabled());

        server.expect(requestTo("https://news.example.com/wowtv"))
                .andRespond(withSuccess("""
                        <html>
                          <head>
                            <meta property="og:image:secure_url" content="https://img.example.com/news/main.png">
                            <meta name="twitter:image:src" content="https://static.example.com/logo.png">
                          </head>
                          <body>
                            <div class="section-content" itemprop="articleBody">
                            <div id="divNewsContent" class="box-news-body useAd">
                              <p>삼전닉스 수익률이 반년 동안 크게 올랐다는 기사 본문입니다.</p>
                              <p>외국인 순매수와 반도체 업황 개선 기대가 주요 배경으로 제시됐습니다.</p>
                              <p>투자자는 반도체 대형주의 수급 변화를 확인해야 한다는 내용입니다.</p>
                              <div class="adv-area">광고 문구</div>
                              <img data-src="/news/lazy.jpg" />
                              <img srcset="/news/small.jpg 480w, /news/large.jpg 960w" />
                              <img src="/assets/share-icon.png" />
                              <img src="/PcStyle/images/common/btn_quick_top.png" />
                            </div>
                            </div>
                            <aside>
                              <img src="https://img.wownet.co.kr/wownet20/partner/thumbnail/P315/13_200x130.png" />
                            </aside>
                          </body>
                        </html>
                        """, MediaType.parseMediaType("text/html;charset=UTF-8")));

        Optional<OriginalArticleContent> content = client.fetch("https://news.example.com/wowtv");

        assertThat(content).isPresent();
        assertThat(content.orElseThrow().content()).contains("삼전닉스 수익률");
        assertThat(content.orElseThrow().content()).doesNotContain("광고 문구");
        assertThat(content.orElseThrow().imageUrls()).containsExactly(
                "https://img.example.com/news/main.png",
                "https://news.example.com/news/lazy.jpg",
                "https://news.example.com/news/small.jpg",
                "https://news.example.com/news/large.jpg");
        server.verify();
    }

    @Test
    void fetchFollowsSafePublisherRedirectBeforeParsing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OriginalArticleClient client = new OriginalArticleClient(builder, ProviderTestResilience.disabled());

        server.expect(requestTo("http://news.example.com/article"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.SEE_OTHER)
                        .header("Location", "https://news.example.com/article"));
        server.expect(requestTo("https://news.example.com/article"))
                .andRespond(withSuccess("""
                        <html>
                          <head>
                            <meta itemprop="thumbnailUrl" content="/images/redirected.png">
                          </head>
                          <body>
                            <div id="divNewsContent" class="box-news-body useAd">
                              <p>리다이렉트된 원문 본문입니다.</p>
                              <p>외국인 순매수가 반도체 대형주 강세를 이끌었다.</p>
                              <p>투자자는 수급과 실적 기대를 확인해야 한다.</p>
                            </div>
                          </body>
                        </html>
                        """, MediaType.parseMediaType("text/html;charset=UTF-8")));

        Optional<OriginalArticleContent> content = client.fetch("http://news.example.com/article");

        assertThat(content).isPresent();
        assertThat(content.orElseThrow().content()).contains("리다이렉트된 원문 본문");
        assertThat(content.orElseThrow().imageUrls())
                .containsExactly("https://news.example.com/images/redirected.png");
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
