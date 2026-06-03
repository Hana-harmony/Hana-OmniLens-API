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

## 외부 시스템
- KIS Open API: 현재가, 실시간 체결가, 실시간 호가
- KRX: 전일 외국인 보유율과 한도소진율
- 한국수출입은행: 환율
- Naver News Search: 뉴스 제목, snippet, 원문 링크
- OpenDART: 공시 제목, 유형, 제출시각, 원문 링크
- Hannah-Montana-AI: 뉴스·공시 종목 매핑, 이벤트, 감성, 중요도 분석

## 현재 구현 상태
- 공공데이터 주식시세, KRX 외국인보유량, Naver News Search, OpenDART, Hannah-Montana-AI 어댑터가 구현되어 있다.
- `MarketDataService`가 표준 응답 구조와 현지 통화 환산 로직을 제공한다.
- `MarketDataService`는 KRX 외국인보유량 snapshot이 있으면 전일 외국인 보유수량, 지분율, 한도소진율을 quote payload에 반영한다.
- `AlertStreamingService`가 알림 이벤트를 협력사·종목 topic으로 송신한다.
- `AlertProviderCollectionService`가 종목별 뉴스·공시를 수집하고 AI 분석 결과를 WebSocket 알림으로 발행한다.
- 현재 중복 재발행 방지는 프로세스 단위 bounded in-memory dedupe이며, 운영 저장형 dedupe는 DB 또는 Redis TTL로 확장한다.
