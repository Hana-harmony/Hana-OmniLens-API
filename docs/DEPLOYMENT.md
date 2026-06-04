# 배포

## 로컬
- 로컬 설정은 `src/main/resources/application-local.yml`을 사용한다.
- 이 파일은 gitignore 대상이다.
- 현재 워크스페이스에는 실제 로컬 파일을 생성해 둔다.
- 새 환경에서 파일이 없으면 예시 파일을 복사해 개인 로컬 값으로 수정한다.

```bash
docker compose -f compose.local.yml up -d
./gradlew bootRun
```

## 운영
- `main` push 시 GitHub Actions의 `deploy-prod` job이 실행된다.
- CI는 Docker 이미지를 빌드해 GHCR에 push한다.
- 원격 서버에는 `application-prod.yml`, `application-prod.env`, `deploy-prod.env`, `compose.prod.yml`, `deploy.sh`를 전송한다.
- 원격 서버의 `deploy.sh`가 GHCR에서 이미지를 pull하고 Docker Compose로 `prod` profile 컨테이너를 재시작한다.

## 필요한 GitHub Secrets
- `PROD_HOST`: 운영 서버 호스트
- `PROD_USER`: SSH 사용자
- `PROD_SSH_KEY`: 운영 서버 접근용 private key
- `GHCR_TOKEN`: 운영 서버에서 GHCR 이미지 pull에 사용할 token
- `SERVER_PORT`: 운영 서버 포트
- `OMNILENS_API_KEY_SHA256`: 협력사 API key SHA-256 해시
- `OMNILENS_CORS_ALLOWED_ORIGINS`: 허용 origin 목록
- `DB_URL`: 운영 DB JDBC URL
- `DB_USERNAME`: 운영 DB 사용자
- `DB_PASSWORD`: 운영 DB 비밀번호
- `REDIS_HOST`: 운영 Redis 호스트
- `REDIS_PORT`: 운영 Redis 포트
- `REDIS_PASSWORD`: 운영 Redis 비밀번호
- `PUBLIC_DATA_SERVICE_KEY`: 공공데이터포털 API 인증키
- `NAVER_NEWS_CLIENT_ID`: Naver News Search API Client ID
- `NAVER_NEWS_CLIENT_SECRET`: Naver News Search API Client Secret
- `OPEN_DART_API_KEY`: OpenDART API 인증키
- `KOREA_EXIM_AUTH_KEY`: 한국수출입은행 환율 API 인증키

## 선택 운영 변수
- `HANNAH_AI_BASE_URL`: Hannah-Montana-AI 내부 서비스 주소. 기본값은 `http://hannah-montana-ai:8000`이다.
- `KRX_BASE_URL`: KRX 데이터 endpoint 주소. 기본값은 `https://data.krx.co.kr`이다.
- `KOREA_EXIM_BASE_URL`: 한국수출입은행 환율 endpoint 주소. 기본값은 `https://oapi.koreaexim.go.kr`이다.
- `PAPAGO_TRANSLATION_CLIENT_ID`: Papago NMT API Client ID. 없으면 번역 실패 시 원문 제목으로 fallback한다.
- `PAPAGO_TRANSLATION_CLIENT_SECRET`: Papago NMT API Client Secret.
- `OMNILENS_RATE_LIMIT_ENABLED`: API key fingerprint 단위 rate limit 활성화 여부. 기본값은 `true`이다.
- `OMNILENS_RATE_LIMIT_CAPACITY`: bucket 최대 요청 수. 기본값은 `120`이다.
- `OMNILENS_RATE_LIMIT_REFILL_TOKENS`: refill마다 복구되는 요청 수. 기본값은 `120`이다.
- `OMNILENS_RATE_LIMIT_REFILL_PERIOD`: refill 주기. 기본값은 `1m`이다.
- `OMNILENS_RATE_LIMIT_MAX_BUCKETS`: 메모리에 보관할 최대 API key bucket 수. 기본값은 `10000`이다.
- `OMNILENS_SIGNATURE_ENABLED`: HMAC 요청 서명 검증 활성화 여부. 기본값은 `false`이다.
- `OMNILENS_SIGNATURE_SECRET`: HMAC 요청 서명 검증 secret. 서명 검증 활성화 시 필수다.
- `OMNILENS_SIGNATURE_ALLOWED_CLOCK_SKEW`: 서명 timestamp 허용 오차. 기본값은 `5m`이다.
- `OMNILENS_SIGNATURE_NONCE_STORE_MODE`: 서명 nonce 저장소. 운영 기본값은 `redis`, 로컬 테스트는 `in-memory`를 사용할 수 있다.
- `OMNILENS_SIGNATURE_MAX_NONCES`: 인메모리 nonce 저장 최대 개수. 기본값은 `10000`이다.
- `ALERT_DEDUPE_MODE`: 알림 중복 방지 저장소 모드. 기본값은 `redis`이다.
- `ALERT_DEDUPE_TTL`: Redis dedupe key 보존 시간. 기본값은 `24h`이다.
- `ALERT_DEDUPE_MAX_IN_MEMORY_ENTRIES`: Redis 장애 fallback용 in-memory 최대 key 수. 기본값은 `10000`이다.
- `ALERT_SCHEDULER_ENABLED`: 협력사 watchlist 주기 수집 활성화 여부. 기본값은 `false`이다.
- `ALERT_SCHEDULER_FIXED_DELAY_MS`: 주기 수집 간격. 기본값은 `300000`이다.
- `ALERT_SCHEDULER_NEWS_DISPLAY`: 종목별 뉴스 수집 개수. 기본값은 `10`이다.
- `ALERT_SCHEDULER_DISCLOSURE_LOOKBACK_DAYS`: 공시 조회 기간. 기본값은 `7`이다.
- `EXCHANGE_RATE_REFRESH_ENABLED`: 한국수출입은행 환율 주기 갱신 활성화 여부. 기본값은 `false`이다.
- `EXCHANGE_RATE_REFRESH_FIXED_DELAY_MS`: 환율 갱신 간격. 기본값은 `300000`이다.
- `EXCHANGE_RATE_REFRESH_BASE_DATE_OFFSET_DAYS`: 환율 조회 기준일 offset. 기본값은 `0`이다.
- `EXCHANGE_RATE_REFRESH_CURRENCIES`: 갱신할 통화 목록. 예: `USD,JPY`.
- `EXCHANGE_RATE_CACHE_MODE`: 환율 cache 저장소. 기본값은 `redis`이다.
- `EXCHANGE_RATE_CACHE_TTL`: Redis 환율 cache TTL. 기본값은 `24h`이다.
- `PROVIDER_CONNECT_TIMEOUT`: 외부 provider connect timeout. 기본값은 `2s`이다.
- `PROVIDER_READ_TIMEOUT`: 외부 provider read timeout. 기본값은 `5s`이다.
- `PROVIDER_RETRY_ENABLED`: 외부 provider 네트워크 재시도 활성화 여부. 기본값은 `true`이다.
- `PROVIDER_RETRY_MAX_ATTEMPTS`: 외부 provider 최대 시도 횟수. 기본값은 `2`이다.
- `PROVIDER_RETRY_BACKOFF`: 외부 provider 재시도 대기 시간. 기본값은 `150ms`이다.
- `PROVIDER_CIRCUIT_BREAKER_ENABLED`: 외부 provider circuit breaker 활성화 여부. 기본값은 `true`이다.
- `PROVIDER_CIRCUIT_BREAKER_FAILURE_THRESHOLD`: circuit open 전 연속 실패 임계값. 기본값은 `5`이다.
- `PROVIDER_CIRCUIT_BREAKER_OPEN_DURATION`: circuit open 유지 시간. 기본값은 `30s`이다.

watchlist는 Spring indexed env로 주입할 수 있다.

```text
OMNILENS_ALERT_SCHEDULER_WATCHLISTS_0_PARTNER_ID=partner-a
OMNILENS_ALERT_SCHEDULER_WATCHLISTS_0_STOCK_CODES_0=005930
OMNILENS_ALERT_SCHEDULER_WATCHLISTS_0_STOCK_CODES_1=000660
```

## 원격 서버 준비
원격 서버에는 아래 런타임이 미리 설치되어 있어야 한다.

```text
Docker Engine
Docker Compose plugin
```

배포 파일과 `/opt/hana-omnilens-api` 디렉터리는 CI/CD가 생성한다.

## 운영 실행 흐름
```bash
docker login ghcr.io
docker compose --env-file application-prod.env -f compose.prod.yml pull api
docker compose --env-file application-prod.env -f compose.prod.yml up -d api
```
