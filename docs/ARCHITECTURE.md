# 아키텍처

## 목적
- 해외 협력사 거래소가 한국 주식 정보와 뉴스·공시 알림을 연동하는 B2B API를 제공한다.
- 실제 주문, 체결, 정산, 환전, 옴니버스 계좌 처리는 이 레포 범위에서 제외한다.

## 서비스 구성
- `market`: 한국 주식 현재가, 호가, 종목 검색 API, 전체/다건 quote snapshot API, 협력사용 market quote WebSocket stream, KRX 과거 시세 수집·정규화·DB 저장·history API
- `alert`: 뉴스·공시 분석 결과를 협력사와 종목 topic으로 송신하는 API
- `config`: API key 검증, CORS, WebSocket 설정

## API 경계
- REST: `/api/v1/market/**`, `/api/v1/alerts/**`
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
- Naver News Search: 뉴스 제목, snippet, 원문 링크. 기사 전문과 이미지 URL은 보장하지 않으므로 v2에서는 발견 데이터로만 사용한다.
- OpenDART: 공시 제목, 유형, 제출시각, 원문 링크
- Hannah-Montana-AI: 뉴스·공시 종목 매핑, 이벤트, 감성, 중요도 분석, 외국인 보유 시계열 예측 boundary 산출

## 현재 구현 상태
- KIS 모의투자 현재가 REST, KIS 모의투자 실시간 체결·호가 WebSocket runner, 공공데이터 주식시세, KRX Open API 과거 일별매매정보, Frankfurter FX 환율, Naver News Search, OpenDART, Hannah-Montana-AI 어댑터가 구현되어 있다.
- `MarketDataService`가 표준 응답 구조와 현지 통화 환산 로직을 제공한다.
- `GET /api/v1/market/quotes`는 stockCodes가 있으면 요청 순서의 다건 snapshot을, 없으면 종목 마스터 기반 전체 snapshot을 반환한다.
- `POST /api/v1/market/history/collect`는 `omnilens.market.history-collection.provider`에 따라 KRX KOSPI/KOSDAQ/KONEX 일별매매정보 또는 KIS 일봉 chart API를 수집해 `market_daily_price`에 upsert한다. `KIS_DAILY_CHART` 모드는 KIS를 primary 실 provider로 사용하므로 KRX egress 차단 환경에서도 mock 없이 성공한다.
- `POST /api/v1/market/foreign-ownership/collect`와 `ForeignOwnershipRefreshScheduler`는 KIS 현재가 외국인 보유 snapshot을 `foreign_ownership_daily_snapshot`에 upsert해 Hannah 외국인 보유 시계열 예측 입력을 지속적으로 누적한다.
- `GET /api/v1/market/stocks/{stockCode}/history`는 저장된 KRX 기반 OHLCV/거래대금 일봉 데이터를 우선 반환하고, 저장 row가 없으면 KIS 일봉 chart API로 보강한다.
- 종목 마스터는 Flyway가 생성한 `stock_master` 테이블과 JDBC 저장소를 사용한다.
- 초기 종목 universe는 `stock-master-seed.csv`에서 애플리케이션 시작 시 한 번 적재하며, 이미 데이터가 있으면 중복 적재하지 않는다.
- 협력사 watchlist는 Flyway가 생성한 `partner_watchlist_subscription` 테이블과 JDBC 저장소를 사용한다.
- watchlist 종목은 `stock_master` FK로 제한하며, REST API 저장 시 미지원 종목은 404로 거부한다.
- 협력사 API key는 Flyway가 생성한 `partner_api_credential` 테이블에 SHA-256 해시와 `partner_id`로 저장한다.
- `MarketDataService`는 KIS 실시간 체결 cache, KIS 현재가 REST, 공공데이터 전일 snapshot 순서로 실제 provider 가격을 조회한다. 가격, KIS 외국인 보유량 snapshot, 또는 FX cache가 없으면 가짜 시장 데이터로 성공 응답을 만들지 않고 `MARKET_002`로 실패한다.
- `MarketDataService`는 KIS 실시간 호가 cache를 우선 사용하고, 장외 또는 초기 구동처럼 cache가 비어 있으면 KIS REST 호가 snapshot으로 orderbook 응답을 보강한다.
- `MarketQuoteWebSocketHandler`는 raw WebSocket `/ws/market/quotes`에서 인증된 협력사 연결을 관리하고, `RealtimeMarketDataIngestionService`가 KIS 체결 tick을 수신하면 KRW/현지통화/FX metadata가 포함된 `MarketQuote` JSON을 송신한다.
- 협력사가 `QUOTE_STREAM_REPLAY` 메시지를 보내면 현재 quote snapshot을 요청 통화 기준으로 재송신한다.
- `MarketDataService`는 KIS 현재가 REST에서 수집한 외국인보유량 snapshot이 있으면 외국인 보유수량, 지분율, 한도소진율을 quote payload에 반영한다. KIS 실시간 체결가·호가 WebSocket은 가격·호가·상태 전용이며 외국인 보유량 필드를 제공하지 않는다.
- 주문 가능 여부 boundary는 Hannah-Montana-AI 외국인 보유 시계열 예측 API를 우선 호출하고, AI 장애 시 OmniLens 내부 deterministic 시계열 엔진으로 fallback한다. 차단은 AI confidence가 아니라 현재 snapshot에 주문수량 영향을 더한 확정 한도소진율 기준으로만 수행한다.
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
- 전문과 이미지 URL은 Naver Search row에서 직접 얻는 값이 아니다. 허용된 원문 provider 또는 공시 원문에서 수집하고, 재배포 권리가 불확실하면 전문 저장·응답 대신 요약과 원문 링크만 제공한다.
- WebSocket subscription 계약 테스트가 실제 STOMP client로 topic 수신과 협력사 topic 권한을 검증한다.
