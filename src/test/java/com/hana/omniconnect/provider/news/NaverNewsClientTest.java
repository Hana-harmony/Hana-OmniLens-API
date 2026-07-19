package com.hana.omniconnect.provider.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hana.omniconnect.config.ExternalProviderProperties;
import com.hana.omniconnect.provider.ProviderTestResilience;

class NaverNewsClientTest {

    @Test
    void searchMapsSanitizedNewsItems() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NaverNewsClient client = new NaverNewsClient(builder, properties(), ProviderTestResilience.disabled());

        server.expect(requestTo(containsString("/v1/search/news.json")))
                .andExpect(requestTo(containsString("query=Samsung")))
                .andExpect(header("X-Naver-Client-Id", "naver-client"))
                .andExpect(header("X-Naver-Client-Secret", "naver-secret"))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {
                              "title": "<b>삼성전자</b> 실적 개선",
                              "originallink": "https://news.example.com/original",
                              "link": "https://search.example.com/item",
                              "description": "반도체 &quot;회복&quot; 기대",
                              "pubDate": "Wed, 04 Jun 2025 10:15:30 +0900"
                            }
                          ]
                        }
                        """, APPLICATION_JSON));

        List<NaverNewsArticle> articles = client.search("Samsung", 10);

        assertThat(articles).hasSize(1);
        assertThat(articles.get(0).title()).isEqualTo("삼성전자 실적 개선");
        assertThat(articles.get(0).snippet()).isEqualTo("반도체 \"회복\" 기대");
        assertThat(articles.get(0).originalUrl()).isEqualTo("https://news.example.com/original");
        assertThat(articles.get(0).publishedAt()).isEqualTo(Instant.parse("2025-06-04T01:15:30Z"));
        server.verify();
    }

    private ExternalProviderProperties properties() {
        return new ExternalProviderProperties(
                null,
                new ExternalProviderProperties.NaverNews(
                        URI.create("https://openapi.naver.com"),
                        "naver-client",
                        "naver-secret"),
                null,
                null,
                null,
                null);
    }
}
