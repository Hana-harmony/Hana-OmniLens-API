# 배포

## 로컬
- 로컬 설정은 `src/main/resources/application-local.yml`을 사용한다.
- 이 파일은 gitignore 대상이다.
- 현재 워크스페이스에는 실제 로컬 파일을 생성해 둔다.
- 새 환경에서 파일이 없으면 예시 파일을 복사해 개인 로컬 값으로 수정한다.
- Docker Compose 로컬 실행은 이 파일을 컨테이너에 read-only mount하고, 이미지는 민감값 없이 빌드한다.

```bash
docker compose -f compose.local.yml up -d
./gradlew bootRun
```

## 운영
- OmniLens FE 운영 주소는 `https://hanaomni.cloud`, API 운영 주소는 `https://api.hanaomni.cloud`이다.
- `main` push 시 GitHub Actions의 `deploy-prod` job이 실행된다.
- CI는 ARM64 API 이미지를 빌드해 GHCR에 push한다.
- OCI `VM.Standard.A1.Flex` 한 대에서 PostgreSQL, Redis, OmniLens API를 컨테이너로 운영한다.
- PostgreSQL과 Redis는 `hana-omnilens-internal` 네트워크에서만 접근하며 호스트 포트를 공개하지 않는다.
- PostgreSQL과 Redis 데이터는 Docker named volume에 보존한다.
- PostgreSQL은 SCRAM-SHA-256과 data checksum을 사용하고 Redis는 ACL, AOF, `noeviction` 정책을 사용한다.
- 데이터 서비스가 healthy 상태가 된 뒤 API 단일 컨테이너를 교체한다. 새 이미지가 준비되지 않으면 직전 이미지를 자동 복구한다.
- Nginx와 Certbot은 Ubuntu 호스트의 systemd 서비스로 운영하고 애플리케이션 컨테이너와 수명주기를 분리한다.
- 필수 secret이 없으면 운영 secret 확인 단계에서 실패 닫힘 처리한다.
- PostgreSQL 논리 백업은 systemd timer로 매일 실행하고 14일간 로컬 보존한다. OCI 볼륨 백업 정책은 별도로 적용한다.

## 필요한 GitHub Secrets
- `PROD_HOST`: 운영 서버 호스트
- `PROD_USER`: SSH 사용자
- `PROD_SSH_KEY`: 운영 서버 접근용 private key
- `PROD_HOST_KEY`: 운영 서버 SSH host key
- `GHCR_TOKEN`: 운영 서버에서 GHCR 이미지 pull에 사용할 token. 배포 workflow가 GitHub API로 token 소유자를 검증해 GHCR 사용자명을 자동 결정하므로 `GHCR_USERNAME` Secret은 등록하지 않는다.
- `OMNILENS_API_KEY_SHA256`: 협력사 API key SHA-256 해시
- `OMNILENS_PORTAL_BOOTSTRAP_ADMIN_PASSWORD`: 신규 DB의 초기 관리자 임시 비밀번호, 16~128자이며 앞뒤 공백을 허용하지 않음
- `DB_PASSWORD`: 운영 DB 비밀번호
- `REDIS_PASSWORD`: 운영 Redis 비밀번호
- `PUBLIC_DATA_SERVICE_KEY`: 공공데이터포털 API 인증키
- `KIS_APP_KEY`: KIS 현재가, 일봉 보강, REST 호가 호출용 App Key
- `KIS_APP_SECRET`: KIS 현재가, 일봉 보강, REST 호가 호출용 App Secret
- `KRX_OPEN_API_AUTH_KEY`: KRX 일별매매정보 수집용 인증키
- `NAVER_NEWS_CLIENT_ID`: Naver News Search API Client ID
- `NAVER_NEWS_CLIENT_SECRET`: Naver News Search API Client Secret
- `OPEN_DART_API_KEY`: OpenDART API 인증키

## 호스트 자동 생성 운영키

다음 값은 GitHub Secrets로 등록하지 않는다.

- `OMNILENS_PORTAL_SESSION_SIGNING_KEY`
- `OMNILENS_PORTAL_API_KEY_ENCRYPTION_KEY`
- `OMNILENS_TERM_ANALYTICS_HASH_SALT`
- `HANNAH_AI_MAINTENANCE_TOKEN`

OCI 최초 배포에서 `/opt/hana-runtime/root-secret`을 256-bit로 생성하고 용도별 HMAC 라벨로 서로 다른 값을 파생한다. Hana-OmniLens-API와 Hannah-Montana-AI는 같은 OCI 호스트 루트와 `hana/ai/maintenance-auth/v1` 라벨을 사용하므로 별도 공유 Secret 없이 동일한 유지보수 토큰을 얻는다. 루트 파일은 재배포 때 덮어쓰지 않고 `600` 권한으로 유지한다. 루트키가 유실되면 기존 포털 API 키 암호문을 복호화할 수 없으므로 OCI 암호화 볼륨 백업에 반드시 포함한다.

DB와 Redis 연결 정보는 배포 계약으로 고정한다.

```text
DB_URL=jdbc:postgresql://hana-omnilens-postgres:5432/omnilens
DB_USERNAME=omnilens_app
REDIS_HOST=hana-omnilens-redis
REDIS_PORT=6379
REDIS_USERNAME=omnilens_app
```

## 선택 운영 변수
- `SERVER_SSL_ENABLED`: Spring Boot TLS 활성화 여부. mTLS 사용 시 `true`로 설정한다.
- `SERVER_SSL_CLIENT_AUTH`: client certificate 요청 모드. healthcheck 유지를 위해 mTLS 사용 시 `want`를 권장한다.
- `SERVER_SSL_KEY_STORE`: 컨테이너 내부 서버 TLS keystore 경로. 기본값은 `/app/config/tls/server-keystore.p12`이다.
- `SERVER_SSL_KEY_STORE_PASSWORD`: 서버 TLS keystore 비밀번호.
- `SERVER_SSL_KEY_STORE_TYPE`: 서버 TLS keystore 타입. 기본값은 `PKCS12`이다.
- `SERVER_SSL_TRUST_STORE`: 컨테이너 내부 client CA truststore 경로. 기본값은 `/app/config/tls/client-truststore.p12`이다.
- `SERVER_SSL_TRUST_STORE_PASSWORD`: client CA truststore 비밀번호.
- `SERVER_SSL_TRUST_STORE_TYPE`: client CA truststore 타입. 기본값은 `PKCS12`이다.
- `SERVER_SSL_KEY_STORE_BASE64`: 서버 TLS keystore 파일을 base64 인코딩한 값. CI/CD가 원격 서버 `tls/` 디렉터리에 자동 생성한다.
- `SERVER_SSL_TRUST_STORE_BASE64`: 협력사 client certificate CA truststore 파일을 base64 인코딩한 값. CI/CD가 원격 서버 `tls/` 디렉터리에 자동 생성한다.
- `HEALTHCHECK_SCHEME`: 컨테이너 healthcheck scheme. TLS 활성화 시 `https`로 설정한다.
- `HANNAH_AI_BASE_URL`: Hannah-Montana-AI 내부 서비스 주소. 기본값은 `http://hannah-montana-ai:8000`이다.
- `HANNAH_AI_CONNECT_TIMEOUT`: Hannah-Montana-AI 내부 호출 connect timeout. 기본값은 `2s`이다.
- `HANNAH_AI_READ_TIMEOUT`: Hannah-Montana-AI 분석·번역·글로벌 피어·금융용어 호출 read timeout. 기본값은 `90s`이며 로컬/sidecar Qwen3 생성 시간이 외부 provider 공통 timeout보다 길 수 있어 별도로 관리한다.
- `OMNILENS_TERM_ANALYTICS_HASH_SALT`: 한국 금융 용어 클릭 로그의 사용자/세션 식별자 salted hash에 사용하며 운영에서는 호스트 루트키에서 자동 파생한다.
- `KRX_OPEN_API_BASE_URL`: KRX Open API 실제 호출 endpoint 주소. 기본값은 `https://data-dbg.krx.co.kr`이다.
- `KRX_OPEN_API_AUTH_KEY`: KRX Open API `AUTH_KEY` 헤더로 전달하는 인증키다.
- `KRX_SCRAPING_ENABLED`: KRX Data Marketplace 로그인 기반 외국인 보유량 백필 활성화 여부. 기본값은 `true`이다.
- `KRX_ID`: KRX Data Marketplace 로그인 ID. GitHub Secrets 또는 서버 env로만 주입한다.
- `KRX_PW`: KRX Data Marketplace 로그인 비밀번호. GitHub Secrets 또는 서버 env로만 주입한다.
- `MARKET_HISTORY_COLLECTION_PROVIDER`: 일봉 수집 provider. `KRX_OPEN_API_WITH_KIS_BACKUP`, `KRX_OPEN_API`, `KIS_DAILY_CHART` 중 하나이며, KRX egress가 막힌 환경은 `KIS_DAILY_CHART`를 사용한다.
- `MARKET_HISTORY_COLLECTION_ENABLED`: KRX 과거 일별 시세 수집 scheduler 활성화 여부. 기본값은 `false`이다.
- `MARKET_HISTORY_COLLECTION_FIXED_DELAY_MS`: KRX 과거 시세 수집 scheduler 주기. 기본값은 `86400000`이다.
- `MARKET_HISTORY_COLLECTION_BASE_DATE_OFFSET_DAYS`: KRX 과거 시세 수집 기준일 offset. 기본값은 `1`이다.
- `FOREIGN_OWNERSHIP_REFRESH_ENABLED`: 외국인 보유량 전일 snapshot과 누락 백필 scheduler 활성화 여부. 운영 기본값은 `true`이다.
- `FOREIGN_OWNERSHIP_REFRESH_CRON`: 외국인 보유량 scheduler cron. 기본값은 평일 장전 `0 10 8 * * MON-FRI`이다.
- `FOREIGN_OWNERSHIP_REFRESH_BACKFILL_LOOKBACK_DAYS`: 누락 백필 조회 기간. 기본값은 초기 1년+ 보강을 위한 `400`이다.
- `FOREIGN_OWNERSHIP_REFRESH_STOCK_LIMIT`: 수집 종목 최대 개수. 기본값은 전체 국내주식 수용 우선의 `5000`이며 provider quota 제약 시 `30` 등으로 낮춘다.
- `FOREIGN_OWNERSHIP_MODEL_TRAINING_ENABLED`: 제한 종목 외국인 보유 ML 재학습 기능 활성화 여부. 기본값은 `true`이다.
- `FOREIGN_OWNERSHIP_MODEL_TRAINING_TRIGGER_AFTER_REFRESH`: 전일/누락 KRX 외국인 보유 row 저장 후 Hannah 재학습을 자동 호출할지 여부. 기본값은 `true`이다.
- `FOREIGN_OWNERSHIP_MODEL_TRAINING_READ_TIMEOUT`: Hannah 재학습 호출 read timeout. 기본값은 `20m`이다.
- `FOREIGN_OWNERSHIP_PREDICTION_PRECOMPUTE_ENABLED`: 제한 종목 금일 외국인 보유 예측 선계산 활성화 여부. 기본값은 `true`이다.
- `FOREIGN_OWNERSHIP_PREDICTION_PRECOMPUTE_TRIGGER_AFTER_REFRESH`: KRX 수집과 재학습 이후 예측 cache를 자동 갱신할지 여부. 기본값은 `true`이다.
- `FOREIGN_OWNERSHIP_PREDICTION_PRECOMPUTE_STOCK_LIMIT`: 예측 선계산 대상 최대 종목 수. 기본값은 `5000`이며 실제 대상은 외국인 취득한도 제한 allowlist로 제한된다.
- `HANNAH_AI_MAINTENANCE_TOKEN`: Hannah 재학습 endpoint 보호용 내부 토큰이며 OmniLens API와 AI가 같은 OCI 호스트 루트키에서 자동 파생한다.
- `KIS_BASE_URL`: KIS 모의투자 REST endpoint 주소. 기본값은 `https://openapivts.koreainvestment.com:29443`이다.
- `KIS_WEBSOCKET_URL`: KIS 모의투자 WebSocket endpoint 주소. 기본값은 `ws://ops.koreainvestment.com:31000`이다.
- `KIS_REALTIME_STOCK_CODES`: 기동 시 해제하지 않는 인기 10종목 코드. 기본값은 로컬·운영 설정의 10종목이다.
- `KIS_REALTIME_INDEX_CODES`: 기동 시 고정 구독하는 KOSPI·KOSDAQ·KOSPI200 코드. 기본값은 `0001,1001,2001`이다.
- `KIS_ACCOUNT_NUMBER`, `KIS_APP_KEY`, `KIS_APP_SECRET`: 일반 종목 REST·WebSocket용 모의투자 자격증명이다. 로컬 Compose에서는 대응하는 `OMNILENS_PROVIDERS_KIS_*` 변수와 모의 endpoint를 사용한다.
- `KIS_REAL_ACCOUNT_NUMBER`, `KIS_REAL_APP_KEY`, `KIS_REAL_APP_SECRET`: 실전 지수 WebSocket 전용 자격증명이다. 로컬 Compose에서는 대응하는 `OMNILENS_PROVIDERS_REAL_KIS_*` 변수와 실전 endpoint를 사용한다. 두 계정의 자격증명과 endpoint를 교차 사용하지 않는다.
- `KIS_REALTIME_SHARD_SIZE`: KIS 연결 하나에서 허용할 주식 체결·호가 등록 TR 상한. provider 한도 보호를 위해 최대 40으로 제한된다.
- `KIS_REALTIME_ORDER_BOOK_ENABLED`: `true`이면 종목마다 체결과 호가가 각각 1 TR을 사용한다. 고정 universe 이후 남은 슬롯은 상세 종목 LRU로 사용한다.
- `FRANKFURTER_BASE_URL`: Frankfurter 환율 endpoint 주소. 기본값은 `https://api.frankfurter.dev`이다.
- `HANNAH_AI_BASE_URL`: Hannah-Montana-AI 내부 endpoint 주소. 번역, 분석, 글로벌 피어, 금융용어 해설을 같은 내부망에서 호출한다.
- `HANNAH_AI_CONNECT_TIMEOUT`, `HANNAH_AI_READ_TIMEOUT`: Hannah-Montana-AI 내부 endpoint 전용 timeout이다. `PROVIDER_READ_TIMEOUT`은 KIS, Naver, KRX 같은 외부 provider에만 적용한다.
- `OMNILENS_RATE_LIMIT_ENABLED`: API key fingerprint 단위 rate limit 활성화 여부. 기본값은 `true`이다.
- `OMNILENS_RATE_LIMIT_CAPACITY`: bucket 최대 요청 수. 기본값은 `120`이다.
- `OMNILENS_RATE_LIMIT_REFILL_TOKENS`: refill마다 복구되는 요청 수. 기본값은 `120`이다.
- `OMNILENS_RATE_LIMIT_REFILL_PERIOD`: refill 주기. 기본값은 `1m`이다.
- `OMNILENS_RATE_LIMIT_MAX_BUCKETS`: 메모리에 보관할 최대 API key bucket 수. 기본값은 `10000`이다.
- `OMNILENS_SECURITY_SIGNATURE_ENABLED`: HMAC 요청 서명 검증 활성화 여부. 애플리케이션과 운영 기본값은 `true`이며 자동 테스트 외에는 끄지 않는다.
- HMAC secret은 협력사 API key 원문을 사용한다. 별도의 `OMNILENS_SIGNATURE_SECRET`은 사용하지 않는다.
- `OMNILENS_SECURITY_SIGNATURE_ALLOWED_CLOCK_SKEW`: 서명 timestamp 허용 오차. 기본값은 `2m`이다.
- `OMNILENS_SECURITY_SIGNATURE_NONCE_STORE_MODE`: 서명 nonce 저장소. 애플리케이션과 운영 기본값은 `redis`, 격리된 자동 테스트는 `in-memory`를 사용할 수 있다.
- `OMNILENS_SECURITY_SIGNATURE_MAX_NONCES`: 인메모리 nonce 저장 최대 개수. 기본값은 `10000`이다.
- `OMNILENS_MTLS_ENABLED`: 보호 API client certificate 필수 검증 활성화 여부. 기본값은 `false`이다.
- `ALERT_DEDUPE_MODE`: 알림 중복 방지 저장소 모드. 기본값은 `redis`이다.
- `ALERT_DEDUPE_TTL`: Redis dedupe key 보존 시간. 기본값은 `24h`이다.
- `ALERT_DEDUPE_MAX_IN_MEMORY_ENTRIES`: Redis 장애 fallback용 in-memory 최대 key 수. 기본값은 `10000`이다.
- `ALERT_SCHEDULER_ENABLED`: 협력사 watchlist와 기본 종목 universe 주기 수집 활성화 여부. 기본값은 `true`이다.
- `ALERT_SCHEDULER_FIXED_DELAY_MS`: 주기 수집 간격. 기본값은 `300000`이다.
- `ALERT_SCHEDULER_NEWS_DISPLAY`: 종목별 뉴스와 공시의 최신 수집 목표 개수. 기본값은 `5`이다.
- `ALERT_SCHEDULER_DISCLOSURE_LOOKBACK_DAYS`: 공시 조회 기간. 기본값은 `365`이다.
- `ALERT_SCHEDULER_DEFAULT_UNIVERSE_ENABLED`: `stock_master_priority` 인기 종목과 외국인 취득한도 제한 종목 자동 수집 활성화 여부. 기본값은 `true`이다.
- `ALERT_SCHEDULER_DEFAULT_UNIVERSE_PARTNER_ID`: 기본 universe 이벤트의 partner id. 기본값은 `omnilens-default-universe`이다.
- `ALERT_SCHEDULER_PRIORITY_STOCK_LIMIT`: 기본 universe에 포함할 인기 종목 수. 기본값은 `10`이다.
- `ALERT_SCHEDULER_INCLUDE_FOREIGN_OWNERSHIP_RESTRICTED_STOCKS`: 외국인 취득한도 제한 종목 allowlist 포함 여부. 기본값은 `true`이다.
- `ALERT_SCHEDULER_COLLECTION_BATCH_SIZE`: 종목별 뉴스·공시 수집 배치 크기. 기본값과 상한은 `20`이다.
- `EXCHANGE_RATE_REFRESH_ENABLED`: FX 환율 주기 갱신 활성화 여부. 기본값은 `false`이다.
- `EXCHANGE_RATE_REFRESH_FIXED_DELAY_MS`: 환율 갱신 간격. 기본값은 `300000`이다.
- `EXCHANGE_RATE_REFRESH_BASE_DATE_OFFSET_DAYS`: fallback provider가 기준일을 요구할 때 사용할 조회 기준일 offset. 기본값은 `0`이다.
- `EXCHANGE_RATE_REFRESH_CURRENCIES`: 갱신할 통화 목록. 예: `USD,JPY`.
- `EXCHANGE_RATE_CACHE_MODE`: 환율 cache 저장소. 기본값은 `redis`이다.
- `EXCHANGE_RATE_CACHE_TTL`: Redis 환율 cache TTL. 기본값은 `24h`이다.
- `STOCK_MASTER_SEED_ENABLED`: 종목 마스터 초기 적재 활성화 여부. 기본값은 `true`이다.
- `STOCK_MASTER_SEED_LOCATION`: 종목 마스터 seed CSV 위치. 기본값은 `classpath:data/stock-master-seed.csv`이다.
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

mTLS를 사용할 때는 아래 조합을 권장한다.

```text
SERVER_SSL_ENABLED=true
SERVER_SSL_CLIENT_AUTH=want
OMNILENS_MTLS_ENABLED=true
HEALTHCHECK_SCHEME=https
```

`SERVER_SSL_CLIENT_AUTH=want`는 healthcheck가 client certificate 없이 통과할 수 있게 두고, 보호 API는 앱 필터가 client certificate 존재와 유효 기간을 다시 검증한다.

## 외부 연동 Secrets
아래 값은 GitHub Secrets 또는 배포 환경 Secret Manager에만 둔다.

- `KIS_ACCOUNT_NUMBER`: KIS 계좌번호
- `KIS_APP_KEY`, `KIS_APP_SECRET`: KIS Open API credential
- `NAVER_NEWS_CLIENT_ID`, `NAVER_NEWS_CLIENT_SECRET`: Naver News Search API credential
- `OPEN_DART_API_KEY`: OpenDART API credential
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
