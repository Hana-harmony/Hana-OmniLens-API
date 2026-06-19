# 구현 기록

## 2026-06-03 하네스 구축
- Spring Boot 3.5.14, Java 17, Gradle Wrapper 기반 API 프로젝트 생성
- Real-time Korea Market Data API 계약 초안 구현
- Watchlist News & Disclosure Alert API WebSocket 송신 계약 초안 구현
- API key SHA-256 해시 검증 필터 구현
- Git 전략, PR 템플릿, CI 하네스 추가

## 2026-06-04 profile 기반 설정 분리
- 로컬 설정을 gitignore된 `application-local.yml`로 분리
- 운영 설정을 커밋되는 실제 `application-prod.yml`과 GitHub Secrets 기반 env 파일로 분리
- 로컬 Postgres, Redis 컨테이너용 `compose.local.yml` 추가
- `main` push 시 GitHub Secrets로 원격 서버의 `application-prod.env`를 생성하는 배포 job 추가

## 2026-06-04 GHCR 기반 배포 전환
- jar scp 배포를 GHCR 이미지 push/pull 방식으로 변경
- `compose.prod.yml` 추가
- GHCR pull용 `deploy-prod.env`와 앱 런타임용 `application-prod.env` 분리
- CI/CD가 원격 서버에 `deploy.sh`까지 전송하고 Docker Compose로 재시작하도록 변경

## 2026-06-04 외부 제공자 클라이언트 하네스
- 공공데이터 주식시세, Naver News Search, OpenDART 공시검색 설정을 profile 기반으로 분리
- 운영 설정은 `PUBLIC_DATA_SERVICE_KEY`, `NAVER_NEWS_CLIENT_ID`, `NAVER_NEWS_CLIENT_SECRET`, `OPEN_DART_API_KEY` 환경변수로만 주입
- 로컬 실제 키는 gitignore된 `application-local.yml`에만 저장
- 제공자별 `RestClient` 어댑터를 추가하고 테스트에서는 `MockRestServiceServer`로 네트워크 없이 요청 헤더, 쿼리, 응답 매핑을 검증
- 키가 비어 있으면 호출 시점에 예외를 발생시켜 외부 API를 잘못 호출하지 않도록 처리

## 2026-06-04 시장 데이터 제공자 어댑터 연결
- `MarketDataService`가 공공데이터 주식시세 snapshot을 우선 사용하도록 변경
- 최근 7일 범위에서 전일 기준 가격 데이터를 탐색하고, 미설정·장애·무응답 시 목 시세로 fallback
- 종목 검색과 quote의 종목명 보강을 위해 인메모리 종목 마스터 저장소 추가
- 로컬 실제 키가 있어도 테스트가 외부망을 타지 않도록 컨트롤러 테스트에서 provider key를 비움
- 서비스 단위 테스트로 provider 성공, provider 실패 fallback, 종목 마스터 검색을 검증

## 2026-06-04 KRX 외국인보유량 snapshot 연결
- KRX 화면 데이터 API 의존성은 제거하고, KRX 인증키 기반 Open API client만 유지한다.
- 외국인보유량은 live 화면 데이터 호출 없이 cache 또는 기본 fallback 값만 사용한다.

## 2026-06-04 Hannah-Montana-AI 분석 클라이언트
- 내부 FastAPI 서비스 `POST /api/v1/alerts/analyze` 호출용 `HannahAiAnalysisClient` 추가
- AI 요청·응답 DTO는 Python API 계약에 맞춰 snake_case JSON 필드로 고정
- 스프링 컨테이너 내부 통신 전제에 따라 서비스 토큰 헤더는 사용하지 않음
- 운영 설정은 secret이 아닌 `HANNAH_AI_BASE_URL` 또는 기본 내부 서비스명으로 처리
- 테스트에서 토큰 헤더 미전송, stock universe payload, 분석 응답 매핑을 검증

## 2026-06-04 알림 분석 후 발행 endpoint
- `/api/v1/alerts/analyze-and-publish` endpoint 추가
- 수집된 뉴스·공시 원문과 종목 universe를 Hannah-Montana-AI에 전달하고 분석 결과를 WebSocket 알림 이벤트로 변환
- AI가 종목을 매핑하지 못하면 `422 Unprocessable Entity`로 발행을 중단
- 번역 어댑터가 붙기 전까지 `translatedTitle`은 원문 제목으로 대체
- MockMvc 테스트로 AI 분석 결과가 기존 알림 이벤트 payload로 발행되는지 검증

## 2026-06-04 뉴스·공시 provider 수집 후 발행 endpoint
- `/api/v1/alerts/collect-and-publish` endpoint 추가
- 협력사가 `partnerId`, `stockCodes`, 뉴스 수집 개수, 공시 조회 기간을 전달하면 종목별 provider 수집을 수행
- 인메모리 종목 마스터에 OpenDART 고유번호를 추가해 주식코드에서 공시검색 `corp_code`를 찾도록 구성
- Naver News Search 결과를 `NEWS` 분석 요청으로 변환하고 Hannah-Montana-AI 분석 후 기존 WebSocket 발행 로직을 재사용
- OpenDART 공시검색 결과를 `DISCLOSURE` 분석 요청으로 변환하고 접수번호 기반 원문 URL을 알림 payload에 포함
- 같은 partner, source type, original URL 조합은 bounded in-memory dedupe로 중복 재발행을 방지
- AI가 종목을 매핑하지 못한 건은 발행하지 않고 실패 카운트에 반영
- MockMvc 테스트로 뉴스·공시 수집, 중복 URL skip, AI 분석, 알림 발행 응답을 검증

## 2026-06-04 Redis 기반 알림 dedupe
- `AlertDedupeStore` 인터페이스를 추가해 알림 중복 방지 저장소를 서비스 로직에서 분리
- 기본 dedupe 모드를 Redis TTL 기반으로 설정하고 `omnilens.alert.dedupe.ttl`로 보존 시간을 조정 가능하게 구성
- Redis `SETNX` 의미의 `setIfAbsent`를 사용해 같은 partner, source type, original URL 조합의 재발행을 차단
- Redis 장애 시 bounded in-memory fallback으로 같은 프로세스 내 중복 발행을 계속 제한
- AI 분석 실패로 발행되지 않은 원문은 dedupe key를 제거해 다음 수집에서 재시도 가능하게 유지
- 테스트 profile에서는 외부 Redis 없이 `in-memory` 모드로 전환하고 Redis health indicator를 비활성화
- Redis store, in-memory store, fallback 동작을 단위 테스트로 검증

## 2026-06-04 협력사 watchlist 주기 수집 스케줄러
- `omnilens.alert.scheduler.enabled`가 켜진 경우 설정된 협력사 watchlist를 주기적으로 수집한다.
- 스케줄러는 `/collect-and-publish`와 같은 `AlertProviderCollectionService`를 재사용해 Naver News, OpenDART, Hannah-Montana-AI 분석, WebSocket 발행 흐름을 수행한다.
- 기본값은 disabled이며, `fixed-delay-ms`, `news-display`, `disclosure-lookback-days`, `watchlists`를 설정으로 조정한다.
- 한 협력사 수집 실패가 전체 스케줄러 중단으로 전파되지 않도록 파트너 단위로 예외를 격리한다.
- 종목명이 없는 잘못된 watchlist 설정은 provider 호출 전에 skip한다.
- 단위 테스트로 disabled 상태, 정상 요청 생성, 한 파트너 실패 후 다음 파트너 계속 처리, properties 기본값을 검증한다.

## 2026-06-04 협력사 watchlist DB 관리 API
- Flyway가 `partner_watchlist_subscription` 테이블을 생성하고 `stock_master` FK로 지원 종목만 저장되게 구성했다.
- `PUT /api/v1/alerts/watchlists/{partnerId}`는 협력사 watchlist 전체를 교체 저장한다.
- 요청 종목코드는 중복 제거 후 저장하며, 최초 요청 순서를 `sort_order`로 보존한다.
- 빈 `stockCodes`는 해당 협력사 watchlist 삭제로 처리해 운영자가 수집 대상을 즉시 비울 수 있게 했다.
- `GET /api/v1/alerts/watchlists/{partnerId}`는 현재 저장된 watchlist를 반환한다.
- 미지원 종목코드는 `StockMasterNotFoundException`을 통해 `404 Stock not found` ProblemDetail로 응답한다.
- 스케줄러는 설정 기반 watchlist와 DB watchlist를 협력사별로 병합해 주기 수집 대상으로 사용한다.
- 테스트로 JDBC 저장소의 교체 저장, 순서 보존, 전체 조회 grouping, HTTP validation, 미지원 종목 404, 스케줄러 병합을 검증했다.

## 2026-06-19 Alert REST 공동 응답 정합화
- `GET/PUT /api/v1/alerts/watchlists/{partnerId}`, `POST /api/v1/alerts/analyze-and-publish`, `POST /api/v1/alerts/collect-and-publish` 응답을 `ApiResponse` envelope으로 통일했다.
- `/api/v1/alerts/events`를 포함한 alert REST 200 응답 schema를 static OpenAPI에서 `PartnerWatchlistApiResponse`, `AlertEventApiResponse`, `AlertCollectPublishApiResponse`로 명시했다.
- MockMvc 테스트가 alert REST의 `success`, `status`, `code`, `data` envelope을 검증하도록 갱신했다.

## 2026-06-04 협력사별 API key registry
- Flyway가 `partner_api_credential` 테이블을 생성하고 API key SHA-256 해시, `partner_id`, active 상태를 저장하게 했다.
- `ApiKeyAuthenticationFilter`는 전역 bootstrap 해시를 먼저 상수 시간 비교하고, 일치하지 않으면 DB active credential을 조회한다.
- 전역 해시와 DB active credential이 모두 없으면 실패 닫힘 방식으로 `503`을 반환한다.
- DB credential 인증 성공 시 request attribute에 인증 `partnerId`와 API key fingerprint를 기록한다.
- 알림 API는 인증 `partnerId`가 있으면 요청 path/body의 `partnerId`와 일치하는지 검증하고, 불일치 시 `403 Partner access denied` ProblemDetail을 반환한다.
- 전역 bootstrap 키는 기존 테스트와 비상 운영 호환을 위해 partner 제한 없이 유지했다.
- CORS 허용 method에 `PUT`을 추가해 환율 저장과 watchlist 저장 API를 브라우저 기반 협력사 백엔드에서도 호출할 수 있게 했다.
- 테스트로 DB credential 조회, inactive credential 거부, 전역 해시 미설정 시 DB credential 인증, partner mismatch 403을 검증했다.

## 2026-06-04 WebSocket topic authorization
- WebSocket handshake interceptor가 REST API key 필터에서 인증한 `partnerId`와 API key fingerprint를 STOMP session attribute로 복사한다.
- STOMP inbound channel interceptor가 `SUBSCRIBE` destination을 검사한다.
- DB credential 세션은 `/topic/partners/{partnerId}/alerts`와 `/topic/partners/{partnerId}/stocks/{stockCode}/alerts`에서 자기 `partnerId`만 구독할 수 있다.
- DB credential 세션은 partner 구분이 없는 `/topic/stocks/{stockCode}/alerts` 구독을 거부한다.
- `AlertStreamingService`는 기존 partner topic, 기존 global stock topic에 더해 partner-scoped stock topic으로도 이벤트를 발행한다.
- 기존 global stock topic은 bootstrap 전역 키와 기존 계약 호환을 위해 유지한다.
- 실제 STOMP client 통합 테스트로 전역 키 기존 topic 수신, DB credential의 partner-scoped topic 수신, global stock topic 차단을 검증했다.

## 2026-06-04 OpenAPI 계약 문서
- `/openapi.yaml` 정적 OpenAPI 3.1 문서를 추가했다.
- 문서는 API key 보호 대상이며 협력사 API key가 있어야 조회할 수 있다.
- 시장 데이터 quote, orderbook, stock search REST 계약을 기록했다.
- 알림 수동 발행, AI 분석 후 발행, provider 수집 후 발행 REST 계약을 기록했다.
- STOMP WebSocket endpoint `/ws/alerts`와 topic `/topic/partners/{partnerId}/alerts`, `/topic/stocks/{stockCode}/alerts`는 `x-websocket` 확장으로 기록했다.
- MockMvc 테스트로 API key 보호, 문서 제공, 핵심 path/topic 포함 여부를 검증한다.

## 2026-06-04 API key rate limit
- 인증된 운영 API 요청에 API key SHA-256 fingerprint 단위 token bucket rate limit을 적용한다.
- API key 원문은 저장하지 않고 fingerprint만 bucket key로 사용한다.
- 기본값은 1분에 120개 요청이며 `omnilens.security.rate-limit` 설정으로 조정한다.
- 초과 요청은 `429 Too Many Requests`와 `Retry-After` 헤더를 반환한다.
- health/info 공개 endpoint는 rate limit bucket을 소비하지 않는다.
- 단위 테스트로 bucket refill, disabled 모드, 필터의 `429` 응답, public endpoint skip, properties 기본값을 검증한다.

## 2026-06-04 WebSocket subscription 계약 테스트
- 실제 STOMP client가 `/ws/alerts`에 API key handshake header를 포함해 연결하는 계약 테스트를 추가했다.
- 테스트는 `/topic/partners/{partnerId}/alerts`와 `/topic/stocks/{stockCode}/alerts`를 동시에 구독한다.
- REST `/api/v1/alerts/events`로 알림을 발행한 뒤 두 topic에서 같은 `alertId`의 `AlertEvent`를 수신하는지 검증한다.
- Spring Boot `ObjectMapper`를 WebSocket test client에 적용해 운영 JSON 직렬화 계약과 같은 방식으로 payload를 역직렬화한다.

## 2026-06-04 AI 분석 추적 메타데이터 전파
- Hannah AI 응답의 `duplicate_key`와 `model_version`을 `AlertEvent`의 `duplicateKey`, `modelVersion`으로 전파한다.
- 직접 알림 발행 API는 협력사 내부 재처리·마이그레이션용으로 두 필드를 선택값으로 허용한다.
- `analyze-and-publish`, `collect-and-publish`, WebSocket 계약 테스트에서 AI 중복 키와 모델 버전 전파를 검증한다.

## 2026-06-04 AI duplicateKey 기반 수집 중복 방지
- provider 원문 URL dedupe 이후 Hannah AI가 생성한 `duplicateKey`를 한 번 더 dedupe 기준으로 사용한다.
- dedupe key는 `partnerId`, `sourceType`, AI `duplicateKey`를 조합해 협력사·뉴스/공시 경계를 분리한다.
- 같은 기사 포장 차이로 URL은 다르지만 AI canonical key가 같은 뉴스는 WebSocket 발행 전에 건너뛴다.
- 테스트로 동일 URL 중복과 AI duplicateKey 중복이 모두 `skippedDuplicateCount`에 반영되는지 검증한다.

## 2026-06-04 입력 validation 실패 계약
- `ApiExceptionHandler`를 추가해 path, query, request body validation 실패를 `400 Bad Request` ProblemDetail로 통일했다.
- validation error type은 `https://hana-omnilens-api/errors/validation`으로 고정했다.
- 시장 API는 잘못된 종목코드, 통화코드, 환율, 빈 검색어를 거부하는 테스트를 추가했다.
- 알림 API는 잘못된 알림 payload, nested stock universe, 수집 limit과 종목코드를 거부하는 테스트를 추가했다.

## 2026-06-04 KRX 외국인보유량 재시도와 캐시
- KRX 외국인보유량 조회가 특정 기준일 장애로 실패해도 최근 7일 탐색을 계속하도록 수정했다.
- KRX snapshot 조회에 성공하면 `ForeignOwnershipSnapshotCache`에 저장한다.
- KRX provider가 전체 기간에서 장애 또는 무응답이면 캐시된 전일 확정 snapshot을 quote 응답에 사용한다.
- 캐시 사용 시 source는 `KRX_FOREIGN_OWNERSHIP_CACHE` suffix로 표시해 live provider와 구분한다.
- 단위 테스트로 첫 기준일 장애 후 이전 기준일 재시도, KRX 전체 장애 시 캐시 fallback을 검증했다.

## 2026-06-04 KIS 현재가 REST provider 연결
- KIS Open API 국내주식 현재가 endpoint `GET /uapi/domestic-stock/v1/quotations/inquire-price` 계약을 `KisCurrentPriceClient`로 격리했다.
- 요청 header는 `authorization`, `appkey`, `appsecret`, `tr_id=FHKST01010100`으로 고정했다.
- 요청 query는 `FID_COND_MRKT_DIV_CODE=J`, `FID_INPUT_ISCD={stockCode}`로 고정했다.
- KIS 응답의 `stck_prpr`, `prdy_ctrt`, `acml_vol`, `hts_kor_isnm`을 `KisCurrentPriceSnapshot`으로 변환한다.
- `MarketDataService`는 KIS 현재가를 가격 provider 1순위로 사용하고, KIS 미설정·장애·무응답 시 공공데이터 전일 snapshot과 mock fallback 순서로 quote 응답 구조를 유지한다.
- quote `source`는 `KIS_OPEN_API`, `PUBLIC_DATA_STOCK_SECURITIES`, `MOCK_MARKET_DATA`와 KRX suffix를 조합해 데이터 출처를 표시한다.
- KIS app key, app secret, access token은 env placeholder로만 관리하고, 테스트 fixture에는 가짜 값만 사용한다.
- 단위 테스트로 KIS 요청 헤더·쿼리·응답 매핑, MarketDataService의 KIS 우선 사용, 공공데이터 fallback을 검증했다.

## 2026-06-04 Papago NMT 알림 제목 번역
- Papago NMT `POST /v1/papago/n2mt` 계약을 `PapagoTranslationClient`로 격리했다.
- 요청 header는 `X-Naver-Client-Id`, `X-Naver-Client-Secret`으로 고정하고, 요청 body는 `source=ko`, `target=en`, `text={원문 제목}` form payload로 전송한다.
- 운영 설정은 `PAPAGO_TRANSLATION_CLIENT_ID`, `PAPAGO_TRANSLATION_CLIENT_SECRET` 환경변수 슬롯만 추가하고, 실제 값은 커밋하지 않는다.
- `AlertAnalysisPublishingService`가 Hannah-Montana-AI 분석 결과의 원문 제목을 번역해 `translatedTitle`에 넣도록 연결했다.
- 번역 키 미설정, Papago 장애, 빈 번역 결과는 알림 발행을 막지 않고 원문 제목으로 fallback한다.
- 단위 테스트로 Papago 요청 계약, 번역 성공, 번역 실패 fallback, 분석 후 발행 payload의 번역 제목 반영을 검증했다.

## 2026-06-19 DeepL 알림 제목 번역 fallback chain
- DeepL `POST /v2/translate` 계약을 `DeepLTranslationClient`로 격리했다.
- 요청 header는 `Authorization: DeepL-Auth-Key {apiKey}`로 고정하고, 요청 body는 `source_lang=KO`, `target_lang=EN-US`, `text={원문 제목}` form payload로 전송한다.
- 운영 설정은 `DEEPL_TRANSLATION_BASE_URL`, `DEEPL_API_KEY` 환경변수 슬롯만 추가하고, 실제 값은 커밋하지 않는다.
- `AlertTitleTranslationService`는 DeepL을 먼저 시도하고, DeepL 키 미설정·장애·빈 결과 시 Papago를 시도한 뒤, Papago도 실패하면 원문 제목으로 fallback한다.
- 단위 테스트로 DeepL 요청 계약, DeepL 우선 번역, Papago fallback, 전체 provider 실패 시 원문 fallback을 검증했다.

## 2026-06-04 HMAC 요청 서명 인증
- `omnilens.security.signature.enabled`가 켜진 경우 보호 API 요청에 HMAC-SHA256 서명을 요구한다.
- canonical string은 `METHOD`, `URI_WITH_QUERY`, `TIMESTAMP`, `NONCE`, `SHA256_BODY_HEX`를 줄바꿈으로 연결한다.
- 협력사는 `X-HANA-OMNILENS-TIMESTAMP`, `X-HANA-OMNILENS-NONCE`, `X-HANA-OMNILENS-SIGNATURE` 헤더를 전송한다.
- timestamp는 기본 5분 clock skew 안에서만 허용하고, nonce는 같은 API key fingerprint에서 재사용할 수 없다.
- 서명 검증이 켜졌는데 `OMNILENS_SIGNATURE_SECRET`이 비어 있으면 실패 닫힘 방식으로 `503`을 반환한다.
- POST body도 signature에 포함되도록 request body를 필터에서 캐시한 뒤 컨트롤러로 전달한다.
- 단위 테스트로 body 변조 감지와 nonce 저장을 검증하고, MockMvc 통합 테스트로 유효 서명, 누락 서명, nonce replay, stale timestamp를 검증했다.

## 2026-06-04 감사 로그와 correlation id
- `CorrelationIdFilter`를 최우선 필터로 추가해 모든 응답에 `X-HANA-OMNILENS-CORRELATION-ID`를 포함한다.
- 협력사가 안전한 형식의 correlation id를 보내면 그대로 사용하고, 없거나 안전하지 않으면 서버가 UUID를 생성한다.
- correlation id는 로그 MDC `correlationId`에 저장해 요청 처리 로그와 장애 추적을 연결한다.
- `SecurityAuditLogger`는 인증 성공, API key 미설정, invalid API key, rate limit, invalid signature 이벤트를 `com.hana.omnilens.audit.security` logger로 기록한다.
- 감사 로그에는 API key 원문을 남기지 않고 SHA-256 hash prefix만 남긴다.
- 테스트로 correlation id echo/generation과 감사 로그 fingerprint masking을 검증했다.

## 2026-06-04 Redis 기반 request signature nonce store
- `ApiSignatureNonceStore` 구현을 설정 기반으로 선택하도록 분리했다.
- 운영 기본값은 Redis nonce store이며 key prefix는 `omnilens:security:signature:nonce:`이다.
- Redis nonce store는 `SETNX`와 TTL을 사용해 같은 API key fingerprint와 nonce 조합의 재사용을 차단한다.
- Redis nonce store가 없거나 장애가 발생하면 replay 방어를 보장할 수 없으므로 서명 검증 요청을 `503`으로 실패 닫힘 처리한다.
- 로컬·테스트용 `in-memory` mode는 명시적으로 선택한 경우에만 사용한다.
- 단위 테스트로 Redis `SETNX` TTL, duplicate nonce, expired nonce window, 설정별 store 선택을 검증했다.

## 2026-06-04 협력사 입력 환율 캐시
- `ExchangeRateCache` 포트를 추가해 환율 저장소를 시장 데이터 계산 로직에서 분리했다.
- 현재 구현은 `InMemoryExchangeRateCache`이며 `KRW -> 현지통화` 표시용 환율과 갱신 시각을 프로세스 캐시에 보관한다.
- `PUT /api/v1/market/exchange-rates/{currency}` endpoint로 협력사가 현지 통화 환율을 입력할 수 있다.
- quote 요청에 `fxRate`가 있으면 요청값을 우선 사용하고, 없으면 협력사가 저장한 환율 캐시를 사용한다.
- 저장된 환율도 없으면 기존처럼 `1`을 사용해 KRW 가격을 그대로 유지한다.
- 이 기능은 표시용 현지 통화 환산가를 위한 것이며 실제 환전, 정산, 주문 처리와 연결하지 않는다.
- 단위 테스트와 MockMvc 테스트로 환율 저장, quote fallback, 요청 `fxRate` 우선순위, validation 실패를 검증했다.

## 2026-06-04 KIS 실시간 체결·호가 WebSocket 계약 하네스
- KIS 공식 샘플의 국내주식 실시간체결가 `H0STCNT0`, 국내주식 실시간호가 `H0STASP0` 계약을 기준으로 구독 frame 생성을 구현했다.
- 구독 frame은 `approval_key`, `tr_type`, `custtype=P`, `body.input.tr_id`, `body.input.tr_key` 구조로 직렬화한다.
- 구독 등록은 `tr_type=1`, 해제는 `tr_type=2`로 고정했다.
- KIS 실시간 데이터 frame `0|{tr_id}|...|field^field...`를 `KisRealtimeTradeTick`, `KisRealtimeOrderBookSnapshot`으로 파싱한다.
- `RealtimeMarketDataCache`를 추가해 수신된 최신 체결 tick과 호가 snapshot을 시장 데이터 서비스에서 조회할 수 있게 했다.
- quote는 실시간 체결 cache를 KIS REST 현재가보다 우선 사용하고, orderbook은 실시간 호가 cache가 있으면 mock 호가보다 우선 사용한다.
- KIS WebSocket approval key는 `KIS_APPROVAL_KEY` placeholder로 분리하고, REST access token과 혼용하지 않는다.
- 이 단계는 네트워크 연결 loop 이전의 계약·파서·캐시 하네스이며, 실제 WebSocket session runner는 다음 기능 단위에서 구현한다.
- 단위 테스트로 구독 frame JSON, TR ID, payload field mapping, 실시간 cache 우선순위를 검증했다.

## 2026-06-04 KIS 실시간 메시지 ingestion service
- `RealtimeMarketDataIngestionService`를 추가해 KIS raw WebSocket frame 처리와 cache 반영을 분리했다.
- 체결 메시지는 `KisRealtimeTradeTick`으로 파싱해 `RealtimeMarketDataCache.putTrade`에 저장한다.
- 호가 메시지는 `KisRealtimeOrderBookSnapshot`으로 파싱해 `RealtimeMarketDataCache.putOrderBook`에 저장한다.
- ping, 시스템 응답, 미지원 TR은 cache를 변경하지 않고 `IGNORED` 결과로 반환한다.
- 단위 테스트로 체결 저장, 호가 저장, 미지원 메시지 무시를 검증했다.

## 2026-06-04 KIS 실시간 WebSocket session runner
- `KisRealtimeSessionRunner`를 추가해 애플리케이션 준비 후 KIS 실시간 구독을 시작할 수 있게 했다.
- 기본값은 `omnilens.market.kis-realtime.enabled=false`이며, 비활성화 상태에서는 외부 WebSocket에 연결하지 않는다.
- 활성화 시 `omnilens.market.kis-realtime.stock-codes`의 각 종목에 대해 체결 `H0STCNT0`와 호가 `H0STASP0` 구독 frame을 생성한다.
- `StandardKisRealtimeWebSocketConnection`은 연결 수립 후 구독 frame을 전송하고, 수신한 text message를 ingestion service로 전달한다.
- 빈 종목코드 설정은 `KisRealtimeProperties`에서 제거해 env placeholder 기본값이 구독 요청으로 전파되지 않게 했다.
- 단위 테스트로 disabled no-op, 종목별 구독 frame 생성, 수신 메시지 cache 반영, 빈 종목코드 제거를 검증했다.

## 2026-06-04 Frankfurter 환율 provider 연결
- Frankfurter `GET /v2/rates` 계약을 `FrankfurterExchangeRateClient`로 격리했다.
- 요청 query는 `base=KRW`, `quotes={통화}`로 고정했다.
- Frankfurter는 별도 API key를 사용하지 않으므로 환율 provider secret을 두지 않는다.
- `ExchangeRateProviderRefreshService`가 provider snapshot을 `ExchangeRateCache`에 저장해 기존 quote 환산 fallback 흐름과 같은 캐시를 사용하게 했다.
- 단위 테스트로 요청 query, 응답 매핑, provider 미응답 시 cache 미변경을 검증했다.

## 2026-06-04 한국수출입은행 환율 refresh scheduler
- `ExchangeRateRefreshProperties`를 추가해 `enabled`, `fixedDelayMs`, `baseDateOffsetDays`, `currencies`를 설정으로 분리했다.
- 기본값은 disabled이며 통화 목록이 비어 있으면 외부 provider를 호출하지 않는다.
- 활성화 시 한국 시간 기준 오늘에서 `baseDateOffsetDays`를 뺀 날짜로 설정 통화를 순회 refresh한다.
- 한 통화의 provider 장애는 warn log로 격리하고 다음 통화 refresh를 계속한다.
- 단위 테스트로 disabled no-op, 통화코드 정규화, 기준일 offset, 통화별 장애 격리를 검증했다.

## 2026-06-04 Redis 기반 환율 cache
- `ExchangeRateCacheProperties`를 추가해 환율 cache 저장소를 `redis`와 `in-memory` 모드로 전환할 수 있게 했다.
- 기본 모드는 Redis이며 `EXCHANGE_RATE_CACHE_TTL`로 저장 TTL을 조정한다.
- `RedisExchangeRateCache`는 `ExchangeRateSnapshot`을 JSON으로 직렬화해 `omnilens:market:exchange-rate:{currency}` key에 저장한다.
- Redis 조회·저장 장애 또는 payload 역직렬화 실패 시 `InMemoryExchangeRateCache` fallback을 사용한다.
- Redis 저장 성공 후에도 fallback cache를 갱신해 일시 장애 시 마지막 성공 값을 같은 프로세스에서 계속 사용할 수 있게 했다.
- 단위 테스트로 TTL 저장, Redis payload 조회, Redis 장애 fallback, properties 기본값을 검증했다.

## 2026-06-04 Redis 기반 외국인 보유율 cache
- `ForeignOwnershipSnapshotCache` 구현을 설정 기반 bean으로 전환했다.
- 기본 모드는 Redis이며 `FOREIGN_OWNERSHIP_CACHE_MODE`, `FOREIGN_OWNERSHIP_CACHE_TTL`로 조정한다.
- `RedisForeignOwnershipSnapshotCache`는 `omnilens:market:foreign-ownership:{stockCode}` key에 KRX snapshot JSON을 TTL 저장한다.
- Redis 조회·저장 장애 또는 payload 역직렬화 실패 시 `InMemoryForeignOwnershipSnapshotCache` fallback을 사용한다.
- Redis 저장 성공 후에도 fallback cache를 갱신해 KRX/Redis 일시 장애 시 마지막 성공 snapshot을 같은 프로세스에서 계속 사용할 수 있게 했다.
- 단위 테스트로 TTL 저장, Redis payload 조회, Redis 장애 fallback, properties 기본값을 검증했다.

## 2026-06-04 외부 provider resilience policy
- `ExternalProviderResilienceProperties`를 추가해 provider connect timeout, read timeout, retry, circuit breaker를 공통 설정으로 분리했다.
- 모든 `RestClient` builder는 `SimpleClientHttpRequestFactory` 기반 timeout을 사용한다.
- `ExternalProviderResiliencePolicy`는 provider 이름별 circuit state를 관리하고 `RestClientException` 계열 장애만 재시도한다.
- 재시도 기본값은 2회, backoff 기본값은 150ms다.
- circuit breaker 기본값은 연속 실패 5회 후 30초 open이다.
- Naver News, OpenDART, Papago, KIS 현재가, 공공데이터 주식시세, KRX 외국인보유량, 한국수출입은행 환율, Hannah-Montana-AI 내부 분석 호출에 정책을 적용했다.
- Hannah-Montana-AI 호출에는 별도 서비스 토큰을 추가하지 않고 내부 네트워크 호출 모델을 유지했다.
- `application-prod.yml`은 `PROVIDER_*` placeholder를 사용하고, CI/CD가 기본값 포함 `application-prod.env`를 자동 생성한다.
- 단위 테스트로 retry 성공, circuit open, 비네트워크 예외 no-retry, properties 기본값을 검증했다.

## 2026-06-04 종목 마스터 DB 적재
- Flyway migration으로 `stock_master` 테이블과 종목코드, 종목명, 시장구분, ISIN, OpenDART 고유번호 조회 인덱스를 생성했다.
- 기존 인메모리 종목 마스터 bean을 JDBC 저장소로 교체해 검색 API, quote 보강, 뉴스·공시 수집의 종목 universe가 같은 DB 데이터를 사용하게 했다.
- `stock-master-seed.csv`에 KOSPI/KOSDAQ 주요 종목 seed를 추가하고, 애플리케이션 시작 시 테이블이 비어 있을 때만 적재하도록 구성했다.
- seed loader는 header, 컬럼 수, 종목코드, ISIN, OpenDART 고유번호 형식을 검증해 깨진 CSV가 운영 DB에 들어가지 않게 한다.
- OpenDART 고유번호가 아직 확정되지 않은 종목은 빈 값으로 허용하되, 확정된 종목은 수집 단계에서 공시검색 `corp_code`로 사용한다.
- 운영 설정은 `STOCK_MASTER_SEED_ENABLED`, `STOCK_MASTER_SEED_LOCATION` placeholder로 분리하고, 로컬 실제 설정은 gitignore된 `application-local.yml`에서 관리한다.
- H2 PostgreSQL mode와 Flyway를 사용하는 통합 테스트로 schema 생성, seed 적재, 중복 실행 방지, 코드·한글명·영문명 검색, 검색 API 응답을 검증했다.

## 2026-06-04 종목 마스터 단건 조회 API
- `GET /api/v1/market/stocks/{stockCode}` endpoint를 추가해 협력사 백엔드가 watchlist·보유 종목 동기화에 필요한 종목 메타데이터를 코드로 직접 조회할 수 있게 했다.
- 응답은 quote, orderbook, 뉴스·공시 수집에서 쓰는 `StockSummary` 계약을 `data`에 담은 공동 응답 envelope으로 반환하며, 종목코드, 한글명, 영문명, 시장구분, ISIN, OpenDART 고유번호를 포함한다.
- 미지원 종목코드는 `404 Not Found`와 `https://hana-omnilens-api/errors/stock-not-found` ProblemDetail로 반환해 validation 오류와 구분한다.
- OpenAPI 문서에 단건 조회 path와 404 응답을 추가했다.
- MockMvc 테스트로 정상 조회, 미지원 종목 404, 잘못된 종목코드 validation 실패를 검증했다.

## 2026-06-19 Market REST 공동 응답 정합화
- `GET /api/v1/market/stocks/{stockCode}`와 `PUT /api/v1/market/exchange-rates/{currency}`가 `ApiResponse` envelope을 반환하도록 정리했다.
- static OpenAPI의 market quote, bulk quote, orderbook, orderability, history, history collect, stock search, stock detail, exchange-rate schema를 typed `ApiResponse*`로 맞춰 Swagger에서 본문-only 계약으로 보이지 않게 했다.
- MockMvc 테스트로 종목 단건 조회와 환율 갱신의 `success`, `status`, `code`, `data` envelope을 검증한다.

## 2026-06-04 mTLS client certificate gate
- `omnilens.security.mtls.enabled` 설정을 추가해 운영에서 보호 API 요청의 client certificate 존재를 앱 레벨에서 검증할 수 있게 했다.
- mTLS가 활성화되면 `/actuator/health`, `/actuator/info`를 제외한 요청은 `jakarta.servlet.request.X509Certificate` 속성이 없을 경우 `401 Unauthorized`로 거부한다.
- client certificate가 있더라도 유효 기간이 지났거나 아직 유효하지 않으면 `401 Unauthorized`로 거부한다.
- TLS 직접 종료용 `server.ssl.*` 운영 placeholder와 keystore/truststore base64 GitHub Secrets 배포 흐름을 추가했다.
- 운영 healthcheck를 위해 `SERVER_SSL_CLIENT_AUTH=want`, `OMNILENS_MTLS_ENABLED=true`, `HEALTHCHECK_SCHEME=https` 조합을 문서화했다.
- OpenAPI에 `mutualTLS` security scheme을 추가했다.
- MockMvc 테스트로 인증서 없음, 정상 인증서, 만료 인증서, health endpoint 예외를 검증했다.

## 2026-06-04 배포 환경 분리 guardrail
- `DeploymentProfileGuardrailTest`를 추가해 로컬·운영 profile 분리 계약을 CI에서 검증한다.
- `application-local.yml`은 gitignore 대상으로 유지하고, `application-local.example.yml`만 커밋한다.
- `application-prod.yml`은 실제 운영 profile 파일로 커밋하되 필수 secret은 기본값 없는 환경변수 placeholder로만 둔다.
- `compose.prod.yml`은 `prod` profile, 외부 `application-prod.env`, read-only `application-prod.yml` mount만 사용하도록 고정한다.
- GitHub Actions 배포 job은 `main` push, production environment, GHCR image push/pull 흐름을 유지하도록 검증한다.
- 배포 script는 원격 서버의 `application-prod.env`, `deploy-prod.env`, `compose.prod.yml`만 사용하고 local profile 파일을 참조하지 않는다.

## 현재 구현 로직
- 종목 마스터는 `stock_master` DB 테이블을 기준으로 조회하고, seed loader는 빈 테이블에만 기본 universe를 적재한다.
- 시장 데이터는 KIS 실시간 체결 cache, KIS 현재가 REST, 공공데이터 주식시세 snapshot, fallback 데이터 순서로 표준 응답 구조를 유지한다.
- 주문 가능 여부 boundary는 `/api/v1/market/stocks/{stockCode}/orderability`에서 공동 응답 envelope으로 제공하며, BUY 요청은 KRX 외국인보유량 cache와 요청 수량으로 예상 한도소진율을 계산해 100% 이상이면 `FOREIGN_LIMIT_EXCEEDED`로 차단한다.
- KIS 실시간 체결 cache가 있으면 1호가 공백 패턴으로 `priceLimitState=UPPER_LIMIT|LOWER_LIMIT|NORMAL`을 판단해 orderability 응답에 반영한다. VI와 거래정지는 KIS 전용 상태 필드 파싱 전까지 false로 유지한다.
- KRX KOSPI/KOSDAQ/KONEX 일별매매정보는 `market_daily_price`에 OHLCV, 거래량, 거래대금, 조정종가 기준으로 정규화 저장한다.
- 과거 시세는 `/api/v1/market/stocks/{stockCode}/history`에서 공동 응답 envelope으로 조회하고, 운영 수집은 `/api/v1/market/history/collect` 또는 scheduler로 실행한다.
- 호가 응답은 KIS 실시간 호가 cache를 우선 사용하고, 없으면 mock 호가 snapshot으로 응답 구조를 유지한다.
- 외국인 보유수량, 외국인 지분율, 한도소진율은 KRX 외국인보유량 snapshot을 우선 사용하고 장애 시 캐시 또는 fallback 데이터로 응답 구조를 유지한다.
- 외국인 보유율 cache는 Redis TTL 저장소를 기본으로 사용하고 Redis 장애 시 in-memory fallback으로 전환한다.
- 현지 통화 환산가는 quote 요청의 `fxRate`, 한국수출입은행 또는 협력사 입력 환율 캐시, `1` fallback 순서로 선택한 환율에 `currentPriceKrw`를 곱해 계산한다.
- 환율 cache는 Redis TTL 저장소를 기본으로 사용하고 Redis 장애 시 in-memory fallback으로 전환한다.
- validation 실패 응답은 `400 Bad Request`와 ProblemDetail body로 통일한다.
- 알림 이벤트는 `/api/v1/alerts/events`로 수신한 뒤 `/topic/partners/{partnerId}/alerts`, `/topic/stocks/{stockCode}/alerts`로 전송한다.
- WebSocket endpoint `/ws/alerts` handshake도 운영 API key 검증 대상이다.
- 알림 분석 발행 endpoint는 AI 분석 결과를 받아 기존 알림 이벤트 송신 로직을 재사용한다.
- 알림 수집 발행 endpoint는 Naver 뉴스와 OpenDART 공시를 수집한 뒤 AI 분석과 WebSocket 발행을 순차 수행한다.
- 알림 스케줄러는 설정된 협력사 watchlist별로 수집 발행 흐름을 주기 실행한다.
- provider 수집 기반 알림은 Redis TTL dedupe로 같은 원문 URL의 반복 발행을 줄인다.
- Naver News 응답의 HTML 태그와 entity를 정규화해 제목과 snippet으로 변환한다.
- OpenDART 공시검색 응답의 접수번호로 원문 공시 URL을 생성한다.
- KIS 현재가 응답은 `KisCurrentPriceSnapshot`으로 변환한다.
- 한국수출입은행 환율 refresh scheduler는 설정된 통화의 `KRW -> 현지통화` 환율을 `ExchangeRateCache`에 주기 저장한다.
- 공공데이터 주식시세 응답은 첫 번째 종목 항목을 `PublicDataStockPriceSnapshot`으로 변환한다.
- Hannah-Montana-AI 분석 응답은 알림 이벤트 생성 단계에서 사용할 표준 분석 결과 DTO로 수신한다.
- 외부 provider 호출은 공통 timeout, retry, circuit breaker 정책을 통과한다.
- API 계약은 `/openapi.yaml`에서 OpenAPI 3.1 문서로 제공한다.
- 인증된 운영 API는 API key fingerprint별 rate limit을 적용한다.
- 운영 요청 서명은 HMAC-SHA256, timestamp clock skew, nonce replay 방어를 적용할 수 있다.
- 운영 요청 서명 nonce는 Redis에 공유 저장해 다중 인스턴스에서도 replay를 방지한다.
- mTLS 검증을 켜면 health/info를 제외한 보호 API 요청에 client certificate가 있어야 한다.
- 모든 요청은 correlation id로 추적 가능하고, 보안 인증 이벤트는 API key 원문 없이 감사 로그로 기록한다.

## 외부 연동 예정
- 협력사 watchlist를 DB에서 관리하는 저장소를 추가한다.
