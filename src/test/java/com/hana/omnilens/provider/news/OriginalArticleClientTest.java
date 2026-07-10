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
    void fetchRemovesHiddenStructuredStockMetadataFromArticleWrapper() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OriginalArticleClient client = new OriginalArticleClient(builder, ProviderTestResilience.disabled());

        server.expect(requestTo("https://news.example.com/hidden-stock-tags"))
                .andRespond(withSuccess("""
                        <html><body>
                          <div class="article-content">
                            <div id="stockCodes" style="display:none;">
                              [{"CODE_TYPE":"T","CODE_ID":"126560","CODE_NM":"현대퓨처넷"}]
                            </div>
                            <div id="article-body" class="view" itemprop="articleBody">
                              <p>현대퓨처넷은 4월 9일 국내 기관투자자를 대상으로 IR을 개최한다.</p>
                              <p>회사는 경영 실적과 주요 사업 현황을 설명하고 질의에 답변할 예정이다.</p>
                              <p>투자자는 실적 개선과 사업 재편 진행 상황을 확인해야 한다.</p>
                            </div>
                          </div>
                        </body></html>
                        """, MediaType.parseMediaType("text/html;charset=UTF-8")));

        Optional<OriginalArticleContent> content = client.fetch(
                "https://news.example.com/hidden-stock-tags");

        assertThat(content).isPresent();
        assertThat(content.orElseThrow().content()).contains("현대퓨처넷은 4월 9일");
        assertThat(content.orElseThrow().content()).doesNotContain("CODE_TYPE", "CODE_ID");
        server.verify();
    }

    @Test
    void fetchRemovesArticleUiBoilerplateBeforeTranslation() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OriginalArticleClient client = new OriginalArticleClient(builder, ProviderTestResilience.disabled());

        server.expect(requestTo("https://news.example.com/ui-boilerplate"))
                .andRespond(withSuccess("""
                        <html>
                          <head>
                            <meta property="og:title" content="삼성전자·SK하이닉스 장 초반 강세">
                          </head>
                          <body>
                            <article>
                              <p>가 가 기사의 본문 내용은 이 글자크기로 변경됩니다.</p>
                              <p>삼성전자와 SK하이닉스가 미국 반도체 랠리 영향으로 장 초반 강세를 보였다.</p>
                              <p>삼성전자는 외국인 순매수와 메모리 업황 회복 기대가 주가에 반영됐다.</p>
                              <p>투자자는 반도체 대형주의 실적 전망과 수급 변화를 함께 확인해야 한다.</p>
                              <p>좋아요 0 훈훈해요 0 슬퍼요 0 화나요 0 후속기사 원해요 0</p>
                              <p>이 기사를 공유합니다 관련기사 목록입니다.</p>
                              <p>댓글 0 추천 0 무단 전재 및 재배포 금지</p>
                            </article>
                          </body>
                        </html>
                        """, MediaType.parseMediaType("text/html;charset=UTF-8")));

        Optional<OriginalArticleContent> content = client.fetch("https://news.example.com/ui-boilerplate");

        assertThat(content).isPresent();
        assertThat(content.orElseThrow().content()).contains("삼성전자와 SK하이닉스");
        assertThat(content.orElseThrow().content()).doesNotContain("글자크기로 변경");
        assertThat(content.orElseThrow().content()).doesNotContain("좋아요 0");
        assertThat(content.orElseThrow().content()).doesNotContain("이 기사를 공유합니다");
        assertThat(content.orElseThrow().content()).doesNotContain("댓글 0 추천 0");
        assertThat(content.orElseThrow().content()).doesNotContain("무단 전재");
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
    void fetchPrefersFocusedNewsisTextBodyOverMixedArticleContainer() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OriginalArticleClient client = new OriginalArticleClient(builder, ProviderTestResilience.disabled());

        server.expect(requestTo("https://news.example.com/newsis"))
                .andRespond(withSuccess("""
                        <html>
                          <head><meta property="og:image" content="/images/newsis.jpg"></head>
                          <body>
                            <div class="view" itemprop="articleBody">
                              <h1>"외국인이 삼성전자 파는 진짜 이유"…전문가가 본 7월 증시 전망</h1>
                              <div>등록 2026.07.10 00:05:00</div>
                              <div>구글에서 선호하는 매체로 추가 작게 크게</div>
                              <div class="viewer">
                                <div id="textBody">
                                  <p>[서울=뉴시스] 염승환 LS증권 이사 (사진출처: 유튜브 김재원TV)</p>
                                  <p>최근 외국인 투자자들의 대규모 매도세가 이어지며 국내 증시 불안감이 커졌다.</p>
                                  <p>전문가는 해외 펀드의 자산 배분 규정에 따른 기계적 매도 성격이 강하다고 분석했다.</p>
                                  <p>투자자는 삼성전자와 SK하이닉스 수급 변화, 코스피 조정 가능성을 확인해야 한다.</p>
                                </div>
                              </div>
                              <section>
                                <h2>실시간 속보</h2>
                                <p>[속보]뉴욕증시, 반도체주 강세에 상승 출발…기술주↑</p>
                                <p>[속보]프로야구 전반기 관중 신기록</p>
                                <p>[속보]이란 중동 각국에 보복 경보</p>
                                <p>[속보]코스닥 9.00포인트 오른 794.00 마감</p>
                                <p>[속보]삼성전자 0.2% SK하이닉스 5.3% 상승 마감</p>
                              </section>
                            </div>
                          </body>
                        </html>
                        """, MediaType.parseMediaType("text/html;charset=UTF-8")));

        Optional<OriginalArticleContent> content = client.fetch("https://news.example.com/newsis");

        assertThat(content).isPresent();
        assertThat(content.orElseThrow().content()).contains("대규모 매도세");
        assertThat(content.orElseThrow().content()).contains("자산 배분 규정");
        assertThat(content.orElseThrow().content()).doesNotContain("등록 2026");
        assertThat(content.orElseThrow().content()).doesNotContain("구글에서 선호하는 매체");
        assertThat(content.orElseThrow().content()).doesNotContain("사진출처");
        assertThat(content.orElseThrow().content()).doesNotContain("프로야구");
        assertThat(content.orElseThrow().content()).doesNotContain("실시간 속보");
        server.verify();
    }

    @Test
    void fetchPrefersFnnewsArticleBodyOverRecommendedArticleContainer() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OriginalArticleClient client = new OriginalArticleClient(builder, ProviderTestResilience.disabled());

        server.expect(requestTo("https://news.example.com/fnnews"))
                .andRespond(withSuccess("""
                        <html>
                          <head><meta property="og:image" content="/images/skhynix.jpg"></head>
                          <body>
                            <article class="article-view" role="article">
                              <h1 class="article-view__title">SK하이닉스 상장에 월가도 잭팟</h1>
                              <div class="article-view__body">
                                <p>SK하이닉스의 미국 나스닥 상장이 역대급 기업공개로 기록될 전망이다.</p>
                                <p>월가 투자은행들은 1억4000만달러가 넘는 수수료를 받을 것으로 예상된다.</p>
                                <p>투자자는 ADR 상장 규모와 메모리 반도체 수요 변화를 확인해야 한다.</p>
                              </div>
                              <section class="article-list article-list--horizontal">
                                <article><h3>아파트서 발견된 남녀 3명 시신</h3></article>
                                <article><h3>딸에 유산 안 물려줄 것</h3></article>
                              </section>
                            </article>
                          </body>
                        </html>
                        """, MediaType.parseMediaType("text/html;charset=UTF-8")));

        Optional<OriginalArticleContent> content = client.fetch("https://news.example.com/fnnews");

        assertThat(content).isPresent();
        assertThat(content.orElseThrow().content()).contains("나스닥 상장");
        assertThat(content.orElseThrow().content()).contains("ADR 상장 규모");
        assertThat(content.orElseThrow().content()).doesNotContain("남녀 3명 시신");
        assertThat(content.orElseThrow().content()).doesNotContain("유산 안 물려줄");
        server.verify();
    }

    @Test
    void fetchExtractsInvestChosunArticleElementBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OriginalArticleClient client = new OriginalArticleClient(builder, ProviderTestResilience.disabled());

        server.expect(requestTo("https://news.example.com/investchosun"))
                .andRespond(withSuccess("""
                        <html>
                          <head>
                            <meta property="og:title" content="레버리지 ETF, LP부터 조인다">
                          </head>
                          <body>
                            <div class="headline">레버리지 ETF, LP부터 조인다</div>
                            <div id="article" class="article">
                              <ul>
                                <li class="par">
                                  <div class="center_img">이미지 크게보기</div>
                                  <p>단일종목 레버리지 ETF를 둘러싼 시장 불안이 가라앉지 않고 있다.</p>
                                  <p>금융당국의 다음 카드가 판매채널과 유동성공급자 감독 강화로 향할 것이란 우려가 나온다.</p>
                                  <p>투자자는 ETF 괴리율, LP 업무 부담, 판매 채널 점검 가능성을 확인해야 한다.</p>
                                </li>
                              </ul>
                            </div>
                            <footer>회사소개 채용 광고안내</footer>
                          </body>
                        </html>
                        """, MediaType.parseMediaType("text/html;charset=UTF-8")));

        Optional<OriginalArticleContent> content = client.fetch("https://news.example.com/investchosun");

        assertThat(content).isPresent();
        assertThat(content.orElseThrow().content()).contains("단일종목 레버리지 ETF");
        assertThat(content.orElseThrow().content()).contains("유동성공급자 감독 강화");
        assertThat(content.orElseThrow().content()).doesNotContain("회사소개");
        server.verify();
    }

    @Test
    void fetchExtractsChosunFusionGlobalContentTextElements() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OriginalArticleClient client = new OriginalArticleClient(builder, ProviderTestResilience.disabled());

        server.expect(requestTo("https://news.example.com/chosun"))
                .andRespond(withSuccess("""
                        <html>
                          <head>
                            <title>꼬리가 몸통 흔드는 투기판 코스피</title>
                            <meta property="og:image" content="/images/kospi.jpg">
                          </head>
                          <body>
                            <div id="fusion-app"></div>
                            <script>
                              Fusion.globalContent={
                                "_id":"UGCIJBQUU5GOHDJCMTIZ3NUVKY",
                                "content_elements":[
                                  {"type":"image","caption":"전광판 사진"},
                                  {"type":"text","content":"7~8일 이틀 연속 급락했던 코스피는 9일에도 크게 출렁였다."},
                                  {"type":"text","content":"삼성전자, SK하이닉스 주가 움직임에 2배 베팅할 수 있는 단일종목 레버리지 상품이 변동성을 키웠다는 지적이 나온다."},
                                  {"type":"text","content":"한국거래소에 따르면 두 종목 레버리지 상품 거래대금이 크게 늘었다."}
                                ]
                              };Fusion.globalContentConfig={"source":"article"};
                            </script>
                          </body>
                        </html>
                        """, MediaType.parseMediaType("text/html;charset=UTF-8")));

        Optional<OriginalArticleContent> content = client.fetch("https://news.example.com/chosun");

        assertThat(content).isPresent();
        assertThat(content.orElseThrow().content()).contains("코스피는 9일에도 크게 출렁였다");
        assertThat(content.orElseThrow().content()).contains("삼성전자, SK하이닉스");
        assertThat(content.orElseThrow().content()).doesNotContain("전광판 사진");
        server.verify();
    }

    @Test
    void fetchRemovesNewspimAiSummaryBeforeArticleBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OriginalArticleClient client = new OriginalArticleClient(builder, ProviderTestResilience.disabled());

        server.expect(requestTo("https://news.example.com/newspim"))
                .andRespond(withSuccess("""
                        <html>
                          <head><meta property="og:image" content="/images/chip.jpg"></head>
                          <body>
                            <article class="bodynews">
                              <section class="contents">
                                AI 핵심 요약 beta 분석 중... AI 기대 약화 속 반도체주와 소프트웨어주가 엇갈렸다
                                ! AI가 자동 생성한 요약으로 정확하지 않을 수 있어요.
                                이 기사는 인공지능(AI) 번역으로 생산된 콘텐츠로, 원문은 7월 9일자 블룸버그 기사입니다.
                                [서울=뉴스핌] 김현영 기자 = 2026년을 지배해온 대표적인 기술주 트레이드가 와해되는 신호가 나타나고 있다.
                                소프트웨어 주식들은 AI가 기업 성장을 잠식할 것이라는 우려로 수개월간 급락한 끝에 되살아나는 모습이다.
                                반면 반도체 기업들은 수조 달러 규모의 AI 설비투자 계획이 실제로 집행될지에 대한 의구심 속에 흔들리고 있다.
                              </section>
                            </article>
                          </body>
                        </html>
                        """, MediaType.parseMediaType("text/html;charset=UTF-8")));

        Optional<OriginalArticleContent> content = client.fetch("https://news.example.com/newspim");

        assertThat(content).isPresent();
        assertThat(content.orElseThrow().content()).startsWith("[서울=뉴스핌]");
        assertThat(content.orElseThrow().content()).doesNotContain("AI 핵심 요약");
        assertThat(content.orElseThrow().content()).doesNotContain("자동 생성한 요약");
        server.verify();
    }

    @Test
    void fetchRemovesBrowserPlayerAndCommentBoilerplate() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OriginalArticleClient client = new OriginalArticleClient(builder, ProviderTestResilience.disabled());

        server.expect(requestTo("https://news.example.com/kbs"))
                .andRespond(withSuccess("""
                        <html>
                          <head>
                            <meta property="og:image" content="/images/kbs-main.jpg">
                          </head>
                          <body>
                            <article>
                              <p>잠깐! 현재 Internet Explorer 8이하 버전을 이용중이십니다. 최신 브라우저 사용을 권장드립니다!</p>
                              <p>읽어주기 기능은 크롬기반의 브라우저에서만 사용하실 수 있습니다.</p>
                              <p>센스리더 사용자는 가상커서를 해제한 후 동영상플레이어 단축키를 이용하세요.</p>
                              <p>좌 / 우 방향키는 시간이 - 5 / +5로 이동되며, 상 / 하 방향키는 음량이 + 5 / -5로 조절됩니다.</p>
                              <p>댓글 이용시 소셜계정으로 로그인하셔야 하며 로그인하시면 소셜회원으로 표시됩니다.</p>
                              <p>LG화학이 고부가가치 미래 전략사업 영역을 확대하고 있다.</p>
                              <p>AI 투자 확대와 HBM 수요 증가로 고성능 공정 소재의 중요성이 커졌다.</p>
                              <p>LG화학은 반도체용 스트리퍼 공급 확대를 본격화한다고 밝혔다.</p>
                            </article>
                          </body>
                        </html>
                        """, MediaType.parseMediaType("text/html;charset=UTF-8")));

        Optional<OriginalArticleContent> content = client.fetch("https://news.example.com/kbs");

        assertThat(content).isPresent();
        assertThat(content.orElseThrow().content()).contains("LG화학이 고부가가치");
        assertThat(content.orElseThrow().content()).doesNotContain("Internet Explorer");
        assertThat(content.orElseThrow().content()).doesNotContain("가상커서");
        assertThat(content.orElseThrow().content()).doesNotContain("소셜계정");
        assertThat(content.orElseThrow().imageUrls())
                .containsExactly("https://news.example.com/images/kbs-main.jpg");
        server.verify();
    }

    @Test
    void fetchRemovesArticleImageOverlayCaptionAndReporterEmail() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OriginalArticleClient client = new OriginalArticleClient(builder, ProviderTestResilience.disabled());

        server.expect(requestTo("https://news.example.com/global"))
                .andRespond(withSuccess("""
                        <html>
                          <head>
                            <meta property="og:image" content="/images/robot.jpg">
                          </head>
                          <body>
                            <div class="vtxt detailCont" itemprop="articleBody">
                              <div class="article_con_img">
                                <figure>
                                  <img src="/images/inline.jpg">
                                  <a class="mimg_open"><span class="blind">이미지 확대보기</span></a>
                                  <figcaption>셰플러의 CES 부스 사진</figcaption>
                                </figure>
                              </div>
                              <p>엔비디아의 피지컬 AI 매출 기여도는 아직 제한적이다.</p>
                              <p>투자자 관심은 완제품보다 부품 공급망으로 옮겨가고 있다.</p>
                              <p>증권가는 로봇과 자동화 장비 수요가 관련 부품 업체의 실적에 반영되는 시점을 확인해야 한다고 설명했다.</p>
                              <p>진형근 글로벌이코노믹 기자 jinwook@g-enews.com</p>
                              <p>독자들의 PICK! 원문과 무관한 추천 기사 제목</p>
                            </div>
                          </body>
                        </html>
                        """, MediaType.parseMediaType("text/html;charset=UTF-8")));

        Optional<OriginalArticleContent> content = client.fetch("https://news.example.com/global");

        assertThat(content).isPresent();
        assertThat(content.orElseThrow().content()).contains("부품 공급망");
        assertThat(content.orElseThrow().content()).doesNotContain("이미지 확대보기");
        assertThat(content.orElseThrow().content()).doesNotContain("CES 부스 사진");
        assertThat(content.orElseThrow().content()).doesNotContain("jinwook@g-enews.com");
        assertThat(content.orElseThrow().content()).doesNotContain("추천 기사");
        assertThat(content.orElseThrow().imageUrls())
                .containsExactly(
                        "https://news.example.com/images/robot.jpg",
                        "https://news.example.com/images/inline.jpg");
        server.verify();
    }

    @Test
    void fetchExtractsYtnArticleBodyInsteadOfMetaDescription() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OriginalArticleClient client = new OriginalArticleClient(builder, ProviderTestResilience.disabled());

        server.expect(requestTo("https://news.example.com/ytn"))
                .andRespond(withSuccess("""
                        <html>
                          <head>
                            <meta name="description" content="코스피 5% 하락 단문 요약">
                            <meta property="og:image" content="/images/ytn.jpg">
                          </head>
                          <body>
                            <div id="CmAdContent" class="paragraph">
                              <span>[앵커]<br />
                              코스피와 코스닥이 극심한 변동성을 보인 끝에 나란히 5% 넘게 급락 마감했습니다.<br />
                              반도체 불안과 중동발 지정학적 위험이 투자심리를 크게 위축시켰습니다.<br />
                              매도 사이드카가 발동된 뒤에도 코스피는 7,200선에서 장을 마쳤습니다.<br />
                              YTN 차유정 기자 chayj@ytn.co.kr</span>
                            </div>
                          </body>
                        </html>
                        """, MediaType.parseMediaType("text/html;charset=UTF-8")));

        Optional<OriginalArticleContent> content = client.fetch("https://news.example.com/ytn");

        assertThat(content).isPresent();
        assertThat(content.orElseThrow().content()).contains("극심한 변동성");
        assertThat(content.orElseThrow().content()).contains("매도 사이드카");
        assertThat(content.orElseThrow().content()).doesNotContain("코스피 5% 하락 단문 요약");
        assertThat(content.orElseThrow().content()).doesNotContain("chayj@ytn.co.kr");
        server.verify();
    }

    @Test
    void fetchExtractsKbsDetailBodyInsteadOfMetaDescription() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OriginalArticleClient client = new OriginalArticleClient(builder, ProviderTestResilience.disabled());

        server.expect(requestTo("https://news.example.com/kbs-detail"))
                .andRespond(withSuccess("""
                        <html>
                          <head>
                            <meta property="og:description" content="코스닥은 전일 대비 하락했습니다.">
                            <meta property="og:image" content="/images/kbs.jpg">
                          </head>
                          <body>
                            <div class="detail-body font-size" id="cont_newstext">
                              [앵커]<br /><br />
                              미국과 이란의 무력 충돌이 재점화하면서 국내 증시도 흔들렸습니다.<br /><br />
                              코스피와 코스닥 시장에서는 장중 매도 사이드카까지 발동됐습니다.<br /><br />
                              코스피는 결국 5% 이상 떨어지며 7,246.79로 마감했습니다.<br /><br />
                              코스닥 시장도 약 10개월 만에 800선 아래로 밀렸습니다.
                            </div>
                          </body>
                        </html>
                        """, MediaType.parseMediaType("text/html;charset=UTF-8")));

        Optional<OriginalArticleContent> content = client.fetch("https://news.example.com/kbs-detail");

        assertThat(content).isPresent();
        assertThat(content.orElseThrow().content()).contains("무력 충돌");
        assertThat(content.orElseThrow().content()).contains("7,246.79");
        assertThat(content.orElseThrow().content()).doesNotContain("전일 대비 하락");
        server.verify();
    }

    @Test
    void fetchDoesNotTreatMetaDescriptionAsOriginalArticleBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OriginalArticleClient client = new OriginalArticleClient(builder, ProviderTestResilience.disabled());

        server.expect(requestTo("https://news.example.com/meta-only"))
                .andRespond(withSuccess("""
                        <html>
                          <head>
                            <meta property="og:description" content="코스피는 전일 대비 하락한 785로 마감했다.">
                            <meta property="og:image" content="/images/market.jpg">
                          </head>
                          <body>
                            <main>
                              <section class="headline">기사 제목 영역</section>
                              <aside>추천 기사</aside>
                            </main>
                          </body>
                        </html>
                        """, MediaType.parseMediaType("text/html;charset=UTF-8")));

        Optional<OriginalArticleContent> content = client.fetch("https://news.example.com/meta-only");

        assertThat(content).isEmpty();
        server.verify();
    }

    @Test
    void fetchPreservesOriginalArticleBodyAboveLegacySixtyThousandChars() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OriginalArticleClient client = new OriginalArticleClient(builder, ProviderTestResilience.disabled());
        String longBody = "삼성전자는 AI 반도체 투자와 외국인 수급 영향을 원문 기사에서 자세히 설명했다. ".repeat(1_700);

        server.expect(requestTo("https://news.example.com/long-article"))
                .andRespond(withSuccess("""
                        <html>
                          <head><link rel="canonical" href="https://news.example.com/long-article"></head>
                          <body><article>%s</article></body>
                        </html>
                        """.formatted(longBody), MediaType.parseMediaType("text/html;charset=UTF-8")));

        Optional<OriginalArticleContent> content = client.fetch("https://news.example.com/long-article");

        assertThat(longBody.length()).isGreaterThan(60_000);
        assertThat(content).isPresent();
        assertThat(content.orElseThrow().content()).hasSizeGreaterThan(60_000);
        assertThat(content.orElseThrow().content()).endsWith("자세히 설명했다.");
        server.verify();
    }

    @Test
    void fetchFallsBackToAmpHtmlWhenPublisherPageRendersArticleWithJavascript() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OriginalArticleClient client = new OriginalArticleClient(builder, ProviderTestResilience.disabled());

        server.expect(requestTo("https://biz.sbs.co.kr/article_hub/20000321662?division=NAVER"))
                .andRespond(withSuccess("""
                        <html>
                          <head>
                            <link rel="canonical" href="https://biz.sbs.co.kr/article/20000321662">
                            <link rel="amphtml" href="https://biz.sbs.co.kr/amp/article/20000321662">
                          </head>
                          <body>
                            <script>new ArticleContainer(target, 20000321662).render();</script>
                          </body>
                        </html>
                        """, MediaType.parseMediaType("text/html;charset=UTF-8")));
        server.expect(requestTo("https://biz.sbs.co.kr/amp/article/20000321662"))
                .andRespond(withSuccess("""
                        <html amp>
                          <head><link rel="canonical" href="https://biz.sbs.co.kr/article/20000321662"></head>
                          <body>
                            <main class="article_content_w">
                              <div class="article_content_end_w">
                                <div class="article_content_end_middle">
                                  외신이 주목한 주요 이슈들 짚어보겠습니다.<br />
                                  <script src="https://adservice.sbs.co.kr/ad.js"></script><br />
                                  <strong>◇ 마이크론, 美 투자 확대…2500억 달러로 증액</strong><br />
                                  마이크론은 미국 반도체 공급망 강화를 위해 투자를 확대한다고 밝혔다.<br />
                                  삼성전자와 SK하이닉스 투자자는 메모리 수요와 HBM 경쟁 구도를 함께 확인해야 한다.<br />
                                  googletag.cmd.push(function(){googletag.display('div-gpt-ad-1');});<br />
                                  증권가는 반도체 대형주의 실적 전망과 외국인 수급 영향을 점검하고 있다.<br />
                                  dschoi@fnnews.com 최두선 기자 #삼성전자 #SK하이닉스 ※ 저작권자 ⓒ 파이낸셜뉴스, 무단전재-재배포 금지
                                </div>
                              </div>
                            </main>
                          </body>
                        </html>
                        """, MediaType.parseMediaType("text/html;charset=UTF-8")));

        Optional<OriginalArticleContent> content =
                client.fetch("https://biz.sbs.co.kr/article_hub/20000321662?division=NAVER");

        assertThat(content).isPresent();
        assertThat(content.orElseThrow().content()).contains("미국 반도체 공급망 강화");
        assertThat(content.orElseThrow().content()).doesNotContain("googletag");
        assertThat(content.orElseThrow().content()).doesNotContain("저작권자");
        assertThat(content.orElseThrow().canonicalUrl()).isEqualTo("https://biz.sbs.co.kr/article/20000321662");
        server.verify();
    }

    @Test
    void fetchExtractsSbsAmpArticleTextContainer() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OriginalArticleClient client = new OriginalArticleClient(builder, ProviderTestResilience.disabled());

        server.expect(requestTo("https://biz.sbs.co.kr/article_hub/20000321685?division=NAVER"))
                .andRespond(withSuccess("""
                        <html>
                          <head>
                            <link rel="canonical" href="https://biz.sbs.co.kr/article/20000321685">
                          </head>
                          <body>
                            <div id="app-cnbc-front"></div>
                            <script>articleContainer = new ArticleContainer(target, 20000321685);</script>
                          </body>
                        </html>
                        """, MediaType.parseMediaType("text/html;charset=UTF-8")));
        server.expect(requestTo("https://biz.sbs.co.kr/amp/article/20000321685"))
                .andRespond(withSuccess("""
                        <html amp>
                          <head><link rel="canonical" href="https://biz.sbs.co.kr/article/20000321685"></head>
                          <body>
                            <main class="article_content_w">
                              <div class="article_content_end_middle">
                                <div class="article_subtitle_w" style="display:none;">숨겨진 부제</div>
                                <div class="acem_text">
                                  <strong>■ 모닝벨 비즈 나우</strong><br />
                                  [앵커]<br />
                                  SK하이닉스의 나스닥 데뷔전에 시선이 쏠린 사이 추격자들의 움직임이 심상치 않습니다.<br />
                                  미국에서는 마이크론이, 중국에서는 창신메모리가 바짝 뒤쫓고 있습니다.<br />
                                  삼성전자와 SK하이닉스의 초대형 투자 계획에 자극을 받아 미국 생산 투자도 대폭 늘렸습니다.<br />
                                  투자자는 메모리 업황, HBM 수요, 북미 공급망 다변화 영향을 확인해야 합니다.
                                </div>
                                <div class="reporter_article_w">
                                  <a>추천 기사 SK하이닉스 ADR 공모가</a>
                                </div>
                              </div>
                            </main>
                          </body>
                        </html>
                        """, MediaType.parseMediaType("text/html;charset=UTF-8")));

        Optional<OriginalArticleContent> content =
                client.fetch("https://biz.sbs.co.kr/article_hub/20000321685?division=NAVER");

        assertThat(content).isPresent();
        assertThat(content.orElseThrow().content()).contains("마이크론");
        assertThat(content.orElseThrow().content()).contains("북미 공급망");
        assertThat(content.orElseThrow().content()).doesNotContain("추천 기사");
        server.verify();
    }

    @Test
    void fetchFollowsPublisherScriptRedirectAndExtractsTheBellBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OriginalArticleClient client = new OriginalArticleClient(builder, ProviderTestResilience.disabled());

        server.expect(requestTo("https://www.thebell.co.kr/free/content/ArticleView.asp?key=202607081413597920104794"))
                .andRespond(withSuccess("""
                        <html>
                          <body>
                            <script>
                              if (navigator.userAgent.toLowerCase().match('iphone')) {
                                top.location.href='http://m.thebell.co.kr/m/newsview.asp?svccode=00&newskey=202607081413597920104794';
                              } else {
                                top.location.href = "/front/newsview.asp?click=F&key=202607081413597920104794";
                              }
                            </script>
                          </body>
                        </html>
                        """, MediaType.parseMediaType("text/html;charset=UTF-8")));
        server.expect(requestTo("https://www.thebell.co.kr/front/newsview.asp?click=F&key=202607081413597920104794"))
                .andRespond(withSuccess("""
                        <html>
                          <head>
                            <link rel="canonical" href="https://www.thebell.co.kr/front/newsview.asp?key=202607081413597920104794">
                            <meta property="og:image" content="https://image.thebell.co.kr/news/photo/2026/07/08/20260708141724336_n.png">
                          </head>
                          <body>
                            <div id="article_main" class="viewSection">
                              <div class="editBox">편집자주 문구</div>
                              <div class='article_content_banner'><img class='ADVIMG' src='/banner.jpg'></div>
                              개정 상법 시행을 앞두고 주주제안 제도의 실효성을 높이기 위한 후속 입법 과제가 수면 위로 떠올랐다.<br />
                              현행 상법은 주주제안 요건으로 발행주식총수의 0.5~3% 이상 지분 보유를 요구하고 있다.<br />
                              삼성전자의 경우 이 요건을 충족하려면 약 5조원의 자금이 필요하다.<br />
                              주주제안 절차의 진입 장벽을 낮추는 방안도 거론된다.
                            </div>
                          </body>
                        </html>
                        """, MediaType.parseMediaType("text/html;charset=UTF-8")));

        Optional<OriginalArticleContent> content =
                client.fetch("https://www.thebell.co.kr/free/content/ArticleView.asp?key=202607081413597920104794");

        assertThat(content).isPresent();
        assertThat(content.orElseThrow().content()).contains("주주제안 제도의 실효성");
        assertThat(content.orElseThrow().content()).contains("삼성전자의 경우");
        assertThat(content.orElseThrow().content()).doesNotContain("article_content_banner");
        assertThat(content.orElseThrow().canonicalUrl())
                .isEqualTo("https://www.thebell.co.kr/front/newsview.asp?key=202607081413597920104794");
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
                              <p>증권가는 장중 거래대금과 기관 매수 흐름을 함께 살피며 추가 상승 여력을 점검했다.</p>
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
