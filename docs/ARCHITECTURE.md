# 아키텍처

## 목적
- 해외 협력사 거래소가 한국 주식 정보와 뉴스·공시 알림을 연동하는 B2B API를 제공한다.
- 실제 주문, 체결, 정산, 환전, 옴니버스 계좌 처리는 이 레포 범위에서 제외한다.

## 서비스 구성
- `market`: 한국 주식 현재가, 호가, 종목 검색 API, 전체/다건 quote snapshot API, 협력사용 market quote WebSocket stream, KRX 과거 시세 수집·정규화·DB 저장·history API
- `alert`: 뉴스·공시 분석 결과를 협력사와 종목 topic으로 송신하는 API
- `config`: API key 검증, CORS, WebSocket 설정
- `portal`: 웹 회원 인증, 세션 버전, API 키 신청, `MEMBER`/`ADMIN` RBAC
- `tax.refund`: 거래소 신청 동기화, Hannah AI OCR 값 매핑, 경정청구서 PDF 확정·승인

## API 경계
- REST: `/api/v1/market/**`, `/api/v1/alerts/**`
- 웹 포털: `/api/v1/portal/**` (`Authorization: Bearer`, 관리자는 `ROLE_ADMIN`)
- WebSocket: `/ws/alerts`, `/ws/market/quotes`
- 협력사 topic: `/topic/partners/{partnerId}/alerts`
- 협력사 종목 topic: `/topic/partners/{partnerId}/stocks/{stockCode}/alerts`
- 전역 종목 topic: `/topic/stocks/{stockCode}/alerts`
- REST와 WebSocket handshake는 모두 협력사 API key 보호 대상이다.
- 협력사별 DB credential로 인증된 알림 API 요청은 요청 `partnerId`와 인증 `partnerId`가 일치해야 한다.
- 협력사별 DB credential로 연결한 WebSocket 세션은 자기 협력사 topic만 구독할 수 있다.

## 외부 시스템
- KIS Open API: 현재가, 실시간 체결가, 실시간 호가
- KRX Open API: 과거 일별 OHLCV, 거래대금
- FX provider: 실시간/준실시간 환율
- Naver News Search: 뉴스 제목, snippet, 원문 링크 발견. 사용 허가된 원문 URL은 API 서버가 fetch해 전문과 대표 이미지 URL을 정제한다.
- OpenDART: 공시 제목, 유형, 제출시각, 원문 링크, 접수번호 기반 document 전문
- Hannah-Montana-AI: 뉴스·공시 종목 매핑, 이벤트, 감성, 중요도 분석, 한국 금융 고유어·전문용어 해설, 외국인 보유 시계열 예측 boundary 산출, 글로벌 피어 매칭, 세무 문서 템플릿·영역 OCR 검증
- 뉴스 응답의 `modelVersion`은 Hana Montana AI(KF-DeBERTa + K-FNSPID)의 이벤트·금융 감성·공시 의미 중요도와 요청 출처에 맞는 K-FNSPID v4 시장영향 전문가 버전을 함께 보존한다. 의미 중요도 `importance`와 가격반응 `marketImpactImportance/Score/Confidence`는 서로 다른 과제로 분리해 전파한다. 1,247,685문서와 10,691,998행 일별 시세로 구성된 K-FNSPID v4 원천·시세 파일 생성과 학습은 Hannah 저장소 책임이며 OmniLens DB를 데이터셋 원천으로 사용하지 않는다.

## 현재 구현 상태
- KIS 모의투자 현재가 REST, KIS 모의투자 실시간 체결·호가 WebSocket runner, 공공데이터 주식시세, KRX Open API 과거 일별매매정보, Frankfurter FX 환율, Naver News Search, OpenDART, Hannah-Montana-AI 어댑터가 구현되어 있다.
- `MarketDataService`가 표준 응답 구조와 현지 통화 환산 로직을 제공한다.
- `GET /api/v1/market/quotes`는 stockCodes가 있으면 요청 순서의 다건 snapshot을, 없으면 종목 마스터 기반 전체 snapshot을 반환한다.
- `POST /api/v1/market/history/collect`는 `omnilens.market.history-collection.provider`에 따라 KRX KOSPI/KOSDAQ/KONEX 일별매매정보 또는 KIS 일봉 chart API를 수집해 `market_daily_price`에 upsert한다. `KIS_DAILY_CHART` 모드는 KIS를 primary 실 provider로 사용하므로 KRX egress 차단 환경에서도 mock 없이 성공한다.
- `POST /api/v1/market/foreign-ownership/collect`, `POST /api/v1/market/foreign-ownership/backfill`, `ForeignOwnershipRefreshScheduler`는 KRX Data Marketplace 로그인 기반 외국인 보유 snapshot을 `foreign_ownership_daily_snapshot`에 upsert해 Hannah 외국인 보유 시계열 예측 입력을 지속적으로 누적한다.
- `GET /api/v1/market/stocks/{stockCode}/history`는 저장된 KRX 기반 OHLCV/거래대금 일봉 데이터를 우선 반환하고, 저장 row가 없으면 KIS 일봉 chart API 또는 저장된 정규장 분봉의 OHLCV 집계로 보강한다. 휴장일에는 이전 거래일 가격을 요청 날짜로 복제하지 않는다.
- 종목 마스터는 Flyway가 생성한 `stock_master` 테이블과 JDBC 저장소를 사용한다. KIS KOSPI·KOSDAQ·KONEX 마스터를 시장별 스냅샷으로 reconcile하며, 현재 스냅샷에 없는 역사 종목은 `active=false`로 전환한다.
- 초기 seed는 부트스트랩으로만 사용하고, 검색·전체 quote·AI 연동 universe는 `active=true`인 현재 KIS 종목만 사용한다.
- 협력사 watchlist는 Flyway가 생성한 `partner_watchlist_subscription` 테이블과 JDBC 저장소를 사용한다.
- watchlist 종목은 `stock_master` FK로 제한하며, REST API 저장 시 미지원 종목은 404로 거부한다.
- 협력사 API key는 Flyway가 생성한 `partner_api_credential` 테이블에 SHA-256 해시와 `partner_id`로 저장한다.
- `MarketDataService`는 KIS 실시간 체결 cache, KIS 현재가 REST, PostgreSQL 최신 정규장 분봉, 공공데이터 전일 snapshot 순서로 실제 provider 가격을 조회한다. 재기동·휴장일에는 저장 분봉의 체결 시각과 출처를 그대로 노출하며, 가격, KRX 외국인 보유량 snapshot, 또는 FX cache가 없으면 가짜 시장 데이터로 성공 응답을 만들지 않고 `MARKET_002`로 실패한다.
- `MarketDataService`는 KIS 실시간 호가 cache를 우선 사용하고, 장외 또는 초기 구동처럼 cache가 비어 있으면 KIS REST 호가 snapshot으로 orderbook 응답을 보강한다.
- `MarketQuoteWebSocketHandler`는 raw WebSocket `/ws/market/quotes`에서 인증된 협력사 연결을 관리하고, `RealtimeMarketDataIngestionService`가 KIS 체결 tick을 수신하면 KRW/현지통화/FX metadata가 포함된 `MarketQuote` JSON을 송신한다.
- 협력사가 `QUOTE_STREAM_REPLAY` 메시지를 보내면 현재 quote snapshot을 요청 통화 기준으로 재송신한다.
- `KisRealtimeSessionRunner`는 인기 10종목의 체결·호가와 장운영정보(`H0STMKO0`)를 고정하고 KIS의 주식 체결·호가 40 TR 한도에서 남은 슬롯을 상세 종목 LRU로 운영한다. 장운영정보의 거래정지 사유에서 서킷브레이커를 판별하고 체결이 중단된 동안에도 상태 quote를 즉시 발행한다. 발동 후 재접속으로 상태 이벤트를 놓친 경우에는 신선한 실전 시장지수의 서킷 임계 하락과 같은 시장의 체결 중단을 함께 확인해 상태를 복구하고, 체결 재개 즉시 해제한다. `KisRealtimeIndexSessionRunner`는 지수 3개를 같은 실전 provider 또는 별도 실전 연결에 고정한다. `QUOTE_STREAM_SUBSCRIBE`와 상세 구독 REST 계약은 같은 주식 구독 관리자를 사용하며, 교체 시 해제 프레임을 먼저 전송한다.
- `StandardKisRealtimeWebSocketConnection`은 close code와 관계없이 지수 백오프·jitter로 재연결하고 고정·동적 구독 프레임을 복원한다.
- `MarketDataService`는 KRX 외국인보유량 cache에 snapshot이 있으면 외국인 보유수량, 지분율, 한도소진율을 quote payload에 반영한다. KIS 실시간 체결가·호가 WebSocket은 가격·호가·상태 전용이며 외국인 보유량 필드를 제공하지 않는다.
- 주문 가능 여부 boundary는 KRX snapshot의 외국인 취득한도 제한 종목에만 보유 시계열 예측을 사용한다. 장전 batch가 Hannah-Montana-AI 모델로 금일 예측을 선계산해 cache에 저장하고 API 요청은 cache hit를 우선 반환한다. cache miss 또는 AI 장애 시 OmniLens 내부 시계열 엔진으로 fallback한다. 제한이 없는 종목은 `FOREIGN_LIMIT_NOT_APPLICABLE`로 반환한다. 외국인 한도 예측은 프론트 사전 고지용 경고 신호이며 주문 차단 조건에 포함하지 않는다.
- `GET /api/v1/market/stocks/{stockCode}/global-peers`는 종목 master metadata를 Hannah-Montana-AI 글로벌 피어 매칭 모델에 전달해 외국인 투자자용 peer popup copy, 미국 상장 peer 목록, 속성별 `comparisons`, 국내 종목 자체의 4개 `keyStrengths`를 반환한다. AI 응답의 dimension/icon key allowlist와 카드 개수를 provider 경계에서 검증하며, Hannah 장애 fallback은 근거 없는 비교·강점 카드를 만들지 않고 빈 배열로 응답한다.
- `POST /api/v1/korean-financial-terms/explain`는 뉴스/공시 본문에서 선택한 한국 금융 고유어·전문용어를 Hannah-Montana-AI 단일 검증 사전에 전달한다. `개미`는 번역 문장에서 `Ants`, glossary 정규 라벨에서 `Ant`로 고정한다. 검증된 응답만 TTL cache에 저장하고 클릭 식별자는 salted SHA-256 hash로 기록한다.
- `POST /api/v1/tax/documents/verify`는 원본 문서 바이트와 국가 정보를 Hannah-Montana-AI로 전달하며 내부 사용자·계정 ID를 OCR 필수 필드로 사용하지 않는다.
- KRX 수집은 KOSPI/KOSDAQ/KONEX 시장별 실패를 격리해 `SUCCESS`, `PARTIAL_FAILED`, `FAILED` 상태와 시장별 오류를 반환한다. `KRX_OPEN_API_WITH_KIS_BACKUP` 모드는 KRX 실패 시 KIS 일봉 chart API를 실 provider 백업으로 사용하고, `KIS_DAILY_CHART` 모드는 KIS 결과만으로 전체 상태를 계산한다.
- 협력사 입력 환율은 `ExchangeRateCache`에 `KRW -> 현지통화` 표시용 환율로 저장하고, quote 요청에 `fxRate`가 없을 때 현지 통화 환산가 계산에 사용한다.
- `ExchangeRateCache`는 Redis TTL 저장소를 기본으로 사용하고 Redis 장애 시 프로세스 단위 in-memory fallback을 사용한다.
- FX 환율 provider는 최신 `KRW -> 현지통화` 비율을 같은 `ExchangeRateCache`에 저장한다.
- 환율 refresh scheduler는 기본 disabled이며, 설정된 통화 목록만 FX provider로 주기 갱신한다.
- `AlertStreamingService`가 알림 이벤트를 협력사·종목 topic으로 송신한다.
- `AlertProviderCollectionService`가 종목별 뉴스·공시를 수집하고 AI 분석 결과를 WebSocket 알림으로 발행한다.
- 알림 스케줄러는 설정 파일 watchlist와 DB watchlist를 협력사별로 병합해 같은 수집·분석·발행 경로를 재사용한다.
- 뉴스·공시 중복 재발행 방지는 Redis TTL 기반 dedupe를 기본으로 사용하고, Redis 장애 시 프로세스 단위 in-memory fallback을 사용한다.
- v2에서는 watchlist 수집 경로와 별도로 전체 종목 shard 스케줄러를 둔다. 처리된 뉴스·공시는 DB 이벤트 저장소에 먼저 저장하고, canonical URL/content hash/AI duplicate key/cluster key로 중복을 줄인 뒤 REST 목록·상세와 WebSocket 이벤트를 같은 저장 레코드에서 만든다.
- 전문과 이미지 URL은 Naver Search row에서 직접 얻는 값이 아니다. 사용 허가된 원문 URL 또는 공시 원문에서 수집하고, block element 단위 문단과 줄바꿈을 보존해 전문을 저장한 뒤 동일 레코드에서 REST 목록·상세와 WebSocket payload를 만든다. 이미지 후보는 DOM 정제 전에 Open Graph, JSON-LD, article/header의 lazy-loading 속성에서 수집하며 후보가 없으면 빈 `imageUrls`를 반환한다.
- 한국 증시 시장뉴스는 종목별 alert와 별도 테이블 `market_news_event`에 저장하고 `/api/v1/market/news`, `/api/v1/market/news/{newsId}`, `/api/v1/market/news/collect`로 제공한다. 기본 검색어는 `한국 증시`, `코스피 코스닥`, `국내 증시`이며 원문 보강 가능 시 전문·이미지 URL을 함께 보관한다.
- WebSocket subscription 계약 테스트가 실제 STOMP client로 topic 수신과 협력사 topic 권한을 검증한다.
