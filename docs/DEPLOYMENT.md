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

## 선택 운영 변수
- `HANNAH_AI_BASE_URL`: Hannah-Montana-AI 내부 서비스 주소. 기본값은 `http://hannah-montana-ai:8000`이다.
- `KRX_BASE_URL`: KRX 데이터 endpoint 주소. 기본값은 `https://data.krx.co.kr`이다.
- `ALERT_DEDUPE_MODE`: 알림 중복 방지 저장소 모드. 기본값은 `redis`이다.
- `ALERT_DEDUPE_TTL`: Redis dedupe key 보존 시간. 기본값은 `24h`이다.
- `ALERT_DEDUPE_MAX_IN_MEMORY_ENTRIES`: Redis 장애 fallback용 in-memory 최대 key 수. 기본값은 `10000`이다.
- `ALERT_SCHEDULER_ENABLED`: 협력사 watchlist 주기 수집 활성화 여부. 기본값은 `false`이다.
- `ALERT_SCHEDULER_FIXED_DELAY_MS`: 주기 수집 간격. 기본값은 `300000`이다.
- `ALERT_SCHEDULER_NEWS_DISPLAY`: 종목별 뉴스 수집 개수. 기본값은 `10`이다.
- `ALERT_SCHEDULER_DISCLOSURE_LOOKBACK_DAYS`: 공시 조회 기간. 기본값은 `7`이다.

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
