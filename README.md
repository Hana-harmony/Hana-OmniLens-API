# Hana OmniLens API

해외 협력사 거래소·브로커가 한국 주식 정보, 매매제한 판단 데이터, 뉴스·공시 인텔리전스, 세무 환급 상태를 붙일 수 있도록 제공하는 B2B API다.

## 범위
- Real-time Korea Market Data and Orderability API
- Foreign Ownership Limit and VI/Price Limit Status API
- Watchlist News and Disclosure Intelligence API
- Tax Refund Status and Advance Payout Integration API
- 실제 주문 실행, 체결, 정산, 환전, 최종투자자 계정 관리는 현지 거래소·브로커 영역으로 둔다.

## 빠른 시작
```bash
docker compose -f compose.local.yml up -d
./gradlew test
./gradlew bootRun
```

로컬 Docker 실행:
```bash
docker compose -f compose.local.yml up --build
curl http://localhost:8080/actuator/health
```

`compose.local.yml`은 API, Postgres, Redis를 함께 띄운다. 로컬 secret 파일은 `.dockerignore`로 이미지에 포함하지 않는다.

로컬 설정 파일 `src/main/resources/application-local.yml`은 gitignore 대상이다. 운영 설정 파일 `src/main/resources/application-prod.yml`은 실제 파일로 커밋하고, 민감값은 GitHub Secrets로 생성한 원격 서버 env 파일에서 주입한다.

알림은 수동 `collect-and-publish` 호출뿐 아니라 설정 기반 협력사 watchlist 스케줄러로도 수집·분석·WebSocket 발행할 수 있다.
협력사별 API key는 원문을 저장하지 않고 DB에 SHA-256 해시와 `partnerId`로 묶어 관리한다.

## API 계약
- `GET /openapi.yaml`
- OpenAPI 문서는 API key 보호 대상이며 REST endpoint와 WebSocket endpoint/topic 계약을 함께 기록한다.

## 주요 엔드포인트
- `GET /api/v1/market/stocks/{stockCode}`
- `GET /api/v1/market/stocks/{stockCode}/quote`
- `GET /api/v1/market/quotes`
- `GET /api/v1/market/stocks/{stockCode}/orderbook`
- `GET /api/v1/market/stocks/{stockCode}/history`
- `POST /api/v1/market/history/collect`
- `GET /api/v1/market/stocks/search?query=삼성`
- `WS /ws/market/quotes` raw JSON quote stream
- `POST /api/v1/alerts/events`
- `POST /api/v1/alerts/analyze-and-publish`
- `POST /api/v1/alerts/collect-and-publish`
- `GET/PUT /api/v1/alerts/watchlists/{partnerId}`
- `WS /ws/alerts`, topic `/topic/partners/{partnerId}/alerts`
- Swagger UI: `/swagger-ui/index.html`
- OpenAPI JSON: `/v3/api-docs`

## 최신 기획 기준
- 시장 데이터는 KIS 모의투자 현재가, 실시간 체결가·호가 WebSocket, KIS 종목 마스터, KRX 과거 시세, KIS REST snapshot/cache 기반 외국인 보유율, 실시간/준실시간 FX 환율을 합성한다.
- Market REST API는 성공/실패 모두 `success`, `status`, `code`, `message`, `data`, `timestamp`를 포함하는 공동 응답 형식을 사용한다.
- 주문 관련 기능은 실제 주문 API가 아니라 외국인 투자 한도, 당일 예측 지분율 min/base/max boundary, VI 발동, 상·하한가 상태를 현지 MTS 주문/종목 화면에 제공하는 의사결정 지원 API다.
- `GET /api/v1/market/stocks/{stockCode}/orderability`는 협력사 거래소가 자체 mock ledger 주문 전 확인할 외국인 한도 min/base/max 예측, VI, 상·하한가, 거래정지 상태를 공동 응답 형식으로 제공한다. 이 API는 실제 주문이나 KIS 모의투자 주문을 실행하지 않는다.
- 뉴스·공시는 Naver News Search와 OpenDART를 수집하고, Hannah-Montana-AI 분석 결과와 DeepL 번역 결과를 함께 WebSocket 이벤트로 송신한다. Papago는 레거시 provider로 제거되어 smoke report에서 `legacy_disabled`로만 기록한다.
- 세무 기능은 최종투자자별 서류/OCR/케이스 판정/환급금 선지급 상태를 현지 거래소 백엔드에 제공하는 연동 계약으로 관리한다. `POST /api/v1/tax/refund-cases/classify`는 한국·홍콩 조세조약 CASE_01 경계를 판정하고, `POST /api/v1/tax/refund-cases/sync`는 현지 거래소 mock 매도 실현손익 기반 tax case를 받아 환급/선지급 상태를 공동 응답 형식으로 반환한다. `GET /api/v1/tax/rectification-batches/{taxYear}/quarters/{quarter}`는 분기별 경정청구 배치 진행 상태를 조회한다.

## 문서
- [기여 가이드](CONTRIBUTING.md)
- [아키텍처](docs/ARCHITECTURE.md)
- [운영](docs/OPERATIONS.md)
- [배포](docs/DEPLOYMENT.md)
- [보안](docs/SECURITY.md)
- [테스트](docs/TESTING.md)
- [구현 기록](docs/IMPLEMENTATION_LOG.md)
- [API 표준](docs/API_STANDARD.md)
- [통합 기능정의서](docs/FEATURE_DEFINITION.md)
- [기능 분류와 레포 책임](docs/FEATURE_CLASSIFICATION.md)
- [로드맵](docs/ROADMAP.md)
- [깃 전략](docs/GIT_STRATEGY.md)
