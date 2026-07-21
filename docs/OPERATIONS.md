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
- 로컬 Docker Compose는 `application-local.yml`을 이미지에 굽지 않고 `/app/config/application-local.yml`로 읽기 전용 마운트한다.
- 로컬 JVM 실행은 profile 기본값 `local`로 `src/main/resources/application-local.yml`을 읽는다.

## 운영 설정
- `src/main/resources/application-prod.yml`은 커밋되는 실제 운영 profile 설정 파일이다.
- 민감값은 `${...}` 환경변수 placeholder로만 작성한다.
- 관리자 계정은 OCI에 SSH로 접속한 운영자만 DB에서 생성·초기화한다. 임시 비밀번호는 셸 기록·프로세스 인자·환경 파일에 남기지 않고, 초기화와 함께 `password_change_required=true`, `password_changed_at=NULL`, `session_version=session_version+1`을 적용한다.
- KIS 현재가 provider는 실전 REST domain `https://openapi.koreainvestment.com:9443`을 기본으로 사용한다.
- KIS 현재가 provider를 사용하려면 `KIS_APP_KEY`, `KIS_APP_SECRET`을 GitHub Secrets에 등록한다. `KIS_ACCESS_TOKEN`은 비워두면 앱이 자동 발급한다.
- 로컬 Compose는 gitignored `.env.local`에서 일반 종목용 `OMNI_CONNECT_PROVIDERS_KIS_*`에 모의 계정과 모의 endpoint를, 지수 전용 `OMNI_CONNECT_PROVIDERS_REAL_KIS_*`에 실전 계정과 실전 endpoint를 각각 주입한다. 승인키와 access token은 저장하지 않고 기동 시 계정별로 발급한다.
- KIS WebSocket provider는 실전 WebSocket domain `ws://ops.koreainvestment.com:21000`을 기본으로 사용한다.
- KIS WebSocket provider를 사용하려면 `KIS_WEBSOCKET_URL`을 설정할 수 있다. `KIS_APPROVAL_KEY`는 비워두면 앱이 자동 발급한다.
- 환율 provider는 `FRANKFURTER_BASE_URL` 하나만 사용한다. Frankfurter public API는 별도 API key가 필요 없다.
- `main` push 시 외부 credential과 데이터 서비스 비밀번호는 GitHub Secrets로 원격 서버 환경 파일을 생성하고, 내부 런타임 키는 OCI 호스트 루트키에서 자동 파생한다.
- `main` push 시 Docker 이미지를 GHCR에 push한다.
- 원격 서버는 GHCR에서 이미지를 pull하고 `compose.prod.yml`로 컨테이너를 실행한다.
- `deploy-prod.env`는 GHCR pull용 값과 배포할 이미지 태그만 담고 앱 컨테이너에는 주입하지 않는다.

## 알림 주기 수집
- 기본값은 `omni-connect.alert.scheduler.enabled=true`이다.
- 스케줄러를 켜면 설정 또는 DB에 저장된 협력사 watchlist와 기본 종목 universe마다 Naver 뉴스와 OpenDART 공시를 수집하고 Hannah-Montana-AI 분석 후 WebSocket으로 발행한다.
- 기본 종목 universe는 `stock_master_priority` 인기 종목과 외국인 취득한도 제한 종목 allowlist를 합친 뒤 20종목 단위로 나누어 수집한다.
- Naver News Search는 제목, snippet, 링크 발견용으로 사용하고, 사용 허가된 원문 URL에서 기사 전문과 대표 이미지 URL을 추가 수집한다. 저장 허가가 없는 provider를 추가할 때는 원문 저장을 비활성화하고 hash/요약만 남기는 별도 정책을 적용한다.
- OpenDART는 공시 목록 검색 뒤 `rcept_no` document 원문을 내려받아 본문을 정제한다. 수 MB가 될 수 있는 투자설명서·증권신고서는 동기 피드에 1,200자 분석 excerpt와 공식 DART 전문 URL을 저장해 API payload와 번역 지연을 제한한다.
- 신규 여부는 URL TTL만으로 판단하지 않는다. `(partner_id, stock_code, source_type, original_url)` DB unique identity와 Redis 실행 중 lock을 함께 사용하고, canonical URL, normalized title, content hash, Hannah duplicate key, 시간창 기반 cluster key를 추가 중복 판정에 사용한다.
- 주기 수집은 Hannah `DEFERRED` 분석으로 전체 본문 Qwen 번역을 호출하지 않는다. 종목 연결·감성·중요도·시장영향·영문 What/Why/Impact를 먼저 저장하며, 전문은 보강 완료 전까지 `ORIGINAL_TEXT_ONLY`와 원문 링크로 제공한다. 전용 2-worker 큐가 미번역 종목 알림과 시장뉴스를 각각 최대 10건씩 연속 보강하고, 성공 시 같은 레코드를 `FULL_TEXT`로 갱신한다. 상세 조회는 아직 미번역인 본문을 즉시 보강한다. 전문 분할은 Hannah만 담당하며 OmniConnect에서 중복 분할하지 않는다.
- 전체 번역은 `HANNAH_AI_BASE_URL`의 Hannah `/api/v1/translation/ko-en`만 호출한다. Hannah는 로컬 Qwen 4B GGUF를 사용하며 OmniConnect에는 provider 선택값이 없다. 내부 AI 호출은 `HANNAH_AI_CONNECT_TIMEOUT`, `HANNAH_AI_READ_TIMEOUT`으로 제한한다. provider 장애, 빈 응답, 한글 잔존 시 원문과 `SOURCE_LANGUAGE_FALLBACK` 또는 `PARTIAL_SOURCE_LANGUAGE_FALLBACK` 상태를 반환한다.
- watchlist 조회/갱신, 단건 분석 발행, 수집 발행 REST 응답은 모두 `data`에 alert payload를 담은 공동 응답 envelope이다.
- Hannah-Montana-AI 분석 결과의 `eventConfidence`, `sentimentConfidence`, `importanceConfidence`, `stockMatchConfidence`는 alert REST/WebSocket payload에 그대로 전파한다.
- Hana Montana AI(KF-DeBERTa + K-FNSPID)의 감성 후보는 중복·충돌과 파티션 간 중첩을 제거한 Validation Selection에서 잠근 KF-DeBERTa LoRA다. 공개 재현 Test 932건 macro F1은 0.8849로 KR-FinBERT-SC의 0.7266보다 높지만, 해당 Test의 과거 반복 조회 때문에 독립 SOTA 근거로 사용하지 않는다. 실제 뉴스 Gold 정확도 0.8625가 운영 gate 0.90에 미달하면 신규 후보를 승격하지 않고 기존 검증 모델을 유지한다. `importance`는 Validation으로 선택한 제목+요약 공시 의미 모델과 존속위험 정책, `marketImpactImportance/Score/Confidence`는 파일 기반 K-FNSPID v4의 뉴스·공시 출처별 전문가로 분리한다. 시간 Test에서 뉴스 전문가는 9,560건 macro F1 0.3745 / QWK 0.4754, 공시 전문가는 4,615건 macro F1 0.3216 / QWK 0.1550이며 두 출처 모두 자체 기준선을 넘는다. 요청 출처와 모델 출처가 다르면 Hannah가 추론을 거부한다. 가격반응은 의미 라벨이나 confidence를 덮어쓰지 않는다. OmniConnect는 복합 `modelVersion`과 두 신호를 REST/WebSocket 이벤트에 그대로 전파하며, 시장 시세 데이터셋을 운영 DB에서 다시 export하지 않는다.
- 주기는 `ALERT_SCHEDULER_FIXED_DELAY_MS`로 조정한다. 기본값은 `300000`이다.
- 수집 범위는 `ALERT_SCHEDULER_NEWS_DISPLAY`, `ALERT_SCHEDULER_DISCLOSURE_LOOKBACK_DAYS`로 조정한다. 최신 저장 이벤트는 완료 슬롯으로 계산하므로 주기 실행마다 원문과 번역을 다시 요청하지 않는다.
- 기본 universe는 `ALERT_SCHEDULER_DEFAULT_UNIVERSE_ENABLED`, `ALERT_SCHEDULER_PRIORITY_STOCK_LIMIT`, `ALERT_SCHEDULER_INCLUDE_FOREIGN_OWNERSHIP_RESTRICTED_STOCKS`, `ALERT_SCHEDULER_COLLECTION_BATCH_SIZE`로 조정한다.
- 운영 중 watchlist는 `PUT /api/v1/alerts/watchlists/{partnerId}`로 DB에 저장한다.
- 설정 파일 watchlist는 부트스트랩 또는 비상 운영용으로 유지하며, DB watchlist와 협력사별로 병합된다.

```text
OMNI_CONNECT_ALERT_SCHEDULER_WATCHLISTS_0_PARTNER_ID=partner-a
OMNI_CONNECT_ALERT_SCHEDULER_WATCHLISTS_0_STOCK_CODES_0=005930
OMNI_CONNECT_ALERT_SCHEDULER_WATCHLISTS_0_STOCK_CODES_1=000660
```

```bash
curl -X PUT http://localhost:8080/api/v1/alerts/watchlists/partner-a \
  -H "X-HANA-OMNI-CONNECT-API-KEY: ${PARTNER_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{"stockCodes":["005930","000660"]}'

curl http://localhost:8080/api/v1/alerts/watchlists/partner-a \
  -H "X-HANA-OMNI-CONNECT-API-KEY: ${PARTNER_API_KEY}"
```

## KIS 실시간 시세 수신
- 기본값은 `KIS_REALTIME_ENABLED=true`이다.
- 활성화하면 `KIS_REALTIME_STOCK_CODES`의 종목마다 KIS 실시간 체결과 선택된 호가를, `KIS_REALTIME_INDEX_CODES`의 지수를 구독한다.
- 수신 메시지는 실시간 cache에 저장되고 quote/orderbook 응답에서 우선 사용된다. 장운영정보 `H0STMKO0`의 거래정지 사유는 `circuitBreakerActive`, `tradingHalted`, `tradingHaltReason`으로 정규화한다.
- 장외, 휴장, 초기 구동 등으로 실시간 호가 cache가 비어 있으면 orderbook API는 KIS REST 호가 snapshot을 사용한다.
- 휴장일 또는 재기동 직후 KIS 현재가가 지연·장애이면 PostgreSQL `market_intraday_minute_price`의 가장 최근 정규장 체결가를 quote/detail fallback으로 사용한다. `marketDataTime`은 응답 생성 시각이 아니라 해당 체결 시각이며 `source`는 `KIS_INTRADAY_PRICE_SNAPSHOT`으로 유지한다.
- 호가처럼 마지막 확정 snapshot을 저장하지 않는 데이터는 빈 호가를 임의 생성하지 않는다. KIS 실시간·REST 호가가 모두 없으면 orderbook API는 명시적으로 unavailable을 반환한다.
- KIS 체결 tick 또는 장운영 상태 변경 수신 시 `/ws/market/quotes` raw WebSocket 연결에도 `MarketQuote` JSON을 송신한다. 거래가 멈춘 서킷브레이커 상태도 다음 체결을 기다리지 않는다.
- 협력사는 `{"type":"QUOTE_STREAM_REPLAY","currency":"USD","after":"..."}` 메시지로 현재 quote snapshot replay를 요청할 수 있다.

```text
KIS_REALTIME_ENABLED=true
KIS_REALTIME_STOCK_CODES=005930,000660,005380,000270,086790,035420,068270,105560,055550,012330
KIS_REALTIME_INDEX_CODES=0001,1001,2001
```

## KRX 과거 시세 수집
- 기본값은 `MARKET_HISTORY_COLLECTION_ENABLED=false`다.
- 활성화하면 설정된 주기마다 전일 기준 KOSPI/KOSDAQ/KONEX 일별매매정보를 KRX Open API에서 수집해 DB에 upsert한다.
- 수동 운영 수집은 `POST /api/v1/market/history/collect?baseDate=YYYY-MM-DD`로 실행한다.
- 수집 응답은 전체/시장별 상태를 포함한다. KRX provider timeout이 발생해도 서비스 500으로 전파하지 않고 실패 시장과 오류 메시지를 `marketResults`에 남긴다.
- `MARKET_HISTORY_COLLECTION_PROVIDER`는 `KRX_OPEN_API_WITH_KIS_BACKUP`, `KRX_OPEN_API`, `KIS_DAILY_CHART` 중 하나다. KRX egress가 허용되지 않는 로컬 smoke 환경은 `KIS_DAILY_CHART`를 사용해 KIS 일봉 chart API를 primary 실 provider로 수집한다.
- `KRX_OPEN_API_WITH_KIS_BACKUP`에서 KRX 시장 호출이 하나라도 실패하면 종목 마스터 기준으로 KIS 일봉 chart API를 순차 호출해 해당 기준일 데이터를 보강 저장한다. 이 경우 응답 `source`는 `KRX_OPEN_API_DAILY_TRADE+KIS_DAILY_ITEM_CHART_PRICE`가 되고, KRX 시장 실패가 남아 있으면 전체 `status`는 `PARTIAL_FAILED`다.
- `KIS_DAILY_CHART` 모드의 응답 `source`는 `KIS_DAILY_ITEM_CHART_PRICE`이며, KIS 수집 결과만으로 `SUCCESS`, `PARTIAL_FAILED`, `FAILED`를 판단한다.
- KIS 일봉 수집은 provider 초당 제한을 넘지 않도록 종목별 호출 간격을 1.2초로 둔다. 제한 응답은 짧게 대기 후 재시도한다.
- 차트 조회는 `GET /api/v1/market/stocks/{stockCode}/history?from=YYYY-MM-DD&to=YYYY-MM-DD&limit=365`를 사용한다.
- 저장된 KRX row가 없을 때는 KIS 일봉 chart API를 호출해 해당 종목의 일봉을 저장하고 반환한다.
- KIS·KRX 일봉 provider가 휴장일 또는 외부 장애로 응답하지 않아도, 최근 7일 요청 범위에 저장된 정규장 분봉이 있으면 서버가 실제 OHLCV를 집계해 `KIS_REALTIME_TRADE_DAILY_AGGREGATE` 출처로 일봉을 저장·반환한다. 집계 대상이 없는 날짜를 다른 날짜의 가격으로 복제하지 않는다.
- Stock-exchange-BE는 KRX를 직접 호출하지 않고 이 history API를 호출해 앱 차트 응답으로 재가공한다.

```text
MARKET_HISTORY_COLLECTION_ENABLED=true
MARKET_HISTORY_COLLECTION_FIXED_DELAY_MS=86400000
MARKET_HISTORY_COLLECTION_BASE_DATE_OFFSET_DAYS=1
```

## 주문 가능 여부 boundary
- `GET /api/v1/market/stocks/{stockCode}/orderability?side=BUY&quantity=1`를 사용한다.
- 이 API는 현지 거래소의 자체 mock ledger 주문 전 확인용이며 실제 주문, 체결, 정산, KIS 모의투자 주문을 수행하지 않는다.
- BUY 요청은 KRX 외국인보유량 cache의 현재 snapshot 종목코드가 외국인 취득한도 제한 법령 allowlist에 있을 때만 외국인 한도 예측을 붙인다. 비제한 종목은 ratio가 높아도 한도 경고 ML 대상이 아니다.
- SBS, KNN, 티비씨처럼 KRX가 외국인 보유/한도/소진율을 모두 0으로 반환하는 0% 취득불허 종목은 Hannah를 호출하지 않고 confidence `FOREIGN_LIMIT_ZERO_NOT_ACQUIRABLE`, model version `foreign-ownership-zero-limit-v1`로 경고한다.
- 제한 종목은 최근 외국인 보유 일별 시계열로 금일 외국인 취득 수량이 한도에 도달할 가능성을 min/base/max 한도소진율로 계산한다. 요청 수량과 KIS 실시간 누적 거래량은 외국인 한도 예측식에 반영하지 않는다.
- 제한이 없는 종목은 Hannah를 호출하지 않고 confidence `FOREIGN_LIMIT_NOT_APPLICABLE`, model version `foreign-ownership-unrestricted-v1`로 현재 snapshot 값을 반환한다.
- 외국인 한도 예측은 주문 차단 조건이 아니다. 제한 종목의 AI 예측 상단이 화면 표시 기준 `100.00%`로 반올림되는 `99.995%` 이상이면 orderability의 `foreignLimitExceeded=true`와 상세의 `foreignLimitBuyWarning=true`를 반환한다. 이는 BUY 주문이 체결되지 않을 수 있음을 프론트에서 미리 고지하기 위한 경고 신호이며, `orderable=false`는 거래정지 같은 시장 상태 차단에만 사용한다.
- KIS 실시간 체결가·호가 WebSocket에는 외국인 보유수량, 보유율, 한도소진율 필드가 없다. 외국인 한도 정보는 KRX Data Marketplace snapshot refresh와 Redis/in-memory cache로 공급한다.
- 고정 WebSocket 구독 밖의 종목 상세 1분봉은 Yahoo Finance를 우선 조회하고 부족한 구간을 KIS REST 분봉으로 장 시작 시각부터 백필한 뒤 PostgreSQL `ON CONFLICT` upsert로 저장한다. 저장소 장애가 발생해도 이미 조회한 provider 분봉을 해당 요청에 반환해 차트와 High/Low/Prev 표시가 빈 상태로 내려가지 않게 한다.
- 제한 종목 예측은 장전 batch가 Redis/in-memory `ForeignOwnershipPredictionCache`에 선계산한다. Hannah `hannah-foreign-owned-quantity-ml-v2`는 종목별 walk-forward 검증 절대오차 90분위수를 안전 상한으로 두고 최신 60개 관측의 일별 절대변화 90분위수로 현재 변동성 국면을 반영하며, 전체 종목에 동일 비율을 적용하지 않는다. Redis key namespace는 `v2`로 분리해 이전 모델의 넓은 구간이 남지 않게 한다. 모바일 거래소의 `orderability/detail` 요청은 cache를 먼저 읽고, cache miss일 때만 Hannah-Montana-AI `POST /api/v1/market/foreign-ownership/predict`를 호출한다.
- 휴장일이나 수집 지연으로 당일 snapshot이 없으면, 제한 종목도 요청 기준일 이전 7일 안의 가장 최근 KRX snapshot을 사용한다. 응답의 `foreignOwnershipBaseDate`로 실제 기준일을 노출하며, 이전 거래일 데이터를 `NO_SNAPSHOT`으로 버리지 않는다.
- Hannah 호출 실패, circuit open, 비정상 envelope 응답 시에는 OmniConnect 내부 deterministic 시계열 엔진으로 fallback해 응답 계약과 주문 전 확인 흐름을 유지한다.
- 외국인 보유 일별 history는 `POST /api/v1/market/foreign-ownership/collect`, `POST /api/v1/market/foreign-ownership/backfill`, `ForeignOwnershipRefreshScheduler`가 `foreign_ownership_daily_snapshot`에 upsert한다. 기본 수집 대상은 현재 상장 외국인 취득한도 제한 32종목 allowlist이며, `stockCodes`를 명시한 수동 요청만 별도 종목을 조회한다.
- KIS 현재가는 전일 증분 snapshot 수집에 사용하지 않는다. 초기 1년+ 과거 백필과 이후 누락일 보강은 `KRX_SCRAPING_ENABLED=true`, `KRX_ID`, `KRX_PW`가 설정된 경우 KRX Data Marketplace 로그인 기반 `MDCSTAT03702` provider로 수행한다. provider가 비어 있으면 현재 snapshot을 과거 날짜로 복제하지 않는다.
- 새 전일 snapshot이 저장되면 스케줄러가 제한 종목 전체 history를 Hannah `POST /api/v1/market/foreign-ownership/model/retrain`으로 전송한다. Hannah는 quality gate를 통과한 모델만 promote하고 reload한다.
- 재학습 호출 이후 `FOREIGN_OWNERSHIP_PREDICTION_PRECOMPUTE_ENABLED=true`와 `FOREIGN_OWNERSHIP_PREDICTION_PRECOMPUTE_TRIGGER_AFTER_REFRESH=true`이면 제한 종목 금일 예측을 즉시 선계산해 cache에 저장한다. 따라서 장중 모바일 요청은 대부분 미리 계산된 결과만 조회한다.
- SELL 요청은 외국인 한도소진율이 100% 이상이어도 외국인 한도 경고를 만들지 않는다.
- KIS 실시간 체결 cache가 있으면 1호가 공백 패턴으로 `priceLimitState=UPPER_LIMIT|LOWER_LIMIT|NORMAL`을 판단한다. `viActive`, `singlePriceTrading`, `tradingHalted`, `circuitBreakerActive`는 `H0STMKO0` 장운영정보를 우선 사용하며, 체결 `H0STCNT0`의 35번 거래정지 여부만 fallback으로 사용한다. 발동 후 프로세스가 재접속해 `H0STMKO0` 변경 이벤트를 놓친 경우에는 2분 이내 실전 KOSPI/KOSDAQ 지수의 -7.90% 이하 하락과 같은 시장의 20초 이상 체결 부재가 동시에 확인될 때만 서킷 상태를 복구한다. VI 기준가인 45번 필드를 거래정지로 해석하지 않는다. 거래정지 상태가 활성화되면 주문 가능 여부는 `TRADING_HALTED`로 차단한다.

## 헬스체크
- `GET /actuator/health`
- `GET /actuator/info`
- 운영 profile에서는 Redis health indicator가 포함되므로 Redis 장애 시 health가 `DOWN`으로 표시된다.

## API 계약 문서
- `GET /openapi.yaml`
- 문서는 협력사 API key 보호 대상이다.
- REST endpoint, STOMP alert WebSocket topic, raw market quote WebSocket 계약을 함께 확인할 수 있다.
- DB credential을 사용하는 협력사는 `/topic/partners/{partnerId}/alerts` 또는 `/topic/partners/{partnerId}/stocks/{stockCode}/alerts`를 구독한다.
- 협력사는 `/topic/partners/{partnerId}/alerts`와 `/topic/partners/{partnerId}/stocks/{stockCode}/alerts`에서 자기 `partnerId` topic만 구독한다.

## 실시간 시세 수요 구독
- 인기 10종목은 `OMNI_CONNECT_MARKET_KIS_REALTIME_STOCK_CODES`로, KOSPI·KOSDAQ·KOSPI200 지수는 `OMNI_CONNECT_MARKET_KIS_REALTIME_INDEX_CODES=0001,1001,2001`로 API 기동 시 KIS WebSocket에 고정 선구독한다.
- KIS 체결 `H0STCNT0`와 호가 `H0STASP0`의 합산 등록 한도는 40 TR이다. 장운영정보 `H0STMKO0`는 같은 종목 수명주기로 구독·해제하되 체결·호가 40 TR 로테이션 계산에서는 분리한다. 지수 TR도 이 합산값과 분리해 고정하며, 호가를 켜면 종목당 체결·호가 2 TR을 소비한다.
- 상세 화면처럼 인기 종목 외 종목이 필요하면 협력사는 `/ws/market/quotes`에 아래 메시지를 전송한다.

```json
{
  "type": "QUOTE_STREAM_SUBSCRIBE",
  "currency": "USD",
  "stockCodes": ["091990"]
}
```

- OmniConnect는 인기 종목과 지수 고정 슬롯을 해제하지 않는다. 남은 슬롯은 상세 종목 전용 LRU로 관리하며, 한도에 도달하면 가장 오래 사용하지 않은 상세 종목의 해제 프레임을 먼저 보낸 뒤 새 종목을 등록한다.
- `POST/DELETE /api/v1/market/stocks/{stockCode}/realtime-subscription`은 상세 화면 진입·이탈 수명주기를 같은 관리자로 전달한다. 요청 직후 현재 REST snapshot도 파트너 WebSocket 세션으로 재송신한다.
- KIS 연결이 정상 close code로 종료된 경우도 지수 백오프와 jitter로 재연결하고 고정·동적 구독을 복원한다.
- API 기동 시 승인키 발급이 실패해 WebSocket 연결 전 단계에서 중단된 경우에도 최대 30초의 지수 백오프와 jitter로 승인키 발급부터 다시 시도한다.
- 모의투자 app key는 실전 지수 endpoint 인증에 재사용하지 않는다. 지수 3개 실시간 구독에는 `real-kis` app key·app secret을 별도로 설정해야 하며, 누락 시 잘못된 승인키로 재시도하지 않고 지수 연결을 비활성화한다.
- KIS가 `OPSP0011`로 승인키를 거부하면 같은 자격증명으로 재연결을 반복하지 않는다. 일반 원격 종료는 성공 제어 메시지 이후 attempt를 초기화하고, 연속 실패는 누적 attempt의 지수 백오프와 jitter를 적용한다.

## 한국 증시 시장뉴스
- 종목별 뉴스·공시는 `/api/v1/alerts/stocks/{stockCode}/events`를 사용하고, 한국 증시 전체 뉴스는 별도 `/api/v1/market/news`를 사용한다.
- 스케줄러는 `MARKET_NEWS_SCHEDULER_ENABLED=true`일 때 `MARKET_NEWS_SCHEDULER_FIXED_DELAY_MS` 간격으로 `MARKET_NEWS_SCHEDULER_QUERIES` 검색어를 수집한다.
- 수동 수집은 `POST /api/v1/market/news/collect`로 실행한다.

```json
{
  "queries": ["한국 증시"],
  "display": 10
}
```

```text
MARKET_NEWS_SCHEDULER_ENABLED=true
MARKET_NEWS_SCHEDULER_FIXED_DELAY_MS=300000
MARKET_NEWS_SCHEDULER_DISPLAY=10
MARKET_NEWS_SCHEDULER_QUERIES=한국 증시,코스피 코스닥,국내 증시
```

## 협력사 입력 환율
- `PUT /api/v1/market/exchange-rates/{currency}`로 `KRW -> 현지통화` 표시용 환율을 저장한다.
- 응답은 `data.baseCurrency`, `data.localCurrency`, `data.fxRate`, `data.updatedAt`를 담은 공동 응답 envelope이다.
- quote 요청에 `fxRate`가 없으면 저장된 환율을 현지 통화 환산가 계산에 사용한다.
- quote 요청에 `fxRate`가 있으면 해당 요청값을 우선한다.
- 기본 캐시는 Redis TTL 기반이며 Redis 장애 시 같은 프로세스의 in-memory fallback을 사용한다.
- 로컬 테스트에서 Redis 없이 실행해야 하면 `EXCHANGE_RATE_CACHE_MODE=in-memory`로 전환한다.

```bash
curl -X PUT http://localhost:8080/api/v1/market/exchange-rates/USD \
  -H "X-HANA-OMNI-CONNECT-API-KEY: ${PARTNER_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{"fxRate":0.00072}'
```

```text
EXCHANGE_RATE_CACHE_MODE=redis
EXCHANGE_RATE_CACHE_TTL=24h
```

## 종목 마스터 DB
- Flyway가 `stock_master` 테이블을 생성한다.
- 단건 조회 `GET /api/v1/market/stocks/{stockCode}`는 `StockSummary`를 `data`에 담은 공동 응답 envelope으로 반환한다.
- 기본 seed 파일은 `classpath:data/stock-master-seed.csv`이다.
- seed loader는 테이블이 비어 있을 때만 실행되며, 이미 적재된 데이터가 있으면 건너뛴다.
- KIS master sync는 KIS master zip의 상품그룹 `ST` row만 주식으로 인식하고 KOSPI·KOSDAQ·KONEX를 시장별로 reconcile한다. 다운로드에 성공한 시장만 현재 row를 `active=true`로 갱신하고, 스냅샷에서 사라진 row는 `active=false`로 전환한다. 다운로드가 실패한 시장의 기존 상태는 유지한다.
- 검색·전체 건수·단건 지원 여부는 `active=true`인 종목만 반환한다. 재상장되거나 종목명이 변경된 코드는 다음 성공 스냅샷에서 재활성화하며 기존 영문명·OpenDART metadata를 보존한다.
- KIS master sync 이후 외국인 보유량 수집을 실행해야 한다. 새로 보강된 종목은 `foreign_ownership_daily_snapshot`에 history가 없으므로 KRX Data Marketplace 백필이 끝나기 전까지 제한 종목 산출에서 누락될 수 있다.
- 운영에서 seed 위치를 바꿀 때는 `STOCK_MASTER_SEED_LOCATION`을 사용한다.
- seed 적재를 끄려면 `STOCK_MASTER_SEED_ENABLED=false`로 설정한다.
- seed 파일은 종목코드, 한글명, 영문명, 시장구분, ISIN, OpenDART 고유번호 순서로 관리한다.
- OpenDART 고유번호가 아직 확정되지 않은 종목은 빈 값으로 둘 수 있다.

```text
STOCK_MASTER_SEED_ENABLED=true
STOCK_MASTER_SEED_LOCATION=classpath:data/stock-master-seed.csv
```

## 글로벌 피어 매칭
- `GET /api/v1/market/stocks/{stockCode}/global-peers`는 종목 상세 화면의 피어 종목 보기 요청에 사용한다.
- OmniConnect는 `stock_master`의 종목코드, 한글명, 영문명, 시장구분을 Hannah `POST /api/v1/market/global-peers/match`로 전달한다.
- Hannah 응답은 headline, summary, primary peer, 기존 후보 peer 목록, 속성별 comparisons, 국내 종목 자체의 key strengths, confidence, model version을 전달한다.
- 각 peer의 섹터, 산업, 사업모델, 규모 버킷, 시가총액, 매출, 영업이익, 순이익, 재무 데이터 출처, 재무 유사도, 매칭 근거 배열은 프론트 피어 설명 팝업에서 바로 사용할 수 있도록 응답에 보존한다.
- 정상 응답은 comparisons 1~3개와 key strengths 4개를 요구한다. dimension과 icon key allowlist 위반, 빈 필수 문구, 카드 개수 위반은 정상 결과로 전달하지 않는다.
- Hannah 장애, circuit open 또는 응답 계약 위반은 `MARKET_DATA_UNAVAILABLE`로 종료한다. 임의 peer와 빈 비교 카드를 반환하지 않는다.

## 한국 금융 고유어·전문용어 해설
- 모바일 거래소 또는 협력사 백엔드는 뉴스/공시 본문에서 사용자가 누른 한국 금융 고유어·전문용어를 `POST /api/v1/korean-financial-terms/explain`로 보낸다.
- OmniConnect는 Hannah `POST /api/v1/korean-financial-terms/explain`를 호출하고, `displayMode=EXPLANATION`이면서 `cacheable=true`인 응답만 `korean_financial_term_explanation_cache`에 저장한다.
- 검증 사전 hit는 TTL 동안 재사용한다. 미등록 한글 용어는 `REVIEW_REQUIRED`로 반환하고 cache하지 않는다.
- 모든 클릭은 `korean_financial_term_click_log`에 저장하고, `korean_financial_term_click_stats`에 누적 집계한다. 사용자와 세션 식별자는 원문 저장 없이 `OMNI_CONNECT_TERM_ANALYTICS_HASH_SALT`로 salted SHA-256 처리한다.
- 클릭 집계는 사전 우선순위와 수동 검수 queue 선정에 사용한다.
- 통계 확인은 `GET /api/v1/korean-financial-terms/stats`를 사용한다.
- 포털 관리자는 `GET /api/v1/portal/admin/term-analytics?period=DAY|MONTH|YEAR|ALL`로 실제 클릭 수를 날짜 버킷과 용어별 합계로 조회한다. 캐시 hit는 클릭과 별도 지표로 노출하지 않는다.

## 포털 API 키와 제한세율 신청 운영
- 회원은 API 키 신청을 취소할 수 있고 승인된 키의 재발급·폐기를 요청할 수 있다. 관리자는 대기 요청을 승인·반려하고 승인 키를 즉시 재발급·폐기할 수 있다.
- 취소·반려·폐기 뒤 재신청은 동일 파트너 신청 레코드를 새 `PENDING` 상태로 초기화해 DB의 파트너 고유성과 credential rotation 경계를 유지한다.
- 거래소 동기화는 검증 완료 원본 3개의 MIME, SHA-256, Base64를 함께 보내며 서버는 크기·hash·magic byte를 다시 검증한다. 문서 열람 API는 `ADMIN`만 허용하고 `no-store`, `nosniff`를 적용한다.
- 경정청구 PDF는 `classpath:/forms/tax-correction-request.pdf`와 서버 번들 글꼴만 사용한다. 클라이언트 양식 업로드는 받지 않으며 편집 필드 저장, PDF 다운로드, 승인 및 국세청 제출 순서로 처리한다.
- 포털 동기화·응답에는 거래 수익이나 환급 추정 금액을 포함하지 않는다.

## 환율 provider
- 기본 provider는 Frankfurter REST 환율 endpoint인 `FRANKFURTER_BASE_URL`이다.
- 내부 캐시에는 `KRW -> 현지통화` 비율로 저장한다.
- 기본 refresh scheduler는 비활성화되어 있다.
- 활성화하면 `EXCHANGE_RATE_REFRESH_CURRENCIES`에 지정한 통화만 주기적으로 갱신한다.
- 환율은 Frankfurter adapter와 cache를 기준으로 운영한다.
- 한국어→영어 번역은 Hannah의 로컬 Qwen3 endpoint만 사용한다.

```text
EXCHANGE_RATE_REFRESH_ENABLED=true
EXCHANGE_RATE_REFRESH_FIXED_DELAY_MS=300000
EXCHANGE_RATE_REFRESH_BASE_DATE_OFFSET_DAYS=0
EXCHANGE_RATE_REFRESH_CURRENCIES=USD,JPY
```

## 외국인 보유량 일별 refresh scheduler
- 운영 기본값은 `FOREIGN_OWNERSHIP_REFRESH_ENABLED=true`이며, `FOREIGN_OWNERSHIP_REFRESH_CRON` 기준 평일 장전 08:10 KST에 KRX Data Marketplace 외국인 보유 snapshot 누락분을 수집한다.
- 수집 대상은 `FOREIGN_OWNERSHIP_REFRESH_STOCK_CODES`가 있으면 해당 종목만, 비어 있으면 현재 상장 외국인 취득한도 제한 32종목 allowlist 기준 최대 `FOREIGN_OWNERSHIP_REFRESH_STOCK_LIMIT`개 종목이다.
- 스케줄러는 전일 snapshot 수집 후 `FOREIGN_OWNERSHIP_REFRESH_BACKFILL_LOOKBACK_DAYS` 범위에서 이미 저장된 날짜를 제외하고 비어 있는 평일만 backfill provider에 요청한다. 초기 운영은 기본 400일 lookback으로 1년+를 채우고, 이후에는 날짜가 넘어갈 때 비어 있는 최근 1일만 저장된다.
- KRX 요청 제한을 넘지 않도록 `FOREIGN_OWNERSHIP_REFRESH_REQUEST_DELAY_MS` 간격으로 종목별 요청을 처리한다.
- 한 종목의 KRX provider empty/failure는 전체 batch를 중단하지 않고 `PARTIAL` 결과로 격리한다.
- 수동 전일 snapshot 수집은 `POST /api/v1/market/foreign-ownership/collect?baseDate=YYYY-MM-DD&limit=5000&requestDelayMs=1200` 또는 `stockCodes=005930&stockCodes=000660`으로 실행한다.
- 수동 누락 백필은 `POST /api/v1/market/foreign-ownership/backfill?fromDate=YYYY-MM-DD&toDate=YYYY-MM-DD&limit=5000&requestDelayMs=1200`로 실행한다.
- 수동 모델 재학습은 `POST /api/v1/market/foreign-ownership/model/retrain`으로 실행한다. 이 API는 OmniConnect DB의 제한 종목 전체 외국인 보유 history를 export해 Hannah에 전달한다.
- 수동 예측 선계산은 `POST /api/v1/market/foreign-ownership/predictions/precompute`로 실행한다. 이 API는 현재 KRX snapshot과 DB history를 사용해 제한 종목 금일 예측을 cache에 저장한다.

```text
FOREIGN_OWNERSHIP_REFRESH_ENABLED=true
FOREIGN_OWNERSHIP_REFRESH_CRON="0 10 8 * * MON-FRI"
FOREIGN_OWNERSHIP_REFRESH_REQUEST_DELAY_MS=1200
FOREIGN_OWNERSHIP_REFRESH_BASE_DATE_OFFSET_DAYS=1
FOREIGN_OWNERSHIP_REFRESH_BACKFILL_LOOKBACK_DAYS=400
FOREIGN_OWNERSHIP_REFRESH_STOCK_LIMIT=5000
KRX_SCRAPING_ENABLED=true
KRX_ID=<secret>
KRX_PW=<secret>
FOREIGN_OWNERSHIP_REFRESH_STOCK_CODES=
FOREIGN_OWNERSHIP_MODEL_TRAINING_ENABLED=true
FOREIGN_OWNERSHIP_MODEL_TRAINING_TRIGGER_AFTER_REFRESH=true
FOREIGN_OWNERSHIP_PREDICTION_PRECOMPUTE_ENABLED=true
FOREIGN_OWNERSHIP_PREDICTION_PRECOMPUTE_TRIGGER_AFTER_REFRESH=true
FOREIGN_OWNERSHIP_PREDICTION_PRECOMPUTE_STOCK_LIMIT=5000
HANNAH_AI_MAINTENANCE_TOKEN=<호스트 루트키에서 자동 파생>
```

## Rate Limit
- 운영은 일반 협력사 API key fingerprint당 1분에 120개 요청을 허용한다.
- 초과 시 `429 Too Many Requests`, `COMMON_004`, `Retry-After` 헤더를 반환한다.
- Redis 고정 구간 카운터에 TTL이 없으면 다음 요청에서 1분 TTL을 원자적으로 복구한다.
- 다수 최종 사용자의 요청을 단일 키로 중계하는 신뢰된 거래소 파트너만 `partner_api_credential.rate_limit_exempt=true`로 지정한다. API key와 HMAC 서명 검증은 그대로 적용한다.
- 예외 정책은 키가 아니라 파트너에 귀속되며 재발급 시 새 credential에 승계된다.

## 협력사 API key registry
- Flyway가 `partner_api_credential` 테이블을 생성한다.
- 협력사별 API key는 원문을 저장하지 않고 SHA-256 해시만 저장한다.
- `active=false` credential은 인증에 사용할 수 없다.
- `rate_limit_exempt=true`인 활성 credential만 요청 제한을 건너뛰며 인증·서명·nonce 재사용 방지는 건너뛰지 않는다.
- 전문이 아직 번역되지 않은 뉴스·공시 상세 조회는 현재 저장값을 즉시 반환하고 15분 중복 방지 키와 우선순위 queue로 전문 번역을 요청한다. 클라이언트는 `translatedContent`가 채워질 때까지 상세 GET을 재조회한다.
- 일반 적체 worker는 한 번에 한 작업만 AI로 보내 Qwen 병렬 슬롯 하나를 상세 조회용으로 확보한다.
- DB credential로 인증된 요청은 알림 API의 `partnerId`와 인증된 `partner_id`가 일치해야 한다.
- 회원은 포털에서 API 이용을 신청하고 관리자는 관리자 화면에서 승인·반려한다.
- 승인·재발급 시 서버가 새 256-bit API key를 생성한다. 인증용 테이블에는 SHA-256 hash만 저장하고, 포털 재조회용 원문은 별도 암호문으로 보관해 소유 회원의 현재 비밀번호 재확인 후 횟수 제한 없이 제공한다.
- 같은 partner의 기존 활성 credential은 같은 트랜잭션에서 `active=false`로 전환되므로 기존 키는 즉시 폐기된다.
- 협력사 key 발급·재발급·폐기는 포털 관리자 API만 사용하며 DB 직접 입력과 전역 bootstrap key를 사용하지 않는다.

## Audit Log & Correlation ID
- 모든 요청은 `X-HANA-OMNI-CONNECT-CORRELATION-ID` 응답 헤더를 받는다.
- 협력사가 같은 헤더를 보내면 안전한 형식의 값만 그대로 사용하고, 없거나 안전하지 않으면 서버가 UUID를 생성한다.
- correlation id는 MDC `correlationId`에 저장되어 애플리케이션 로그와 장애 추적을 연결한다.
- 보안 감사 로그는 `com.hana.omni-connect.audit.security` logger로 남긴다.
- 감사 로그는 인증 결과, method, path, API key hash prefix, 실패 사유만 기록하고 API key 원문은 기록하지 않는다.

## Request Signature Nonce Store
- HMAC 요청 서명 검증을 운영에서 켜면 nonce 저장소 기본값은 Redis다.
- Redis nonce key는 TTL을 가지며 같은 API key fingerprint와 nonce 조합은 한 번만 허용된다.
- Redis nonce store가 없거나 장애가 있으면 replay 방어를 보장할 수 없으므로 보호 API 요청은 `503`으로 실패 닫힘 처리된다.
- 로컬 테스트에서 Redis 없이 서명 검증을 실험해야 할 때만 `OMNI_CONNECT_SIGNATURE_NONCE_STORE_MODE=in-memory`를 사용한다.

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
- 없음. 신규 운영 보강은 `docs/ROADMAP.md`의 다음 milestone으로 추가한다.

## OCI 컨테이너 데이터 서비스
- PostgreSQL과 Redis는 API와 같은 OCI A1 호스트의 `hana-omni-connect-internal` Docker 네트워크에서 실행한다.
- DB와 Redis는 호스트 포트를 publish하지 않는다.
- `hana-omni-connect-postgres-data`, `hana-omni-connect-redis-data` named volume을 삭제하지 않는다.
- PostgreSQL backup timer 상태는 `systemctl status hana-omni-connect-postgres-backup.timer`로 확인한다.
- 수동 백업은 `sudo systemctl start hana-omni-connect-postgres-backup.service`로 실행한다.
- 복구 전에는 API 컨테이너를 중지하고 `pg_restore --clean --if-exists`로 검증된 dump를 복원한다.
- OCI boot volume 또는 block volume에는 일일 backup policy와 보존 정책을 적용한다.
