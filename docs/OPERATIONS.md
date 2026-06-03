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
- KIS 현재가 provider를 사용하려면 `KIS_APP_KEY`, `KIS_APP_SECRET`, `KIS_ACCESS_TOKEN`을 GitHub Secrets에 등록한다.
- KIS WebSocket provider를 사용하려면 `KIS_WEBSOCKET_URL`, `KIS_APPROVAL_KEY`를 GitHub Secrets에 등록한다.
- `KIS_APPROVAL_KEY`는 WebSocket 접속키이며 REST `KIS_ACCESS_TOKEN`과 혼용하지 않는다.
- 한국수출입은행 환율 provider를 사용하려면 `KOREA_EXIM_AUTH_KEY`를 GitHub Secrets에 등록한다.
- `main` push 시 GitHub Secrets로 원격 서버의 `application-prod.env`를 생성한다.
- `main` push 시 Docker 이미지를 GHCR에 push한다.
- 원격 서버는 GHCR에서 이미지를 pull하고 `compose.prod.yml`로 컨테이너를 실행한다.
- `deploy-prod.env`는 GHCR pull용 값과 배포할 이미지 태그만 담고 앱 컨테이너에는 주입하지 않는다.

## 알림 주기 수집
- 기본값은 `omnilens.alert.scheduler.enabled=false`이다.
- 스케줄러를 켜면 설정된 협력사 watchlist마다 Naver 뉴스와 OpenDART 공시를 수집하고 Hannah-Montana-AI 분석 후 WebSocket으로 발행한다.
- 주기는 `ALERT_SCHEDULER_FIXED_DELAY_MS`로 조정한다. 기본값은 `300000`이다.
- 수집 범위는 `ALERT_SCHEDULER_NEWS_DISPLAY`, `ALERT_SCHEDULER_DISCLOSURE_LOOKBACK_DAYS`로 조정한다.
- watchlist는 Spring indexed env 또는 `SPRING_APPLICATION_JSON`으로 주입한다.

```text
OMNILENS_ALERT_SCHEDULER_WATCHLISTS_0_PARTNER_ID=partner-a
OMNILENS_ALERT_SCHEDULER_WATCHLISTS_0_STOCK_CODES_0=005930
OMNILENS_ALERT_SCHEDULER_WATCHLISTS_0_STOCK_CODES_1=000660
```

## KIS 실시간 시세 수신
- 기본값은 `KIS_REALTIME_ENABLED=false`이다.
- 활성화하면 `KIS_REALTIME_STOCK_CODES`의 종목마다 KIS 실시간 체결과 호가를 구독한다.
- 수신 메시지는 실시간 cache에 저장되고 quote/orderbook 응답에서 우선 사용된다.

```text
KIS_REALTIME_ENABLED=true
KIS_REALTIME_STOCK_CODES=005930,000660
```

## 헬스체크
- `GET /actuator/health`
- `GET /actuator/info`
- 운영 profile에서는 Redis health indicator가 포함되므로 Redis 장애 시 health가 `DOWN`으로 표시된다.

## API 계약 문서
- `GET /openapi.yaml`
- 문서는 협력사 API key 보호 대상이다.
- REST endpoint와 STOMP WebSocket endpoint/topic 계약을 함께 확인할 수 있다.

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

## 한국수출입은행 환율 provider
- provider 응답의 `deal_bas_r`는 외화 기준 원화 환율이므로 내부 캐시에는 `KRW -> 현지통화` 비율로 변환해 저장한다.
- `JPY(100)`처럼 단위가 붙은 통화는 괄호 안 단위를 분자로 사용한다.
- 기본 endpoint는 `https://oapi.koreaexim.go.kr/site/program/financial/exchangeJSON`이다.
- 기본 refresh scheduler는 비활성화되어 있다.
- 활성화하면 `EXCHANGE_RATE_REFRESH_CURRENCIES`에 지정한 통화만 주기적으로 갱신한다.
- 한국수출입은행 영업일 데이터 지연에 대비해 `EXCHANGE_RATE_REFRESH_BASE_DATE_OFFSET_DAYS`로 조회 기준일을 조정할 수 있다.

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

## 운영 전 보강
- audit log
- 장애 추적 correlation id
- 외부 API timeout, retry, circuit breaker
- 배포 환경별 Secret Manager 연동
