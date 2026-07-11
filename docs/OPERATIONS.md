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
- 기본값은 `omnilens.alert.scheduler.enabled=true`이다.
- 스케줄러를 켜면 설정 또는 DB에 저장된 협력사 watchlist와 기본 종목 universe마다 Naver 뉴스와 OpenDART 공시를 수집하고 Hannah-Montana-AI 분석 후 WebSocket으로 발행한다.
- 기본 종목 universe는 `stock_master_priority` 인기 종목과 외국인 취득한도 제한 종목 allowlist를 합친 뒤 20종목 단위로 나누어 수집한다.
- Naver News Search는 제목, snippet, 링크 발견용으로 사용하고, 사용 허가된 원문 URL에서 기사 전문과 대표 이미지 URL을 추가 수집한다. 저장 허가가 없는 provider를 추가할 때는 원문 저장을 비활성화하고 hash/요약만 남기는 별도 정책을 적용한다.
- OpenDART는 공시 목록 검색 뒤 `rcept_no` document 원문을 내려받아 본문을 정제한다. 수 MB가 될 수 있는 투자설명서·증권신고서는 동기 피드에 1,200자 분석 excerpt와 공식 DART 전문 URL을 저장해 API payload와 번역 지연을 제한한다.
- 신규 여부는 URL TTL만으로 판단하지 않는다. `(partner_id, stock_code, source_type, original_url)` DB unique identity와 Redis 실행 중 lock을 함께 사용하고, canonical URL, normalized title, content hash, Hannah duplicate key, 시간창 기반 cluster key를 추가 중복 판정에 사용한다.
- 번역은 `HANNAH_AI_BASE_URL`의 Hannah `/api/v1/translation/ko-en`만 호출한다. Hannah는 로컬 Qwen 4B GGUF를 사용하며 OmniLens에는 provider 선택값이 없다. 내부 AI 호출은 `HANNAH_AI_CONNECT_TIMEOUT`, `HANNAH_AI_READ_TIMEOUT`으로 제한한다. provider 장애, 빈 응답, 한글 잔존 시 원문과 `SOURCE_LANGUAGE_FALLBACK` 또는 `PARTIAL_SOURCE_LANGUAGE_FALLBACK` 상태를 반환하고 이벤트 발행은 중단하지 않는다.
- watchlist 조회/갱신, 단건 분석 발행, 수집 발행 REST 응답은 모두 `data`에 alert payload를 담은 공동 응답 envelope이다.
- Hannah-Montana-AI 분석 결과의 `eventConfidence`, `sentimentConfidence`, `importanceConfidence`, `stockMatchConfidence`는 alert REST/WebSocket payload에 그대로 전파한다.
- 주기는 `ALERT_SCHEDULER_FIXED_DELAY_MS`로 조정한다. 기본값은 `300000`이다.
- 수집 범위는 `ALERT_SCHEDULER_NEWS_DISPLAY`, `ALERT_SCHEDULER_DISCLOSURE_LOOKBACK_DAYS`로 조정한다. 최신 저장 이벤트는 완료 슬롯으로 계산하므로 주기 실행마다 원문과 번역을 다시 요청하지 않는다.
- 기본 universe는 `ALERT_SCHEDULER_DEFAULT_UNIVERSE_ENABLED`, `ALERT_SCHEDULER_PRIORITY_STOCK_LIMIT`, `ALERT_SCHEDULER_INCLUDE_FOREIGN_OWNERSHIP_RESTRICTED_STOCKS`, `ALERT_SCHEDULER_COLLECTION_BATCH_SIZE`로 조정한다.
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
- 장외, 휴장, 초기 구동 등으로 실시간 호가 cache가 비어 있으면 orderbook API는 KIS REST 호가 snapshot을 사용한다.
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
- 수집 응답은 전체/시장별 상태를 포함한다. KRX provider timeout이 발생해도 서비스 500으로 전파하지 않고 실패 시장과 오류 메시지를 `marketResults`에 남긴다.
- `MARKET_HISTORY_COLLECTION_PROVIDER`는 `KRX_OPEN_API_WITH_KIS_BACKUP`, `KRX_OPEN_API`, `KIS_DAILY_CHART` 중 하나다. KRX egress가 허용되지 않는 로컬 smoke 환경은 `KIS_DAILY_CHART`를 사용해 KIS 일봉 chart API를 primary 실 provider로 수집한다.
- `KRX_OPEN_API_WITH_KIS_BACKUP`에서 KRX 시장 호출이 하나라도 실패하면 종목 마스터 기준으로 KIS 일봉 chart API를 순차 호출해 해당 기준일 데이터를 보강 저장한다. 이 경우 응답 `source`는 `KRX_OPEN_API_DAILY_TRADE+KIS_DAILY_ITEM_CHART_PRICE`가 되고, KRX 시장 실패가 남아 있으면 전체 `status`는 `PARTIAL_FAILED`다.
- `KIS_DAILY_CHART` 모드의 응답 `source`는 `KIS_DAILY_ITEM_CHART_PRICE`이며, KIS 수집 결과만으로 `SUCCESS`, `PARTIAL_FAILED`, `FAILED`를 판단한다.
- KIS 일봉 수집은 provider 초당 제한을 넘지 않도록 종목별 호출 간격을 1.2초로 둔다. 제한 응답은 짧게 대기 후 재시도한다.
- 차트 조회는 `GET /api/v1/market/stocks/{stockCode}/history?from=YYYY-MM-DD&to=YYYY-MM-DD&limit=365`를 사용한다.
- 저장된 KRX row가 없을 때는 KIS 일봉 chart API를 호출해 해당 종목의 일봉을 저장하고 반환한다.
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
- 외국인 한도 예측은 주문 차단 조건이 아니다. `foreignLimitExceeded=true`는 BUY 주문이 체결되지 않을 수 있음을 프론트에서 미리 고지하기 위한 경고 신호이며, `orderable=false`는 거래정지 같은 시장 상태 차단에만 사용한다.
- KIS 실시간 체결가·호가 WebSocket에는 외국인 보유수량, 보유율, 한도소진율 필드가 없다. 외국인 한도 정보는 KRX Data Marketplace snapshot refresh와 Redis/in-memory cache로 공급한다.
- 제한 종목 예측은 장전 batch가 Redis/in-memory `ForeignOwnershipPredictionCache`에 선계산한다. 모바일 거래소의 `orderability/detail` 요청은 cache를 먼저 읽고, cache miss일 때만 Hannah-Montana-AI `POST /api/v1/market/foreign-ownership/predict`를 호출한다.
- Hannah 호출 실패, circuit open, 비정상 envelope 응답 시에는 OmniLens 내부 deterministic 시계열 엔진으로 fallback해 응답 계약과 주문 전 확인 흐름을 유지한다.
- 외국인 보유 일별 history는 `POST /api/v1/market/foreign-ownership/collect`, `POST /api/v1/market/foreign-ownership/backfill`, `ForeignOwnershipRefreshScheduler`가 `foreign_ownership_daily_snapshot`에 upsert한다. 기본 수집 대상은 현재 상장 외국인 취득한도 제한 32종목 allowlist이며, `stockCodes`를 명시한 수동 요청만 별도 종목을 조회한다.
- KIS 현재가는 전일 증분 snapshot 수집에 사용하지 않는다. 초기 1년+ 과거 백필과 이후 누락일 보강은 `KRX_SCRAPING_ENABLED=true`, `KRX_ID`, `KRX_PW`가 설정된 경우 KRX Data Marketplace 로그인 기반 `MDCSTAT03702` provider로 수행한다. provider가 비어 있으면 현재 snapshot을 과거 날짜로 복제하지 않는다.
- 새 전일 snapshot이 저장되면 스케줄러가 제한 종목 전체 history를 Hannah `POST /api/v1/market/foreign-ownership/model/retrain`으로 전송한다. Hannah는 quality gate를 통과한 모델만 promote하고 reload한다.
- 재학습 호출 이후 `FOREIGN_OWNERSHIP_PREDICTION_PRECOMPUTE_ENABLED=true`와 `FOREIGN_OWNERSHIP_PREDICTION_PRECOMPUTE_TRIGGER_AFTER_REFRESH=true`이면 제한 종목 금일 예측을 즉시 선계산해 cache에 저장한다. 따라서 장중 모바일 요청은 대부분 미리 계산된 결과만 조회한다.
- SELL 요청은 외국인 한도소진율이 100% 이상이어도 외국인 한도 경고를 만들지 않는다.
- KIS 실시간 체결 cache가 있으면 1호가 공백 패턴을 이용해 `priceLimitState=UPPER_LIMIT|LOWER_LIMIT|NORMAL`을 판단하고, 체결 상태 필드로 `viActive`, `singlePriceTrading`, `tradingHalted`를 계산한다. 실시간 상태 tick이 아직 없으면 `priceLimitState=UNKNOWN`, source `MARKET_STATUS_UNAVAILABLE`로 반환한다. 거래정지 상태가 활성화되면 주문 가능 여부는 `TRADING_HALTED`로 차단한다.

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

## 실시간 시세 수요 구독
- 인기 종목은 `KIS_REALTIME_STOCK_CODES` 또는 종목 마스터 상위 `KIS_REALTIME_STOCK_LIMIT` 기준으로 API 기동 시 KIS WebSocket에 선구독한다.
- 상세 화면처럼 인기 종목 외 종목이 필요하면 협력사는 `/ws/market/quotes`에 아래 메시지를 전송한다.

```json
{
  "type": "QUOTE_STREAM_SUBSCRIBE",
  "currency": "USD",
  "stockCodes": ["091990"]
}
```

- OmniLens는 지원 종목, 중복 구독, KIS 구독 한도를 확인한 뒤 같은 KIS WebSocket 세션에 추가 구독한다. 요청 직후 현재 REST snapshot도 같은 WebSocket 세션으로 재송신한다.

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
- OmniLens는 `stock_master`의 종목코드, 한글명, 영문명, 시장구분을 Hannah `POST /api/v1/market/global-peers/match`로 전달한다.
- Hannah 응답은 headline, summary, primary peer, 기존 후보 peer 목록, 속성별 comparisons, 국내 종목 자체의 key strengths, confidence, model version을 전달한다.
- 각 peer의 섹터, 산업, 사업모델, 규모 버킷, 시가총액, 매출, 영업이익, 순이익, 재무 데이터 출처, 재무 유사도, 매칭 근거 배열은 프론트 피어 설명 팝업에서 바로 사용할 수 있도록 응답에 보존한다.
- 정상 응답은 comparisons 1~3개와 key strengths 4개를 요구한다. dimension과 icon key allowlist 위반, 빈 필수 문구, 카드 개수 위반은 정상 결과로 전달하지 않는다.
- Hannah 장애 또는 circuit open 시 OmniLens는 anchor fallback을 사용한다. 알테오젠 `196170`은 `HALO` Halozyme Therapeutics fallback을 제공하되, fallback의 comparisons와 keyStrengths는 빈 배열이고 confidence는 LOW다.

## 한국 금융 고유어·전문용어 해설
- 모바일 거래소 또는 협력사 백엔드는 뉴스/공시 본문에서 사용자가 누른 한국 금융 고유어·전문용어를 `POST /api/v1/korean-financial-terms/explain`로 보낸다.
- OmniLens는 Hannah `POST /api/v1/korean-financial-terms/explain`를 호출하고, `displayMode=EXPLANATION`이면서 `cacheable=true`인 응답만 `korean_financial_term_explanation_cache`에 저장한다.
- 사전 hit 또는 충분한 근거가 있는 RAG 응답은 TTL 동안 재사용한다. 신뢰도 낮은 신조어는 `REVIEW_REQUIRED`로 반환하고 cache하지 않아 사람이 검수한 뒤 사전에 편입할 수 있게 한다.
- 모든 클릭은 `korean_financial_term_click_log`에 저장하고, `korean_financial_term_click_stats`에 누적 집계한다. 사용자와 세션 식별자는 원문 저장 없이 `OMNILENS_TERM_ANALYTICS_HASH_SALT`로 salted SHA-256 처리한다.
- 운영 비용 최적화는 집계 기반으로 수행한다. 클릭이 많은 용어는 사전/캐시로 고정하고, 낮은 빈도와 낮은 신뢰도 용어는 검수 queue로 남겨 불필요한 LLM 호출을 줄인다.
- 통계 확인은 `GET /api/v1/korean-financial-terms/stats`를 사용한다.

## 환율 provider
- 기본 provider는 Frankfurter REST 환율 endpoint인 `FRANKFURTER_BASE_URL`이다.
- 내부 캐시에는 `KRW -> 현지통화` 비율로 저장한다.
- 기본 refresh scheduler는 비활성화되어 있다.
- 활성화하면 `EXCHANGE_RATE_REFRESH_CURRENCIES`에 지정한 통화만 주기적으로 갱신한다.
- 한국수출입은행 환율 API는 레거시 provider로 제거됐다. 환율은 Frankfurter adapter와 cache를 기준으로 운영한다.
- DeepL, Papago, OpenAI 번역 경로와 비교 smoke는 제거됐다.

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
- 수동 모델 재학습은 `POST /api/v1/market/foreign-ownership/model/retrain`으로 실행한다. 이 API는 OmniLens DB의 제한 종목 전체 외국인 보유 history를 export해 Hannah에 전달한다.
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
HANNAH_AI_MAINTENANCE_TOKEN=<server-secret>
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
- 협력사별 key rotation은 bootstrap 운영 키로 `POST /api/v1/security/partners/{partnerId}/credentials/rotate`를 호출한다.
- rotation API는 서버가 새 256-bit API key를 생성해 응답에서 한 번만 반환하고, DB에는 SHA-256 hash만 저장한다.
- 같은 partner의 기존 활성 credential은 같은 트랜잭션에서 `active=false`로 전환되므로 기존 키는 즉시 폐기된다.
- DB credential로 인증된 협력사 요청은 rotation API를 호출할 수 없다.

```sql
INSERT INTO partner_api_credential (api_key_sha256, partner_id, active)
VALUES ('{sha256_hex_of_raw_api_key}', 'partner-a', TRUE);
```

```bash
curl -X POST http://localhost:8080/api/v1/security/partners/partner-a/credentials/rotate \
  -H "X-HANA-OMNILENS-API-KEY: ${BOOTSTRAP_API_KEY}"
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
- 없음. 신규 운영 보강은 `docs/ROADMAP.md`의 다음 milestone으로 추가한다.
