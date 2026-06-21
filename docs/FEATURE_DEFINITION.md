# Hana Harmony 통합 기능정의서

이 문서는 `Hana-OmniLens-API`, `Hannah-Montana-AI`, `Stock-exchange-BE`, `Stock-exchange-FE`의 최신 기획을 하나로 정리한 기준 문서다.

## 전체 서비스 개요

해외/현지 거래소 사용자가 모든 한국 상장주식에 접근할 수 있도록 한국 주식 실시간 시세, 과거 시세, 매매제한 판단 데이터, 뉴스·공시 인텔리전스, 세무 환급 상태를 제공한다.

현지 거래소 사용자는 영어를 기본 언어로 사용하고, 앱 표시·충전·모의 주문 기준 화폐는 USD를 기본으로 한다. 원화 시세는 항상 기준값으로 유지하되, 사용자 화면과 모의 거래 원장은 실시간 또는 최신 환율이 적용된 USD 가격을 함께 사용한다.

실제 주문 실행, 체결, 정산, 환전은 이 프로젝트 범위가 아니다. `Stock-exchange-BE`는 KIS 모의투자 API를 사용하지 않고 자체 mock ledger로 가짜 매수·매도, USD 충전, 보유수량, 실현손익을 관리한다.

## 레포별 책임

| 레포 | 책임 |
| --- | --- |
| `Hana-OmniLens-API` | KIS/KRX/환율/뉴스/공시/번역/세무 상태를 수집·가공해 협력사 B2B API로 제공 |
| `Hannah-Montana-AI` | 뉴스·공시 종목 매핑, 이벤트 분류, 감성, 중요도, 요약, 중복 제거 키 생성 |
| `Stock-exchange-BE` | 영어권 현지 사용자, USD 계좌, watchlist, 자체 모의 매수·매도 원장, 알림 매칭, FE용 REST/WebSocket API 제공 |
| `Stock-exchange-FE` | Flutter 기반 iOS/Android 영어 MTS 앱 화면 제공 |

## 1. 한국 주식 시세와 주문 지원

### 실시간 시세

| 기능 | 담당 | 내용 | 상태 |
| --- | --- | --- | --- |
| KIS 실시간 체결가·호가 수집 | `Hana-OmniLens-API` | KIS WebSocket 원천 데이터를 구독하고 장중 캐시에 반영 | Done |
| 실시간 quote REST snapshot | `Hana-OmniLens-API` | 단건, 다건, 전체 종목 현재가 snapshot 제공 | Done |
| market quote WebSocket stream | `Hana-OmniLens-API` | 협력사에 실시간 tick/replay quote 송신 | Done |
| FE용 quote REST API | `Stock-exchange-BE` | 전체/시장별/watchlist/보유종목/단건 snapshot 제공 | Done |
| FE용 quote WebSocket | `Stock-exchange-BE` | Flutter 앱에 사용자 컨텍스트별 실시간 tick 재배포 | Done |
| 실시간 시세 화면 | `Stock-exchange-FE` | 전체/시장별/watchlist/보유종목 시세 목록과 종목 상세 표시 | Done |

실시간 흐름:

`KIS WebSocket -> Hana-OmniLens-API cache -> Hana REST snapshot/WebSocket stream -> Stock-exchange-BE cache/API/WebSocket -> Flutter iOS/Android 앱`

REST snapshot은 초기 로딩, 전체 목록, 검색, 새로고침, WebSocket 재연결 복구에 사용한다. WebSocket은 장중 가격, 호가, 등락률, VI/상·하한가 상태 변화처럼 즉시 움직여야 하는 데이터에 사용한다.

### 원화와 현지통화 가격

| 기능 | 담당 | 내용 | 상태 |
| --- | --- | --- | --- |
| 환율 수집과 캐시 | `Hana-OmniLens-API` | Frankfurter FX provider의 최신 환율 수집 | Done |
| KRW/현지통화 가격 동시 제공 | `Hana-OmniLens-API` | quote snapshot과 WebSocket tick에 원화 가격과 환율 적용 현지통화 가격 포함 | Done |
| 환율 상태 전달 | `Hana-OmniLens-API`, `Stock-exchange-BE` | `fxRate`, `fxRateTime`, `fxRateSource`, stale flag를 FE에 전달 | Done |
| 원화/현지통화 표시 | `Stock-exchange-FE` | 현재가 KRW, 현지통화 환산 가격, 적용 환율 기준시각 표시 | Done |

현지 거래소 기준 `localCurrency`의 기본값은 `USD`다. 향후 다른 현지 거래소를 추가할 경우 partner 설정으로 통화를 바꿀 수 있으나, 현재 하네스와 화면 기획은 영어 사용자와 USD 계좌를 기준으로 둔다.

표준 quote 필드:

- `currentPriceKrw`
- `executionPriceKrw`
- `baseCurrency`
- `localCurrency`
- `fxRate`
- `fxRateTime`
- `fxRateSource`
- `localCurrencyPrice`
- `localCurrencyExecutionPrice`
- `fxStale`

### 과거 시세

| 기능 | 담당 | 내용 | 상태 |
| --- | --- | --- | --- |
| KRX 과거 시세 수집 | `Hana-OmniLens-API` | KOSPI/KOSDAQ/KONEX 일별매매정보를 KRX Open API에서 수집 | Done |
| 과거 시세 DB 저장 | `Hana-OmniLens-API` | 종목코드, 거래일, OHLCV, 거래대금, 수정주가 기준으로 정규화 저장 | Done |
| 과거 시세 REST API | `Hana-OmniLens-API` | `/api/v1/market/stocks/{stockCode}/history` 형태의 chart API 제공 | Done |
| Market REST 공동 응답 형식 | `Hana-OmniLens-API` | 종목 단건, 검색, quote, orderbook, orderability, history, 환율 갱신을 `ApiResponse` envelope으로 제공 | Done |
| FE용 차트 API | `Stock-exchange-BE` | Hana의 KRX 기반 과거 시세 API를 호출해 앱 차트 응답으로 재가공 | Done |
| 과거 시세 차트 | `Stock-exchange-FE` | Flutter 앱에서 일봉/기간 차트 표시 | Done |

과거 시세 흐름:

`KRX historical data -> Hana-OmniLens-API DB -> Hana history REST API -> Stock-exchange-BE chart API -> Flutter iOS/Android 앱`

`Stock-exchange-BE`는 KRX를 직접 호출하지 않는다.

### 외국인 한도, VI, 상·하한가

| 기능 | 담당 | 내용 | 상태 |
| --- | --- | --- | --- |
| KIS 종목 마스터 적재 | `Hana-OmniLens-API` | 종목코드, 국문명, 영문명, 시장, 발행주식수, 당일 상·하한가 기준가격 적재 | Done |
| KIS 외국인 보유량 snapshot cache | `Hana-OmniLens-API` | KIS 현재가 REST 응답의 `frgn_hldn_qty`, `hts_frgn_ehrt`, `lstn_stcn`으로 외국인 보유수량, 보유율, 한도소진율 저장 | Done |
| 주문 전 외국인 한도소진율 예측 boundary | `Hana-OmniLens-API` | KIS 외국인보유량 cache, 요청 수량, KIS 실시간 체결 누적 거래량으로 BUY 예상 한도소진율 min/base/max 산출 | Done |
| 당일 외국인 한도 사전 차단 engine | `Hana-OmniLens-API`, Predict Engine | snapshot 기준 한도소진율, 주문 영향도, 실시간 누적 거래량 기반 불확실성을 반영한다. 외국인 보유량 다일자 시계열 학습 모델은 범위 밖이다. | Done |
| 상·하한가 상태 | `Hana-OmniLens-API` | KIS 실시간 체결 1호가 공백 패턴 기반 `UPPER_LIMIT`/`LOWER_LIMIT` 감지 | Done |
| VI/단일가/거래정지 상태 | `Hana-OmniLens-API` | KIS 실시간 체결 상태 필드 기반 `viActive`, `singlePriceTrading`, `tradingHalted` 감지 | Done |
| 주문 가능 여부 boundary 제공 | `Hana-OmniLens-API` | `/api/v1/market/stocks/{stockCode}/orderability`로 외국인 한도, VI, 가격제한, 거래정지 상태 제공 | Done |
| 주문 가능 여부 계산 | `Stock-exchange-BE` | Hana orderability 기반 모의 주문 경고와 차단 계산 | Done |
| 주문/종목 화면 표시 | `Stock-exchange-FE` | 외국인 한도 게이지, VI 배지, 상·하한가 배지, 주문 제한 팝업 | Done |

### 현지 거래소 자체 모의 거래

| 기능 | 담당 | 내용 | 상태 |
| --- | --- | --- | --- |
| 간편 회원가입 | `Stock-exchange-BE`, `Stock-exchange-FE` | 아이디와 비밀번호만 받아 현지 사용자 계정 생성 | Done |
| USD 계좌 자동 생성 | `Stock-exchange-BE` | 회원가입 시 mock USD cash account와 portfolio ledger 생성 | Done |
| 달러 충전 | `Stock-exchange-BE`, `Stock-exchange-FE` | 사용자가 금액을 입력하면 실제 결제 없이 mock USD 잔고 증가 | Done |
| 자체 모의 매수·매도 | `Stock-exchange-BE` | KIS 모의투자 API가 아니라 BE 내부 원장으로 주문, 체결, 평균단가, 잔고 처리 | Done |
| 주문 제한 검증 | `Stock-exchange-BE` | Hana orderability, 외국인 한도, VI, 상·하한가 상태로 모의 주문 가능 여부 계산 | Done |
| 거래 화면 | `Stock-exchange-FE` | 영어 UI에서 USD 잔고, KRW/USD 시세, 예상 체결금액, mock trade 여부 표시 | Done |

이 거래 기능은 실제 한국 주식 매수·매도나 KIS 모의투자 계좌 주문이 아니다. 사용자가 체험하는 현지 거래소 내부의 가짜 원장 거래이며, 실현손익과 매도 내역은 세무 환급/선지급 기능의 입력 데이터로 연결한다.

## 2. 한국 주식 뉴스·공시 인텔리전스

| 기능 | 담당 | 내용 | 상태 |
| --- | --- | --- | --- |
| 뉴스 수집 | `Hana-OmniLens-API` | Naver News Search API로 한국어 뉴스 제목, snippet, 원문 링크, 언론사, 발행시각 수집 | Done |
| 공시 수집 | `Hana-OmniLens-API` | OpenDART로 공시 제목, 유형, 제출시각, 원문 링크 수집 | Done |
| AI 분석 | `Hannah-Montana-AI` | 종목 매핑, 이벤트 태그, 감성, 중요도, 요약, 중복 제거 키 생성, 금융 용어 normalization, 번역 품질 플래그, confidence 생성 | Done: audited gold readiness pass, 사람 검수 gold 확장은 운영 품질 관리 |
| 번역 | `Hana-OmniLens-API` | DeepL adapter로 제목을 영어로 번역하고 장애 시 원문 fallback | Done |
| 알림 이벤트 송신 | `Hana-OmniLens-API` | 협력사/종목 topic으로 WebSocket 이벤트 송신 | Done |
| Alert REST 공동 응답 형식 | `Hana-OmniLens-API` | watchlist, 단건 분석 발행, 수집 발행, 수동 이벤트 발행을 `ApiResponse` envelope으로 제공 | Done |
| 이벤트 수신과 대상자 매칭 | `Stock-exchange-BE` | 보유종목/watchlist 사용자와 매칭 후 알림함 저장 및 푸시 발송 | Done |
| K-News와 알림함 UI | `Stock-exchange-FE` | 번역 제목, 요약, 태그, 감성, 중요도, 원문 링크, 통합 알림함, push delivery timeline 표시 | Done |

뉴스·공시 흐름:

`Naver/OpenDART -> Hana-OmniLens-API -> Hannah-Montana-AI analysis/confidence metadata -> DeepL translation -> Hana WebSocket alert -> Stock-exchange-BE matching/push -> Flutter iOS/Android 앱`

현재 v1 완료 범위는 제목/snippet 중심의 watchlist 알림이다. v2 보강 범위는 모든 국내 종목을 대상으로 새 뉴스·공시를 탐지하고, 전문 기반 분석 결과를 저장·조회·푸시할 수 있는 서비스 계약이다.

| v2 보강 기능 | 담당 | 내용 | 상태 |
| --- | --- | --- | --- |
| 전체 종목 수집 스케줄러 | `Hana-OmniLens-API` | `stock_master` 전체 universe를 shard로 나누어 Naver News Search 발견 데이터와 OpenDART 공시를 주기 수집 | Planned |
| 전문·이미지 수집 계약 | `Hana-OmniLens-API` | Naver News Search는 발견용으로만 사용하고, 기사 전문과 이미지 URL은 라이선스/robots/약관상 허용된 provider 또는 공시 원문에서만 수집 | Planned |
| 영속 뉴스 이벤트 저장소 | `Hana-OmniLens-API` | alert event 원문 JSON과 stock/source/duplicate/cluster metadata를 DB에 저장 | Done |
| 전문 기반 AI 분석 | `Hannah-Montana-AI` | 제목/snippet/전문을 함께 입력받아 종목 매핑, 이벤트 태그, 감성, 중요도, What/Why/Impact 3줄 요약, 중복 키를 생성 | Planned |
| 전문 번역 | `Hana-OmniLens-API` | DeepL adapter로 제목, What/Why/Impact 요약, 전문을 chunk 단위로 번역하고 원문 fallback/cache를 제공 | Planned |
| 뉴스 REST 목록·상세 | `Hana-OmniLens-API` | `GET /api/v1/alerts/stocks/{stockCode}/events`, `GET /api/v1/alerts/events/{alertId}`로 저장 이벤트 조회 | Done |
| 뉴스 WebSocket 이벤트 | `Hana-OmniLens-API` | 저장된 v2 필드 포함 이벤트를 협력사/종목 topic으로 송신 | Done |
| 사용자 push fanout | `Stock-exchange-BE` | 수신 이벤트를 보유종목/watchlist 사용자와 매칭해 알림함·push delivery로 fanout | Planned |

v2 목표 흐름:

`전체 종목 shard -> Naver 발견/OpenDART 공시 -> 허용 provider 전문·이미지 수집 -> DB dedupe/cluster -> Hannah 전문 분석 -> DeepL 제목·요약·전문 번역 -> Hana REST/WebSocket -> Stock-exchange-BE watchlist/holding fanout -> Flutter 종목 상세 News 탭/알림함`

## 3. 최종투자자별 세무 전산화와 환급금 선지급

세무 기능은 금융/규제 검토가 필요한 planned 영역이다. 실제 운영 전 법무, 컴플라이언스, 보안 검토가 필수다.

| 기능 | 담당 | 내용 | 상태 |
| --- | --- | --- | --- |
| 세무 서류 업로드 접수 | `Stock-exchange-BE`, `Stock-exchange-FE` | 거주자증명서, 제한세율신청서 metadata/multipart 업로드 | Done for metadata upload, native file picker hardening remains |
| 거래원장/sub-ledger 매칭 | `Stock-exchange-BE` | 현지 사용자 mock 거래원장, 매도 실현손익, 세무 서류 metadata 매칭 | Done |
| OCR/위변조 검증 | `Hannah-Montana-AI` | 외부 OCR 결과와 위변조 signal 기반 `VERIFIED`/`PENDING`/`REJECTED` 1차 검증 | Done |
| 조세조약 케이스 판정 | `Hana-OmniLens-API` | 한국·미국 상장주식 장내거래, 25% 미만 지분율, 필수 서류 검증 기반 CASE_01 분류 | Done |
| 환급금 선지급 상태 sync | `Hana-OmniLens-API` | 현지 거래소 tax case의 예상 환급액, 선지급 요청/가능 여부 기반 `NO_REFUNDABLE_PROFIT`, `REFUND_APPROVED`, `ADVANCE_PAID`, `RECAPTURE_RISK` 상태 반환 | Done |
| 분기별 경정청구 배치 상태 조회 | `Hana-OmniLens-API` | `GET /api/v1/tax/rectification-batches/{taxYear}/quarters/{quarter}`로 batch window, 진행 상태, case count, next action 반환 | Done |
| 환급 상태 동기화 | `Stock-exchange-BE` | Hana 세무 상태 API를 사용자별 상태로 저장/전달 | Done |
| 세무 화면 | `Stock-exchange-FE` | 서류 업로드, 상태 타임라인, 정산 상세, 환급 신청, 선지급 완료/리스크 고지 | Done |

세무 흐름:

`Flutter 앱 서류 업로드 -> Stock-exchange-BE 저장/metadata -> Hana tax status/OCR/case/refund API -> Stock-exchange-BE 사용자 상태 동기화 -> Flutter iOS/Android 앱 표시`

## Stock-exchange-FE 플랫폼 기준

`Stock-exchange-FE`는 Flutter 기반 iOS/Android 모바일 MTS 앱이다.

| 플랫폼 | 상태 |
| --- | --- |
| Flutter iOS 앱 | Done |
| Flutter Android 앱 | Done |
| Flutter Web 운영 서비스 | Out of scope |

모바일 앱은 `Stock-exchange-BE` API와 WebSocket만 호출한다. `Hana-OmniLens-API`, `Hannah-Montana-AI`, KIS, KRX, FX provider는 모바일 앱에서 직접 호출하지 않는다.

## 보안과 인증 기준

- 협력사 서버 간 요청은 `X-HANA-OMNILENS-API-KEY`를 사용한다.
- API key 원문은 저장하지 않고 SHA-256 해시 비교를 기본으로 한다.
- `Stock-exchange-FE`에는 Hana API key, KIS/KRX/FX credential, push provider token을 두지 않는다.
- `Hannah-Montana-AI`는 협력사 API key를 받지 않고 내부 네트워크로 격리한다.
- 세무 서류, 거래원장, 환급 상태는 개인정보/민감 금융정보로 보고 최소 저장, 마스킹, 감사 로그를 적용한다.

## 현재 구현 상태 요약

| 영역 | 현재 상태 |
| --- | --- |
| Hana market quote 단건·다건·전체 snapshot API | KIS/공공데이터/캐시/provider 기반 Done |
| Hana orderability boundary API | 외국인 한도소진율 min/base/max 예측, 상·하한가, VI, 단일가, 거래정지 상태 Done |
| Hana market quote WebSocket stream | KIS tick 기반 raw WebSocket Done |
| Hana alert WebSocket 송신 | Done: v2 필드 포함 DB 저장 후 REST/WebSocket 제공 |
| Hana API key 인증 | Done: SHA-256 registry, rate limit, HMAC signature, nonce replay 방어, WebSocket handshake 보호 |
| Hannah-Montana-AI 분석 API | Done for title/snippet v1: audited gold readiness pass, full-content What/Why/Impact v2 Planned |
| KIS/KRX/FX/Naver/OpenDART/DeepL 실연동 | Done for adapter/contracts and env injection, KRX Open API는 서비스별 권한이 있는 `AUTH_KEY` 필요 |
| Stock-exchange-BE 실제 서버 구현 | Spring Boot 기반 Done, FCM/APNS/Web Push 실발송 경계 Done |
| Stock-exchange-FE Flutter 앱 구현 | Flutter iOS/Android 앱 기반 Done, M5 품질 하드닝 진행 |
| 세무/OCR/환급 선지급 구현 | Hana tax case classification/status sync/quarterly batch status Done, AI OCR/위변조 1차 검증 Done, 법무 최종판정/실제 지급은 Planned |

외부 provider credential은 저장소에 두지 않으며 `application-local.yml`, 배포 환경 변수, 또는 Secret Manager로만 주입한다. 따라서 위 `Done`은 KIS REST/WebSocket, KIS 현재가 기반 외국인 보유량 snapshot, KRX Open API history, FX refresh, Naver/OpenDART 수집, DeepL 번역 adapter와 계약 테스트가 구현되어 있다는 의미이고, KRX는 서비스 이용 신청이 완료된 인증키로 smoke validation을 별도로 확인한다. KIS 실시간 체결가·호가 WebSocket에는 외국인 보유수량, 보유율, 한도소진율 필드가 없으므로 외국인 한도 정보는 KIS 현재가 REST snapshot과 cache로 공급한다.
