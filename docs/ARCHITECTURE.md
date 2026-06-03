# 아키텍처

## 목적
- 해외 협력사 거래소가 한국 주식 정보와 뉴스·공시 알림을 연동하는 B2B API를 제공한다.
- 실제 주문, 체결, 정산, 환전, 옴니버스 계좌 처리는 이 레포 범위에서 제외한다.

## 서비스 구성
- `market`: 한국 주식 현재가, 호가, 종목 검색 API
- `alert`: 뉴스·공시 분석 결과를 협력사와 종목 topic으로 송신하는 API
- `config`: API key 검증, CORS, WebSocket 설정

## API 경계
- REST: `/api/v1/market/**`, `/api/v1/alerts/**`
- WebSocket: `/ws/alerts`
- 협력사 topic: `/topic/partners/{partnerId}/alerts`
- 종목 topic: `/topic/stocks/{stockCode}/alerts`
- REST와 WebSocket handshake는 모두 협력사 API key 보호 대상이다.

## 외부 시스템
- KIS Open API: 현재가, 실시간 체결가, 실시간 호가
- KRX: 전일 외국인 보유율과 한도소진율
- 한국수출입은행: 환율
- Naver News Search: 뉴스 제목, snippet, 원문 링크
- OpenDART: 공시 제목, 유형, 제출시각, 원문 링크
- Hannah-Montana-AI: 뉴스·공시 종목 매핑, 이벤트, 감성, 중요도 분석

## 현재 구현 상태
- KIS 현재가 REST, 공공데이터 주식시세, KRX 외국인보유량, Naver News Search, OpenDART, Hannah-Montana-AI 어댑터가 구현되어 있다.
- `MarketDataService`가 표준 응답 구조와 현지 통화 환산 로직을 제공한다.
- `MarketDataService`는 KIS 현재가를 우선 사용하고, KIS 미설정·장애·무응답 시 공공데이터 전일 snapshot과 mock fallback 순서로 응답 구조를 유지한다.
- `MarketDataService`는 KRX 외국인보유량 snapshot이 있으면 전일 외국인 보유수량, 지분율, 한도소진율을 quote payload에 반영한다.
- KRX 기준일 조회 실패 시 최근 7일 탐색을 계속하고, 전체 실패 시 프로세스 캐시의 전일 확정 snapshot을 사용한다.
- 협력사 입력 환율은 `ExchangeRateCache`에 `KRW -> 현지통화` 표시용 환율로 저장하고, quote 요청에 `fxRate`가 없을 때 현지 통화 환산가 계산에 사용한다.
- `AlertStreamingService`가 알림 이벤트를 협력사·종목 topic으로 송신한다.
- `AlertProviderCollectionService`가 종목별 뉴스·공시를 수집하고 AI 분석 결과를 WebSocket 알림으로 발행한다.
- 뉴스·공시 중복 재발행 방지는 Redis TTL 기반 dedupe를 기본으로 사용하고, Redis 장애 시 프로세스 단위 in-memory fallback을 사용한다.
- WebSocket subscription 계약 테스트가 실제 STOMP client로 두 topic 수신을 검증한다.
