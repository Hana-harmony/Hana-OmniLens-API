# 운영

## 실행
```bash
docker compose -f compose.local.yml up -d
./gradlew bootRun
```

## 로컬 인프라
```bash
docker compose -f compose.local.yml up -d
docker compose -f compose.local.yml down
```

## 로컬 설정
- `src/main/resources/application-local.yml`은 gitignore 대상이다.
- 이 워크스페이스에는 로컬 DB, Redis, CORS, API key 설정을 담은 실제 `application-local.yml`을 둔다.
- 새 환경에서 파일이 없으면 `application-local.example.yml`을 복사해 만든다.
- 로컬에서는 환경 변수 없이 실행한다.

## 운영 설정
- `src/main/resources/application-prod.yml`은 커밋되는 실제 운영 profile 설정 파일이다.
- 민감값은 `${...}` 환경변수 placeholder로만 작성한다.
- KIS 현재가 provider는 모의투자 REST domain `https://openapivts.koreainvestment.com:29443`을 기본으로 사용한다.
- KIS 현재가 provider를 사용하려면 `KIS_APP_KEY`, `KIS_APP_SECRET`을 GitHub Secrets에 등록한다. `KIS_ACCESS_TOKEN`은 비워두면 앱이 자동 발급한다.
- KIS WebSocket provider는 모의투자 WebSocket domain `ws://ops.koreainvestment.com:31000`을 기본으로 사용한다.
- KIS WebSocket provider를 사용하려면 `KIS_WEBSOCKET_URL`을 설정할 수 있다. `KIS_APPROVAL_KEY`는 비워두면 앱이 자동 발급한다.
- 환율 provider는 `FRANKFURTER_BASE_URL` 하나만 사용한다. Frankfurter public API는 별도 API key가 필요 없다.
- `main` push 시 GitHub Secrets로 원격 서버의 `application-prod.env`를 생성한다.
- `main` push 시 Docker 이미지를 GHCR에 push한다.
- 원격 서버는 GHCR에서 이미지를 pull하고 `compose.prod.yml`로 컨테이너를 실행한다.
- `deploy-prod.env`는 GHCR pull용 값과 배포할 이미지 태그만 담고 앱 컨테이너에는 주입하지 않는다.

## 알림 주기 수집
- 기본값은 `omnilens.alert.scheduler.enabled=false`이다.
- 스케줄러를 켜면 설정 또는 DB에 저장된 협력사 watchlist마다 Naver 뉴스와 OpenDART 공시를 수집하고 Hannah-Montana-AI 분석 후 WebSocket으로 발행한다.
- 주기는 `ALERT_SCHEDULER_FIXED_DELAY_MS`로 조정한다. 기본값은 `300000`이다.
- 수집 범위는 `ALERT_SCHEDULER_NEWS_DISPLAY`, `ALERT_SCHEDULER_DISCLOSURE_LOOKBACK_DAYS`로 조정한다.
- 운영 중 watchlist는 `PUT /api/v1/alerts/watchlists/{partnerId}`로 DB에 저장한다.
- 설정 파일 watchlist는 부트스트랩 또는 비상 운영용으로 유지하며, DB watchlist와 협력사별로 병합된다.

```text
OMNILENS_ALERT_SCHEDULER_WATCHLISTS_0_PARTNER_ID=partner-a
OMNILENS_ALERT_SCHEDULER_WATCHLISTS_0_STOCK_CODES_0=005930
OMNILENS_ALERT_SCHEDULER_WATCHLISTS_0_STOCK_CODES_1=000660
```

```bash
curl -X PUT http://localhost:8080/api/v1/alerts/watchlists/partner-a \
  -H "X-HANA-OMNILENS-API-KEY: ${PARTNER_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{"stockCodes":["005930","000660"]}'

curl http://localhost:8080/api/v1/alerts/watchlists/partner-a \
  -H "X-HANA-OMNILENS-API-KEY: ${PARTNER_API_KEY}"
```

## KIS 실시간 시세 수신
- 기본값은 `KIS_REALTIME_ENABLED=false`이다.
- 활성화하면 `KIS_REALTIME_STOCK_CODES`의 종목마다 KIS 실시간 체결과 호가를 구독한다.
- 수신 메시지는 실시간 cache에 저장되고 quote/orderbook 응답에서 우선 사용된다.
- KIS 체결 tick 수신 시 `/ws/market/quotes` raw WebSocket 연결에도 `MarketQuote` JSON을 송신한다.
- 협력사는 `{"type":"QUOTE_STREAM_REPLAY","currency":"USD","after":"..."}` 메시지로 현재 quote snapshot replay를 요청할 수 있다.

```text
KIS_REALTIME_ENABLED=true
KIS_REALTIME_STOCK_CODES=005930,000660
```

## KRX 과거 시세 수집
- 기본값은 `MARKET_HISTORY_COLLECTION_ENABLED=false`다.
- 활성화하면 설정된 주기마다 전일 기준 KOSPI/KOSDAQ/KONEX 일별매매정보를 KRX Open API에서 수집해 DB에 upsert한다.
- 수동 운영 수집은 `POST /api/v1/market/history/collect?baseDate=YYYY-MM-DD`로 실행한다.
- 차트 조회는 `GET /api/v1/market/stocks/{stockCode}/history?from=YYYY-MM-DD&to=YYYY-MM-DD&limit=365`를 사용한다.
- Stock-exchange-BE는 KRX를 직접 호출하지 않고 이 history API를 호출해 앱 차트 응답으로 재가공한다.

```text
MARKET_HISTORY_COLLECTION_ENABLED=true
MARKET_HISTORY_COLLECTION_FIXED_DELAY_MS=86400000
MARKET_HISTORY_COLLECTION_BASE_DATE_OFFSET_DAYS=1
```

## 헬스체크
- `GET /actuator/health`
- `GET /actuator/info`
- 운영 profile에서는 Redis health indicator가 포함되므로 Redis 장애 시 health가 `DOWN`으로 표시된다.

## API 계약 문서
- `GET /openapi.yaml`
- 문서는 협력사 API key 보호 대상이다.
- REST endpoint, STOMP alert WebSocket topic, raw market quote WebSocket 계약을 함께 확인할 수 있다.
- DB credential을 사용하는 협력사는 `/topic/partners/{partnerId}/alerts` 또는 `/topic/partners/{partnerId}/stocks/{stockCode}/alerts`를 구독한다.
- `/topic/stocks/{stockCode}/alerts`는 bootstrap 전역 키 호환용 topic으로 유지한다.

## 협력사 입력 환율
- `PUT /api/v1/market/exchange-rates/{currency}`로 `KRW -> 현지통화` 표시용 환율을 저장한다.
- quote 요청에 `fxRate`가 없으면 저장된 환율을 현지 통화 환산가 계산에 사용한다.
- quote 요청에 `fxRate`가 있으면 해당 요청값을 우선한다.
- 기본 캐시는 Redis TTL 기반이며 Redis 장애 시 같은 프로세스의 in-memory fallback을 사용한다.
- 로컬 테스트에서 Redis 없이 실행해야 하면 `EXCHANGE_RATE_CACHE_MODE=in-memory`로 전환한다.

```bash
curl -X PUT http://localhost:8080/api/v1/market/exchange-rates/USD \
  -H "X-HANA-OMNILENS-API-KEY: ${PARTNER_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{"fxRate":0.00072}'
```

```text
EXCHANGE_RATE_CACHE_MODE=redis
EXCHANGE_RATE_CACHE_TTL=24h
```

## 종목 마스터 DB
- Flyway가 `stock_master` 테이블을 생성한다.
- 기본 seed 파일은 `classpath:data/stock-master-seed.csv`이다.
- seed loader는 테이블이 비어 있을 때만 실행되며, 이미 적재된 데이터가 있으면 건너뛴다.
- 운영에서 seed 위치를 바꿀 때는 `STOCK_MASTER_SEED_LOCATION`을 사용한다.
- seed 적재를 끄려면 `STOCK_MASTER_SEED_ENABLED=false`로 설정한다.
- seed 파일은 종목코드, 한글명, 영문명, 시장구분, ISIN, OpenDART 고유번호 순서로 관리한다.
- OpenDART 고유번호가 아직 확정되지 않은 종목은 빈 값으로 둘 수 있다.

```text
STOCK_MASTER_SEED_ENABLED=true
STOCK_MASTER_SEED_LOCATION=classpath:data/stock-master-seed.csv
```

## 환율 provider
- 기본 provider는 Frankfurter REST 환율 endpoint인 `FRANKFURTER_BASE_URL`이다.
- 내부 캐시에는 `KRW -> 현지통화` 비율로 저장한다.
- 기본 refresh scheduler는 비활성화되어 있다.
- 활성화하면 `EXCHANGE_RATE_REFRESH_CURRENCIES`에 지정한 통화만 주기적으로 갱신한다.
- 한국수출입은행 환율 API는 사용하지 않는다. 해당 API는 공공데이터포털 키가 아니라 한국수출입은행 별도 신청 키가 필요하기 때문이다.

```text
EXCHANGE_RATE_REFRESH_ENABLED=true
EXCHANGE_RATE_REFRESH_FIXED_DELAY_MS=300000
EXCHANGE_RATE_REFRESH_BASE_DATE_OFFSET_DAYS=0
EXCHANGE_RATE_REFRESH_CURRENCIES=USD,JPY
```

## Rate Limit
- 기본값은 API key fingerprint당 1분에 120개 요청이다.
- 초과 시 `429 Too Many Requests`와 `Retry-After` 헤더를 반환한다.
- 운영 임계값은 `OMNILENS_RATE_LIMIT_CAPACITY`, `OMNILENS_RATE_LIMIT_REFILL_TOKENS`, `OMNILENS_RATE_LIMIT_REFILL_PERIOD`로 조정한다.
- 임시 비활성화는 `OMNILENS_RATE_LIMIT_ENABLED=false`로 한다.

## 협력사 API key registry
- Flyway가 `partner_api_credential` 테이블을 생성한다.
- 협력사별 API key는 원문을 저장하지 않고 SHA-256 해시만 저장한다.
- `active=false` credential은 인증에 사용할 수 없다.
- DB credential로 인증된 요청은 알림 API의 `partnerId`와 인증된 `partner_id`가 일치해야 한다.
- `OMNILENS_API_KEY_SHA256` bootstrap 키는 초기 운영과 비상 복구용 fallback으로 유지한다.

```sql
INSERT INTO partner_api_credential (api_key_sha256, partner_id, active)
VALUES ('{sha256_hex_of_raw_api_key}', 'partner-a', TRUE);
```

## Audit Log & Correlation ID
- 모든 요청은 `X-HANA-OMNILENS-CORRELATION-ID` 응답 헤더를 받는다.
- 협력사가 같은 헤더를 보내면 안전한 형식의 값만 그대로 사용하고, 없거나 안전하지 않으면 서버가 UUID를 생성한다.
- correlation id는 MDC `correlationId`에 저장되어 애플리케이션 로그와 장애 추적을 연결한다.
- 보안 감사 로그는 `com.hana.omnilens.audit.security` logger로 남긴다.
- 감사 로그는 인증 결과, method, path, API key hash prefix, 실패 사유만 기록하고 API key 원문은 기록하지 않는다.

## Request Signature Nonce Store
- HMAC 요청 서명 검증을 운영에서 켜면 nonce 저장소 기본값은 Redis다.
- Redis nonce key는 TTL을 가지며 같은 API key fingerprint와 nonce 조합은 한 번만 허용된다.
- Redis nonce store가 없거나 장애가 있으면 replay 방어를 보장할 수 없으므로 보호 API 요청은 `503`으로 실패 닫힘 처리된다.
- 로컬 테스트에서 Redis 없이 서명 검증을 실험해야 할 때만 `OMNILENS_SIGNATURE_NONCE_STORE_MODE=in-memory`를 사용한다.

## 외부 Provider Resilience
- 모든 `RestClient` 기반 외부 provider는 공통 connect/read timeout을 사용한다.
- 기본 timeout은 connect `2s`, read `5s`이다.
- `RestClientException` 계열 네트워크 장애는 기본 2회까지 재시도한다.
- 같은 provider의 연속 실패가 기본 5회에 도달하면 circuit breaker가 30초 동안 호출을 차단한다.
- Spring 컨테이너 내부 Hannah-Montana-AI 호출도 토큰 없이 같은 resilience 정책만 적용한다.

```text
PROVIDER_CONNECT_TIMEOUT=2s
PROVIDER_READ_TIMEOUT=5s
PROVIDER_RETRY_ENABLED=true
PROVIDER_RETRY_MAX_ATTEMPTS=2
PROVIDER_RETRY_BACKOFF=150ms
PROVIDER_CIRCUIT_BREAKER_ENABLED=true
PROVIDER_CIRCUIT_BREAKER_FAILURE_THRESHOLD=5
PROVIDER_CIRCUIT_BREAKER_OPEN_DURATION=30s
```

## 운영 전 보강
- 협력사별 key rotation 자동화
