# Hana OmniLens API

해외 거래소·브로커에 한국 주식 뉴스·공시 인텔리전스와 실시간 시장/주문 제한 신호를 제공하는 B2B API 서버다. 실제 주문, 체결, 정산, 환전, 최종투자자 계정 관리는 이 레포의 책임이 아니다.

## 핵심 기능 01. 한국 주식 뉴스·공시 인텔리전스

- Naver News, OpenDART 기반 시장·종목 뉴스와 공시를 수집한다.
- 인기 종목, 외국인 보유 제한 종목, 협력사 watchlist 종목을 주기 수집 대상으로 관리한다.
- 허용된 원문 기사와 OpenDART document를 정제해 전문, 대표 이미지, 원문 링크를 저장한다.
- Hannah-Montana-AI 분석 결과로 종목 매핑, 이벤트 분류, 감성, 중요도, What/Why/Impact 3줄 요약, 중복 키, confidence를 제공한다.
- 시장 뉴스 목록·상세·트렌딩 조회와 수동 수집 API를 제공한다.
- 협력사 watchlist 관리, 수동 분석 발행, 수집·분석·발행 API를 제공한다.
- DeepL 번역 결과, 한국 금융 용어 설명, 용어 클릭 통계를 REST/WebSocket payload로 전달한다.

## 핵심 기능 02. 실시간 주식 정보 제공 및 주문 제한 필터링 시스템

- KIS/KRX 기반 종목 마스터, 검색, 상세, 글로벌 피어, 현재가, 지수, 호가, 과거 시세, 환율 스냅샷을 제공한다.
- `/ws/market/quotes`로 협력사에 실시간 quote tick과 replay 스냅샷을 송신하고, 상세 진입 종목은 KIS 실시간 원천 구독을 추가·해제할 수 있다.
- 주식·지수 1D 분봉, 일봉 히스토리, 차트 캐시 예열, 과거 시세 수집 API를 제공한다.
- 외국인 보유 한도, 당일 한도소진율 예측 boundary, 예측 선계산, 모델 재학습, 스냅샷 수집·백필 API를 제공한다.
- VI, 단일가, 상·하한가, 거래정지 신호를 제공한다.
- 주문 가능 여부 API는 협력사 주문 화면이 참고할 제한 사유와 출처를 반환한다.
- 실제 주문 차단, 체결, 원장 반영은 협력사 또는 `Stock-exchange-BE` 책임이다.

## 주요 API

- Market: `/api/v1/market/stocks/**`, `/api/v1/market/quotes`, `/api/v1/market/indices/**`, `/ws/market/quotes`
- Market news: `/api/v1/market/news/**`
- Alerts: `/api/v1/alerts/**`, `/ws/alerts`
- Korean financial terms: `/api/v1/korean-financial-terms/**`
- Partner credentials: `/api/v1/security/partners/**`
- Spec: `/openapi.yaml`, `/v3/api-docs`, `/swagger-ui/index.html`

## 실행

```bash
docker compose -f compose.local.yml up --build
curl http://localhost:8080/actuator/health
```

개발 실행:

```bash
./gradlew test
./gradlew bootRun
```

로컬 secret은 `src/main/resources/application-local.yml`에만 둔다. 운영 민감값은 GitHub Secrets가 만든 서버 env 파일로 주입한다.

## 검증

```bash
./gradlew test --no-daemon
./gradlew bootJar --no-daemon
```

## 문서

- [통합 기능정의서](docs/FEATURE_DEFINITION.md)
- [아키텍처](docs/ARCHITECTURE.md)
- [API 표준](docs/API_STANDARD.md)
- [운영](docs/OPERATIONS.md)
- [배포](docs/DEPLOYMENT.md)
- [보안](docs/SECURITY.md)
- [테스트](docs/TESTING.md)
