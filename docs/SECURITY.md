# 보안

## 현재 기준
- 모든 운영 API 요청은 `X-HANA-OMNILENS-API-KEY`를 사용한다.
- 서버는 API key 원문을 저장하지 않고 SHA-256 해시만 비교한다.
- 단일 운영 bootstrap 키는 `application-prod.yml`의 `${OMNILENS_API_KEY_SHA256}` placeholder로 주입한다.
- 협력사별 운영 키는 `partner_api_credential` 테이블에 SHA-256 해시와 `partner_id`로 저장한다.
- API key 해시가 설정 파일과 DB 어디에도 없으면 운영 API는 실패 닫힘 방식으로 `503`을 반환한다.
- DB credential로 인증된 요청은 알림 API의 요청 `partnerId`가 인증 `partnerId`와 다르면 `403`으로 거부한다.
- 인증된 요청은 API key SHA-256 fingerprint 단위로 rate limit을 적용한다.
- rate limit 초과 요청은 `429 Too Many Requests`와 `Retry-After` 헤더를 반환한다.
- 운영에서 `OMNILENS_SIGNATURE_ENABLED=true`인 경우 모든 보호 API 요청은 HMAC-SHA256 요청 서명을 함께 검증한다.
- 요청 서명 canonical string은 `METHOD`, `URI_WITH_QUERY`, `TIMESTAMP`, `NONCE`, `SHA256_BODY_HEX`를 줄바꿈으로 연결한 값이다.
- 서명 헤더는 `X-HANA-OMNILENS-TIMESTAMP`, `X-HANA-OMNILENS-NONCE`, `X-HANA-OMNILENS-SIGNATURE`를 사용한다.
- timestamp는 허용 clock skew 안에 있어야 하고, nonce는 같은 API key fingerprint에서 한 번만 사용할 수 있다.
- 운영 기본 nonce 저장소는 Redis이며, Redis nonce store가 없거나 장애가 발생하면 서명 검증 요청은 실패 닫힘 방식으로 `503`을 반환한다.
- 운영에서 `OMNILENS_MTLS_ENABLED=true`인 경우 health/info를 제외한 보호 API 요청은 client certificate가 없으면 `401`로 거부한다.
- mTLS 직접 종료는 Spring Boot `server.ssl` 설정을 사용하고, 운영 healthcheck를 위해 `SERVER_SSL_CLIENT_AUTH=want`와 앱 필터 강제를 함께 사용한다.
- 모든 응답에는 `X-HANA-OMNILENS-CORRELATION-ID`를 포함하고, 같은 값은 로그 MDC `correlationId`에 저장한다.
- 보안 감사 로그는 인증 결과, method, path, API key hash prefix, 실패 사유만 기록한다.
- mTLS 실패 감사 로그는 `client_certificate_missing`, `client_certificate_invalid` 사유를 사용한다.
- CORS는 profile별 설정 파일의 허용 목록만 사용한다.
- WebSocket `/ws/alerts`, `/ws/market/quotes` handshake도 API key 검증 대상이다.
- DB credential로 인증된 WebSocket 세션은 `/topic/partners/{partnerId}/alerts`와 `/topic/partners/{partnerId}/stocks/{stockCode}/alerts`에서 자기 `partnerId`만 구독할 수 있다.
- DB credential 세션은 전역 `/topic/stocks/{stockCode}/alerts` 구독을 사용할 수 없다.
- 세션은 stateless로 유지한다.
- DeepL/KIS/KRX/Naver/OpenDART 등 외부 API credential은 환경 변수 또는 Secret Manager에서만 주입한다.

## 협력사 API key 운영
- 원문 API key는 발급 직후 협력사에게 한 번만 전달한다.
- 서버 DB에는 `SHA-256(apiKey)` 결과만 `partner_api_credential.api_key_sha256`에 저장한다.
- 키 폐기는 row 삭제보다 `active=false` 전환을 우선 사용해 감사 추적을 남긴다.
- bootstrap 전역 키는 운영 초기 credential 등록과 비상 복구용으로만 사용하고 상시 협력사 호출은 DB credential을 사용한다.
- 협력사별 key rotation은 bootstrap 운영 키로 `POST /api/v1/security/partners/{partnerId}/credentials/rotate`를 호출한다.
- rotation 응답의 `apiKey`는 새 원문 키가 노출되는 유일한 시점이다. 운영자는 즉시 협력사 Secret Manager에 저장하고 로그나 티켓 본문에 남기지 않는다.
- rotation 시 기존 활성 credential은 같은 트랜잭션에서 비활성화된다.

## 시크릿 관리
- `application-local.yml`은 커밋하지 않는다.
- `application-prod.yml`은 커밋하되 `${...}` 환경변수 placeholder만 사용한다.
- 로컬 시크릿은 `application-local.yml`에만 둔다.
- 외부 데이터 수집 credential인 `NAVER_NEWS_CLIENT_ID`, `NAVER_NEWS_CLIENT_SECRET`, `OPEN_DART_API_KEY`는 Hana-OmniLens-API에서만 사용한다.
- Hannah-Montana-AI와 Stock-exchange-* 레포에는 Naver/OpenDART/KRX credential을 두지 않는다.
- 운영 시크릿은 GitHub Secrets로 주입하고 원격 서버의 `application-prod.env`에만 생성한다.
- `application-prod.env`는 커밋하지 않는다.
- GHCR pull token은 원격 서버의 `deploy-prod.env`에만 생성하고 앱 컨테이너에는 주입하지 않는다.
- `deploy-prod.env`는 커밋하지 않는다.

## 외부/운영 시크릿
- `KIS_APP_KEY`: KIS Open API app key
- `KIS_APP_SECRET`: KIS Open API app secret
- `KIS_ACCESS_TOKEN`: KIS Open API access token. 비워두면 앱이 자동 발급한다.
- `KIS_APPROVAL_KEY`: KIS WebSocket approval key. 비워두면 앱이 자동 발급한다.
- `PUBLIC_DATA_SERVICE_KEY`: 공공데이터포털 주식시세 계열 API 인증키
- `KRX_OPEN_API_AUTH_KEY`: KRX Open API 호출 시 `AUTH_KEY` 헤더로 전달하는 인증키
- `NAVER_NEWS_CLIENT_ID`: Naver News Search API Client ID
- `NAVER_NEWS_CLIENT_SECRET`: Naver News Search API Client Secret
- `OPEN_DART_API_KEY`: OpenDART 공시검색 API 인증키
- `DEEPL_API_KEY`: DeepL translation API key
- `OMNILENS_SIGNATURE_SECRET`: 협력사 요청 서명 검증용 HMAC secret
- `SERVER_SSL_KEY_STORE_PASSWORD`: 서버 TLS keystore 비밀번호
- `SERVER_SSL_TRUST_STORE_PASSWORD`: 협력사 client certificate CA truststore 비밀번호
- 외부 API 키는 로그, 예외 메시지, 테스트 fixture, 커밋 파일에 남기지 않는다.

## 내부 AI 통신
- Hannah-Montana-AI는 스프링 컨테이너 내부 네트워크에서만 접근 가능하게 배치한다.
- `HANNAH_AI_BASE_URL`은 주소 설정값이며 secret으로 분류하지 않는다.
- `KRX_OPEN_API_BASE_URL`은 KRX Open API 실제 호출 endpoint 주소 설정값이며 secret으로 분류하지 않는다.
- `KIS_BASE_URL`은 KIS endpoint 주소 설정값이며 secret으로 분류하지 않는다.
- `KIS_WEBSOCKET_URL`은 KIS WebSocket endpoint 주소 설정값이며 secret으로 분류하지 않는다.
- `FRANKFURTER_BASE_URL`은 Frankfurter 환율 endpoint 주소 설정값이며 secret으로 분류하지 않는다.
- `STOCK_MASTER_SEED_ENABLED`, `STOCK_MASTER_SEED_LOCATION`은 seed 동작 설정값이며 secret으로 분류하지 않는다.
- `PROVIDER_*TIMEOUT`, `PROVIDER_RETRY_*`, `PROVIDER_CIRCUIT_BREAKER_*`는 장애 대응 튜닝값이며 secret으로 분류하지 않는다.
- 별도 서비스 토큰 헤더는 사용하지 않는다.

## 향후 강화
- abuse detection
- 감사 로그 무결성 보장
- 외부 번역 공급자 전송 데이터 최소화와 개인정보 제거
