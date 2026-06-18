# 아키텍처

## 목적
- 해외 협력사 거래소가 한국 주식 정보와 뉴스·공시 알림을 연동하는 B2B API를 제공한다.
- 실제 주문, 체결, 정산, 환전, 옴니버스 계좌 처리는 이 레포 범위에서 제외한다.

## 서비스 구성
- `market`: 한국 주식 현재가, 호가, 종목 검색 API, 전체/다건 quote snapshot API, 협력사용 market quote WebSocket stream
- Planned `market`: KRX 과거 시세 수집·정규화·DB 저장·history API
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
- KRX: 전일 외국인 보유율과 한도소진율
- FX provider: 실시간/준실시간 환율
- 한국수출입은행: 고시 환율 fallback
- Naver News Search: 뉴스 제목, snippet, 원문 링크
- OpenDART: 공시 제목, 유형, 제출시각, 원문 링크
- Hannah-Montana-AI: 뉴스·공시 종목 매핑, 이벤트, 감성, 중요도 분석

## 현재 구현 상태
- KIS 모의투자 현재가 REST, KIS 모의투자 실시간 체결·호가 WebSocket runner, 공공데이터 주식시세, KRX 외국인보유량, FX 환율, 한국수출입은행 고시 환율 fallback, Naver News Search, OpenDART, Hannah-Montana-AI 어댑터가 구현되어 있다.
- `MarketDataService`가 표준 응답 구조와 현지 통화 환산 로직을 제공한다.
- `GET /api/v1/market/quotes`는 stockCodes가 있으면 요청 순서의 다건 snapshot을, 없으면 종목 마스터 기반 전체 snapshot을 반환한다.
- 종목 마스터는 Flyway가 생성한 `stock_master` 테이블과 JDBC 저장소를 사용한다.
- 초기 종목 universe는 `stock-master-seed.csv`에서 애플리케이션 시작 시 한 번 적재하며, 이미 데이터가 있으면 중복 적재하지 않는다.
- 협력사 watchlist는 Flyway가 생성한 `partner_watchlist_subscription` 테이블과 JDBC 저장소를 사용한다.
- watchlist 종목은 `stock_master` FK로 제한하며, REST API 저장 시 미지원 종목은 404로 거부한다.
- 협력사 API key는 Flyway가 생성한 `partner_api_credential` 테이블에 SHA-256 해시와 `partner_id`로 저장한다.
- `MarketDataService`는 KIS 실시간 체결 cache, KIS 현재가 REST, 공공데이터 전일 snapshot, mock fallback 순서로 quote 응답 구조를 유지한다.
- `MarketDataService`는 KIS 실시간 호가 cache가 있으면 orderbook 응답에 우선 반영한다.
- `MarketQuoteWebSocketHandler`는 raw WebSocket `/ws/market/quotes`에서 인증된 협력사 연결을 관리하고, `RealtimeMarketDataIngestionService`가 KIS 체결 tick을 수신하면 KRW/현지통화/FX metadata가 포함된 `MarketQuote` JSON을 송신한다.
- 협력사가 `QUOTE_STREAM_REPLAY` 메시지를 보내면 현재 quote snapshot을 요청 통화 기준으로 재송신한다.
- `MarketDataService`는 KRX 외국인보유량 snapshot이 있으면 전일 외국인 보유수량, 지분율, 한도소진율을 quote payload에 반영한다.
- KRX 기준일 조회 실패 시 최근 7일 탐색을 계속하고, 전체 실패 시 프로세스 캐시의 전일 확정 snapshot을 사용한다.
- 협력사 입력 환율은 `ExchangeRateCache`에 `KRW -> 현지통화` 표시용 환율로 저장하고, quote 요청에 `fxRate`가 없을 때 현지 통화 환산가 계산에 사용한다.
- `ExchangeRateCache`는 Redis TTL 저장소를 기본으로 사용하고 Redis 장애 시 프로세스 단위 in-memory fallback을 사용한다.
- FX 환율 provider는 최신 `KRW -> 현지통화` 비율을 같은 `ExchangeRateCache`에 저장한다.
- 환율 refresh scheduler는 기본 disabled이며, 설정된 통화 목록만 FX provider로 주기 갱신한다.
- `AlertStreamingService`가 알림 이벤트를 협력사·종목 topic으로 송신한다.
- `AlertProviderCollectionService`가 종목별 뉴스·공시를 수집하고 AI 분석 결과를 WebSocket 알림으로 발행한다.
- 알림 스케줄러는 설정 파일 watchlist와 DB watchlist를 협력사별로 병합해 같은 수집·분석·발행 경로를 재사용한다.
- 뉴스·공시 중복 재발행 방지는 Redis TTL 기반 dedupe를 기본으로 사용하고, Redis 장애 시 프로세스 단위 in-memory fallback을 사용한다.
- WebSocket subscription 계약 테스트가 실제 STOMP client로 topic 수신과 협력사 topic 권한을 검증한다.
