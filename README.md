# Hana OmniLens API

해외 거래소·브로커에 한국 주식 시장 데이터, 뉴스·공시 인텔리전스, 주문 제한 신호, 세무 문서 검증을 제공하는 B2B API 서버다. 실제 주문·체결·정산·환전과 최종투자자 계정·원장은 협력사 시스템이 관리한다.

운영 모니터링, Discord 알림, Grafana 접속과 필수 secret은 [docs/OBSERVABILITY.md](docs/OBSERVABILITY.md)를 따른다.

## 서비스 구성과 책임

| 저장소 | 책임 |
| --- | --- |
| `Hana-OmniLens-API` | KIS·KRX·환율·뉴스·공시 수집, 데이터 정규화, Hana Montana AI 호출, 협력사 REST/WebSocket 제공 |
| `Hannah-Montana-AI` | `Hana Montana AI(KF-DeBERTa + K-FNSPID)` 뉴스·공시 NLP, 번역, 금융 용어 해설, 글로벌 피어, 외국인 보유 예측, 세무 OCR·규칙 추론 |
| `Stock-exchange-BE` | 사용자·계좌·관심종목·모의 원장·알림·세무 신청 상태와 FE용 API |
| `Stock-exchange-FE` | Flutter iOS/Android 영어 MTS 화면 |

## 핵심 기능 01. 한국 증시 인텔리전스

- Naver News와 OpenDART에서 신규 시장·종목 뉴스와 공시를 수집하면 분석·번역·저장 파이프라인을 거쳐 협력사 REST와 WebSocket으로 즉시 발행한다.
- 협력사 watchlist와 인기·외국인 보유 제한 종목을 수집 대상으로 관리하고, 해외 MTS가 사용자의 보유·관심종목 이벤트를 실시간 피드와 알림으로 제공할 수 있게 한다.
- `Hana Montana AI(KF-DeBERTa + K-FNSPID)`가 종목 연관성·이벤트·감성을 분류하고, 의미 중요도와 사후 가격반응 등급을 독립 신호로 반환한다. 가격반응은 의미 중요도를 덮어쓰지 않는다.
- 로컬 LLM이 원문 문단·줄바꿈을 보존하며 번역하고 What/Why/Impact 구조로 요약한다. provider·model version·품질 플래그를 payload에 함께 남긴다.
- RAG 기반 검증 사전이 한국 증시 고유어의 영문 표기, 문맥 해설, evidence, confidence를 제공하고 해시 기반 실제 클릭을 날짜별로 기록해 일·월·년·전체 시계열로 집계한다.
- 섹터·산업·사업 모델·재무 특성을 결합해 한국 종목과 비교할 글로벌 상장 피어를 매칭하고, 비교 차원·근거·신뢰도를 반환한다.
- 시장 뉴스 목록·상세·트렌딩, 종목별 뉴스·공시 이벤트, watchlist, 수동 재처리·수집·발행 API를 제공한다.

데이터 흐름은 `Naver/OpenDART → Hana 수집·저장 → Hannah 분석·번역 → Hana REST/WebSocket → Stock-exchange-BE → Flutter`다.

## 핵심 기능 02. 실시간 종목 스크리너

- KIS 공식 마스터와 KRX 데이터를 기준으로 활성 종목, 검색, 상세, 현재가, 지수, 호가, 일봉·분봉, 환율 스냅샷을 제공한다.
- 시계열 ML 예측 모델이 외국인 취득 한도 제한 32개 종목의 장중 외국인 보유수량·지분율 예상치를 제공한다. 일별 snapshot을 수집·백필하고 당일 예측을 장전 선계산해 cache에 저장한다.
- `/ws/market/quotes`로 실시간 환율을 적용한 quote와 replay 스냅샷을 송신하고 상세 진입 종목의 KIS 원천 구독을 추가·해제한다.
- 호가 틱, VI, 단일가, 상·하한가, 거래정지, 외국인 한도 경고와 출처·계산 버전을 주문 가능 여부 API로 제공한다.
- 주문 가능 여부는 협력사 주문 화면의 참고 신호다. 최종 차단·체결·잔고 반영은 협력사 책임이다.

데이터 흐름은 `KIS/KRX/FX → Hana cache·DB → Hana REST/WebSocket → Stock-exchange-BE → Flutter`다.

## 핵심 기능 03. 글로벌 세무 처리 자동화

- 배당소득 제한세율 적용에 필요한 `거주자 증명서`, `아포스티유`, `제한세율 적용신청서`를 OCR 기반으로 검증한다.
- 최종투자자가 세 문서를 Stock-exchange-FE에서 선택해 Stock-exchange-BE에 순차 업로드한다.
- Stock-exchange-BE는 파일을 계정별로 격리 저장하고 문서 메타데이터와 검증 진행 상태를 관리한다.
- Hana OmniLens는 `POST /api/v1/tax/documents/verify`에서 문서 유형, 파일 내용, MIME과 검증 문맥을 Hannah AI에 전달한다.
- Hannah AI는 이미지/PDF magic byte, 크기, 문서 유형을 검사한 뒤 OCR, 문서별 parser/reviewer, 필수 필드, 국가·문서 간 일관성, 위변조 위험을 검증한다.
- 검증 결과는 `VERIFIED`, `REVIEW_REQUIRED`, `REJECTED` 상태, OCR confidence, 위험도, 누락 필드, 거절 사유, model version으로 반환한다.
- 세 문서가 검증된 경우에만 Stock-exchange-BE가 원본 파일·MIME·SHA-256와 신청 상태를 동기화한다. 수익 추정값은 포털 계약으로 전달하지 않는다.
- 관리자는 원본 세 문서를 열람하고 OCR 필드를 자동 적용한 뒤, API가 실제 공식 PDF에서 렌더링한 화면의 해당 칸을 직접 수정해 저장·PDF 다운로드·승인 및 국세청 제출 처리를 수행한다. 화면 필드와 다운로드 PDF는 서버의 동일 좌표 계약을 사용한다.
- 거래소와 OmniLens의 세무 신청 ID는 암호학적 난수 기반 `TAX-` + 영숫자 12자리로 통일하며, 경로 변수 검증 실패는 500이 아닌 표준 400 응답으로 반환한다.
- 환급액 선지급·환수는 컴플라이언스 샌드박스 상태와 위험 고지를 제공하는 범위다. 실제 세무 신고, 정부 승인, 지급·환수 실행은 운영·법무 승인과 외부 시스템 책임이다.

세무 흐름은 `Flutter 파일 선택 → Stock-exchange-BE 격리 저장 → Hana 검증 orchestration → Hannah OCR·규칙 검증 → BE 상태 관리 → Flutter 진행 표시`다.

## 주요 API

| 영역 | 경로 |
| --- | --- |
| 시장·시세 | `/api/v1/market/stocks/**`, `/api/v1/market/quotes`, `/api/v1/market/indices/**`, `/ws/market/quotes` |
| 뉴스·공시 | `/api/v1/market/news/**`, `/api/v1/alerts/**`, `/ws/alerts` |
| 금융 용어 | `/api/v1/korean-financial-terms/**` |
| 세무 OCR | `POST /api/v1/tax/documents/verify` |
| 웹 회원·관리자 | `/api/v1/portal/**` |
| 협력사 자격증명 | `/api/v1/security/partners/**` |
| 계약 문서 | `/openapi.yaml`, `/v3/api-docs`, `/swagger-ui/index.html` |

모든 REST 응답은 `success/status/code/message/data/timestamp` envelope을 사용한다.

## 보안 기준

- 협력사 서버 요청은 해시로 저장한 API key, rate limit, correlation ID와 감사 로그로 보호한다.
- 운영 환경은 요청 서명 nonce 검증과 mTLS를 설정으로 강제할 수 있다.
- 프론트엔드에는 Hana, KIS, KRX, Naver, OpenDART 자격증명을 전달하지 않는다.
- 외부 자격증명과 DB·Redis 비밀번호는 GitHub Secrets가 생성하는 서버 환경 파일로만 주입한다.
- 세무 파일은 허용 형식·크기·magic byte를 검증하고 계정별 경로 격리, 무작위 저장명, 접근 로그를 적용한다.
- 웹 포털은 Bearer 토큰과 Spring Security RBAC를 사용하며, 비밀번호 변경 시 세션 버전을 올려 기존 토큰을 즉시 폐기한다.
- 초기 관리자 비밀번호는 `OMNILENS_PORTAL_BOOTSTRAP_ADMIN_PASSWORD`로만 주입한다. 신규 DB 또는 레거시 초기 계정은 기동 시 Argon2id 해시로 교체되며 초기 로그인 직후 비밀번호 변경을 강제한다.
- 포털 비밀번호는 12~128자이며 API 키 원문은 암호화 보관하고 활성 키의 소유 회원에게만 표시한다. 취소·승인·반려·재발급·폐기는 상태 전이와 RBAC로 통제한다.

## 로컬 실행과 검증

```bash
docker compose -f compose.local.yml up --build
curl http://localhost:8080/actuator/health
./gradlew test --no-daemon
./gradlew bootJar --no-daemon
```

로컬 시크릿은 gitignore된 `src/main/resources/application-local.yml`에만 둔다. 신규 DB를 처음 기동할 때는 16~128자의 `OMNILENS_PORTAL_BOOTSTRAP_ADMIN_PASSWORD`를 환경 변수로 주입한다. 이미 초기 비밀번호를 변경한 DB에는 이 값이 없어도 된다.

## 문서

- [아키텍처](docs/ARCHITECTURE.md)
- [API 표준](docs/API_STANDARD.md)
- [운영](docs/OPERATIONS.md)
- [배포](docs/DEPLOYMENT.md)
- [보안](docs/SECURITY.md)
- [테스트](docs/TESTING.md)
- [기능 분류와 책임](docs/FEATURE_CLASSIFICATION.md)
- [구현 기록](docs/IMPLEMENTATION_LOG.md)
