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
| KIS 실시간 체결가·호가 수집 | `Hana-OmniLens-API` | KIS WebSocket 원천 데이터를 구독하고 장중 캐시에 반영 | Planned |
| 실시간 quote REST snapshot | `Hana-OmniLens-API` | 단건, 다건, 전체 종목 현재가 snapshot 제공 | Planned |
| market quote WebSocket stream | `Hana-OmniLens-API` | 협력사에 실시간 tick/delta/batch quote 송신 | Planned |
| FE용 quote REST API | `Stock-exchange-BE` | 전체/시장별/watchlist/보유종목/단건 snapshot 제공 | Planned |
| FE용 quote WebSocket | `Stock-exchange-BE` | Flutter 앱에 사용자 컨텍스트별 실시간 tick 재배포 | Planned |
| 실시간 시세 화면 | `Stock-exchange-FE` | 전체/시장별/watchlist/보유종목 시세 목록과 종목 상세 표시 | Planned |

실시간 흐름:

`KIS WebSocket -> Hana-OmniLens-API cache -> Hana REST snapshot/WebSocket stream -> Stock-exchange-BE cache/API/WebSocket -> Flutter iOS/Android 앱`

REST snapshot은 초기 로딩, 전체 목록, 검색, 새로고침, WebSocket 재연결 복구에 사용한다. WebSocket은 장중 가격, 호가, 등락률, VI/상·하한가 상태 변화처럼 즉시 움직여야 하는 데이터에 사용한다.

### 원화와 현지통화 가격

| 기능 | 담당 | 내용 | 상태 |
| --- | --- | --- | --- |
| 환율 수집과 캐시 | `Hana-OmniLens-API` | 한국수출입은행 또는 승인된 FX provider의 실시간/최신 환율 수집 | Planned |
| KRW/현지통화 가격 동시 제공 | `Hana-OmniLens-API` | quote snapshot과 WebSocket tick에 원화 가격과 환율 적용 현지통화 가격 포함 | Planned |
| 환율 상태 전달 | `Stock-exchange-BE` | `fxRate`, `fxRateTime`, `fxRateSource`, stale flag를 FE에 전달 | Planned |
| 원화/현지통화 표시 | `Stock-exchange-FE` | 현재가 KRW, 현지통화 환산 가격, 적용 환율 기준시각 표시 | Planned |

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
| KRX 과거 시세 수집 | `Hana-OmniLens-API` | 모든 국내 주식 과거 시세를 KRX에서 수집 | Planned |
| 과거 시세 DB 저장 | `Hana-OmniLens-API` | 종목코드, 거래일, OHLCV, 거래대금, 수정주가 기준으로 정규화 저장 | Planned |
| 과거 시세 REST API | `Hana-OmniLens-API` | `/api/v1/market/stocks/{stockCode}/history` 형태의 chart API 제공 | Planned |
| FE용 차트 API | `Stock-exchange-BE` | Hana의 KRX 기반 과거 시세 API를 호출해 앱 차트 응답으로 재가공 | Planned |
| 과거 시세 차트 | `Stock-exchange-FE` | Flutter 앱에서 일봉/기간 차트 표시 | Planned |

과거 시세 흐름:

`KRX historical data -> Hana-OmniLens-API DB -> Hana history REST API -> Stock-exchange-BE chart API -> Flutter iOS/Android 앱`

`Stock-exchange-BE`는 KRX를 직접 호출하지 않는다.

### 외국인 한도, VI, 상·하한가

| 기능 | 담당 | 내용 | 상태 |
| --- | --- | --- | --- |
| KIS 종목 마스터 적재 | `Hana-OmniLens-API` | 종목코드, 국문명, 영문명, 시장, 발행주식수, 당일 상·하한가 기준가격 적재 | Planned |
| KRX 외국인 보유율 캐시 | `Hana-OmniLens-API` | 전일 외국인 보유수량, 보유율, 한도소진율 저장 | Planned |
| 당일 외국인 지분율 예측 boundary | `Hana-OmniLens-API`, Predict Engine | 당일 예상 지분율 min/max 산출 | Planned |
| VI/단일가/상·하한가 상태 | `Hana-OmniLens-API` | KIS 실시간 패킷 기반 상태 감지 | Planned |
| 주문 가능 여부 계산 | `Stock-exchange-BE` | 외국인 한도, VI, 가격제한 상태 기반 모의 주문 경고 계산 | Planned |
| 주문/종목 화면 표시 | `Stock-exchange-FE` | 외국인 한도 게이지, VI 배지, 상·하한가 배지, 주문 제한 팝업 | Planned |

### 현지 거래소 자체 모의 거래

| 기능 | 담당 | 내용 | 상태 |
| --- | --- | --- | --- |
| 간편 회원가입 | `Stock-exchange-BE`, `Stock-exchange-FE` | 아이디와 비밀번호만 받아 현지 사용자 계정 생성 | Planned |
| USD 계좌 자동 생성 | `Stock-exchange-BE` | 회원가입 시 mock USD cash account와 portfolio ledger 생성 | Planned |
| 달러 충전 | `Stock-exchange-BE`, `Stock-exchange-FE` | 사용자가 금액을 입력하면 실제 결제 없이 mock USD 잔고 증가 | Planned |
| 자체 모의 매수·매도 | `Stock-exchange-BE` | KIS 모의투자 API가 아니라 BE 내부 원장으로 주문, 체결, 평균단가, 잔고 처리 | Planned |
| 주문 제한 검증 | `Stock-exchange-BE` | Hana orderability, 외국인 한도, VI, 상·하한가 상태로 모의 주문 가능 여부 계산 | Planned |
| 거래 화면 | `Stock-exchange-FE` | 영어 UI에서 USD 잔고, KRW/USD 시세, 예상 체결금액, mock trade 여부 표시 | Planned |

이 거래 기능은 실제 한국 주식 매수·매도나 KIS 모의투자 계좌 주문이 아니다. 사용자가 체험하는 현지 거래소 내부의 가짜 원장 거래이며, 실현손익과 매도 내역은 세무 환급/선지급 기능의 입력 데이터로 연결한다.

## 2. 한국 주식 뉴스·공시 인텔리전스

| 기능 | 담당 | 내용 | 상태 |
| --- | --- | --- | --- |
| 뉴스 수집 | `Hana-OmniLens-API` | Naver News Search API로 한국어 뉴스 제목, snippet, 원문 링크, 언론사, 발행시각 수집 | Planned |
| 공시 수집 | `Hana-OmniLens-API` | OpenDART로 공시 제목, 유형, 제출시각, 원문 링크 수집 | Planned |
| AI 분석 | `Hannah-Montana-AI` | 종목 매핑, 이벤트 태그, 감성, 중요도, 요약, 중복 제거 키 생성 | Partial |
| 번역 | `Hana-OmniLens-API` | Papago/DeepL adapter로 제목과 요약을 현지 언어로 번역 | Planned |
| 알림 이벤트 송신 | `Hana-OmniLens-API` | 협력사/종목 topic으로 WebSocket 이벤트 송신 | Partial |
| 이벤트 수신과 대상자 매칭 | `Stock-exchange-BE` | 보유종목/watchlist 사용자와 매칭 후 알림함 저장 및 푸시 발송 | Planned |
| K-News와 알림함 UI | `Stock-exchange-FE` | 번역 제목, 요약, 태그, 감성, 중요도, 원문 링크, 통합 알림함 표시 | Planned |

뉴스·공시 흐름:

`Naver/OpenDART -> Hana-OmniLens-API -> Hannah-Montana-AI analysis -> Papago/DeepL translation -> Hana WebSocket alert -> Stock-exchange-BE matching/push -> Flutter iOS/Android 앱`

## 3. 최종투자자별 세무 전산화와 환급금 선지급

세무 기능은 금융/규제 검토가 필요한 planned 영역이다. 실제 운영 전 법무, 컴플라이언스, 보안 검토가 필수다.

| 기능 | 담당 | 내용 | 상태 |
| --- | --- | --- | --- |
| 세무 서류 업로드 접수 | `Stock-exchange-BE`, `Stock-exchange-FE` | 거주자증명서, 제한세율신청서 PDF/JPG 업로드 | Planned |
| 거래원장/sub-ledger 매칭 | `Stock-exchange-BE` | 현지 사용자 mock 거래원장, 매도 실현손익, 세무 서류 metadata 매칭 | Planned |
| OCR/위변조 검증 | `Hannah-Montana-AI` 또는 별도 OCR service | 텍스트 추출, 위변조/딥페이크 1차 검증 | Planned, ADR 필요 |
| 조세조약 케이스 판정 | `Hana-OmniLens-API` | 한국·홍콩 조세조약 케이스 분류 | Planned |
| 환급금 선지급 산정 | `Hana-OmniLens-API` | 원천징수액, 제한세율 차액, 선지급 수수료, 환수 상태 산정 | Planned |
| 환급 상태 동기화 | `Stock-exchange-BE` | Hana 세무 상태 API를 사용자별 상태로 저장/전달 | Planned |
| 세무 화면 | `Stock-exchange-FE` | 서류 업로드, 상태 타임라인, 정산 상세, 환급 신청, 선지급 완료/리스크 고지 | Planned |

세무 흐름:

`Flutter 앱 서류 업로드 -> Stock-exchange-BE 저장/metadata -> Hana tax status/OCR/case/refund API -> Stock-exchange-BE 사용자 상태 동기화 -> Flutter iOS/Android 앱 표시`

## Stock-exchange-FE 플랫폼 기준

`Stock-exchange-FE`는 Flutter 기반 iOS/Android 모바일 MTS 앱이다.

| 플랫폼 | 상태 |
| --- | --- |
| Flutter iOS 앱 | Planned |
| Flutter Android 앱 | Planned |
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
| Hana market quote 단건 API | 목 데이터 기반 Partial |
| Hana alert WebSocket 송신 | Partial |
| Hana API key 인증 | Partial |
| Hannah-Montana-AI 분석 API | Partial |
| KIS/KRX/FX/Naver/OpenDART/Papago/DeepL 실연동 | Planned |
| Stock-exchange-BE 실제 서버 구현 | Planned |
| Stock-exchange-FE Flutter 앱 구현 | Planned |
| 세무/OCR/환급 선지급 구현 | Planned |
