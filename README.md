# Hana OmniLens API

해외 거래소·브로커에 한국 주식 시장 데이터, 뉴스·공시 인텔리전스, 주문 제한 신호, 세무 문서 검증을 제공하는 B2B API 서버다. 실제 주문·체결·정산·환전과 최종투자자 계정·원장은 협력사 시스템이 관리한다.

## 서비스 구성과 책임

| 저장소 | 책임 |
| --- | --- |
| `Hana-OmniLens-API` | KIS·KRX·환율·뉴스·공시 수집, 데이터 정규화, Hannah AI 호출, 협력사 REST/WebSocket 제공 |
| `Hannah-Montana-AI` | 뉴스·공시 NLP, 번역, 금융 용어 해설, 글로벌 피어, 외국인 보유 예측, 세무 OCR·규칙 추론 |
| `Stock-exchange-BE` | 사용자·계좌·관심종목·모의 원장·알림·세무 신청 상태와 FE용 API |
| `Stock-exchange-FE` | Flutter iOS/Android 영어 MTS 화면 |

## 핵심 기능 01. 한국 주식 뉴스·공시 인텔리전스

- Naver News와 OpenDART에서 시장·종목 뉴스, 공시 목록과 허용된 전문·이미지를 수집한다.
- 인기 종목, 외국인 보유 제한 종목, 협력사 watchlist를 주기 수집 대상으로 관리한다.
- Hannah AI가 종목 매핑, 이벤트·감성·중요도 분류, What/Why/Impact 요약, 중복 키와 confidence를 생성한다.
- 로컬 Qwen3 번역 결과와 provider·model version·품질 플래그를 REST/WebSocket payload에 전달한다.
- 시장 뉴스 목록·상세·트렌딩, 종목별 이벤트, 협력사 watchlist, 수동 재처리·수집·발행 API를 제공한다.
- 한국 금융 고유어·전문용어를 단일 검증 사전으로 해설하고 evidence, confidence, cache 정책과 해시 기반 클릭 통계를 제공한다.

데이터 흐름은 `Naver/OpenDART → Hana 수집·저장 → Hannah 분석·번역 → Hana REST/WebSocket → Stock-exchange-BE → Flutter`다.

## 핵심 기능 02. 실시간 주식 정보 및 주문 제한 필터링

- KIS 공식 마스터와 KRX 데이터를 기준으로 활성 종목, 검색, 상세, 현재가, 지수, 호가, 일봉·분봉, 환율 스냅샷을 제공한다.
- `/ws/market/quotes`로 실시간 quote와 replay 스냅샷을 송신하고 상세 진입 종목의 KIS 원천 구독을 추가·해제한다.
- 국내 종목을 해외 상장 peer와 연결해 3개 비교 차원과 국내 종목의 4개 핵심 강점을 제공한다.
- 외국인 보유수량·보유율·한도소진율을 수집·백필하고, 제한 종목의 당일 예측을 장전 선계산한다.
- VI, 단일가, 상·하한가, 거래정지, 외국인 한도 경고와 출처를 주문 가능 여부 API로 제공한다.
- 주문 가능 여부는 협력사 주문 화면의 참고 신호다. 최종 차단·체결·잔고 반영은 협력사 책임이다.

데이터 흐름은 `KIS/KRX/FX → Hana cache·DB → Hana REST/WebSocket → Stock-exchange-BE → Flutter`다.

## 핵심 기능 03. 글로벌 세무 처리 자동화(OCR)

- 최종투자자가 `거주자 증명서`, `아포스티유`, `제한세율 적용신청서`를 Stock-exchange-FE에서 선택해 Stock-exchange-BE에 순차 업로드한다.
- Stock-exchange-BE는 파일을 계정별로 격리 저장하고 문서 메타데이터와 검증 진행 상태를 관리한다.
- Hana OmniLens는 `POST /api/v1/tax/documents/verify`에서 문서 유형, 파일 내용, MIME과 검증 문맥을 Hannah AI에 전달한다.
- Hannah AI는 이미지/PDF magic byte, 크기, 문서 유형을 검사한 뒤 OCR, 문서별 parser/reviewer, 필수 필드, 국가·문서 간 일관성, 위변조 위험을 검증한다.
- 검증 결과는 `VERIFIED`, `REVIEW_REQUIRED`, `REJECTED` 상태, OCR confidence, 위험도, 누락 필드, 거절 사유, model version으로 반환한다.
- 세 문서가 검증된 경우에만 Stock-exchange-BE가 모의 매도 실현손익을 기준으로 환급 케이스와 상태 동기화를 진행한다.
- 환급액 선지급·환수는 컴플라이언스 샌드박스 상태와 위험 고지를 제공하는 범위다. 실제 세무 신고, 정부 승인, 지급·환수 실행은 운영·법무 승인과 외부 시스템 책임이다.

세무 흐름은 `Flutter 파일 선택 → Stock-exchange-BE 격리 저장 → Hana 검증 orchestration → Hannah OCR·규칙 검증 → BE 상태 관리 → Flutter 진행 표시`다.

## 주요 API

| 영역 | 경로 |
| --- | --- |
| 시장·시세 | `/api/v1/market/stocks/**`, `/api/v1/market/quotes`, `/api/v1/market/indices/**`, `/ws/market/quotes` |
| 뉴스·공시 | `/api/v1/market/news/**`, `/api/v1/alerts/**`, `/ws/alerts` |
| 금융 용어 | `/api/v1/korean-financial-terms/**` |
| 세무 OCR | `POST /api/v1/tax/documents/verify` |
| 협력사 자격증명 | `/api/v1/security/partners/**` |
| 계약 문서 | `/openapi.yaml`, `/v3/api-docs`, `/swagger-ui/index.html` |

모든 REST 응답은 `success/status/code/message/data/timestamp` envelope을 사용한다.

## 보안 기준

- 협력사 서버 요청은 해시로 저장한 API key, rate limit, correlation ID와 감사 로그로 보호한다.
- 운영 환경은 요청 서명 nonce 검증과 mTLS를 설정으로 강제할 수 있다.
- 프론트엔드에는 Hana, KIS, KRX, Naver, OpenDART 자격증명을 전달하지 않는다.
- 외부 자격증명과 DB·Redis 비밀번호는 GitHub Secrets가 생성하는 서버 환경 파일로만 주입한다.
- 세무 파일은 허용 형식·크기·magic byte를 검증하고 계정별 경로 격리, 무작위 저장명, 접근 로그를 적용한다.

## 로컬 실행과 검증

```bash
docker compose -f compose.local.yml up --build
curl http://localhost:8080/actuator/health
./gradlew test --no-daemon
./gradlew bootJar --no-daemon
```

로컬 시크릿은 gitignore된 `src/main/resources/application-local.yml`에만 둔다.

## 문서

- [아키텍처](docs/ARCHITECTURE.md)
- [API 표준](docs/API_STANDARD.md)
- [운영](docs/OPERATIONS.md)
- [배포](docs/DEPLOYMENT.md)
- [보안](docs/SECURITY.md)
- [테스트](docs/TESTING.md)
- [기능 분류와 책임](docs/FEATURE_CLASSIFICATION.md)
- [구현 기록](docs/IMPLEMENTATION_LOG.md)
