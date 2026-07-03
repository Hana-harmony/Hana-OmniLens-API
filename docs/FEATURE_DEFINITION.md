# Hana Harmony 통합 기능정의서

이 문서는 `Hana-OmniLens-API`, `Hannah-Montana-AI`, `Stock-exchange-BE`, `Stock-exchange-FE`가 공유하는 최신 기능 기준이다. 현재 서비스 범위는 한국 주식 뉴스·공시 인텔리전스와 실시간 주식 정보/주문 제한 신호 제공으로 한정한다.

## 레포 책임

| 레포 | 책임 |
| --- | --- |
| `Hana-OmniLens-API` | KIS/KRX/환율/뉴스/공시/번역 데이터를 수집·정규화하고 협력사 B2B REST/WebSocket API로 제공 |
| `Hannah-Montana-AI` | 뉴스·공시 종목 매핑, 이벤트 분류, 감성, 중요도, 3줄 요약, 중복 제거, confidence 산출 |
| `Stock-exchange-BE` | 현지 사용자, watchlist, 보유종목, 자체 mock ledger, 알림 매칭, FE용 REST/WebSocket API 제공 |
| `Stock-exchange-FE` | Flutter iOS/Android 기반 영어 MTS 앱 화면 제공 |

## 핵심 기능 01. 한국 주식 뉴스·공시 인텔리전스

목표는 해외 사용자가 한국어 뉴스와 공시를 종목 기준으로 이해할 수 있게 만드는 것이다.

| 기능 | 담당 | 내용 |
| --- | --- | --- |
| 뉴스 발견 | `Hana-OmniLens-API` | Naver News Search로 시장·종목 뉴스 제목, snippet, 언론사, 발행시각, 원문 링크 수집 |
| 공시 발견 | `Hana-OmniLens-API` | OpenDART 공시 목록과 접수번호 기반 document 원문 수집 |
| 수집 대상 관리 | `Hana-OmniLens-API` | 인기 종목, 외국인 보유 제한 종목, 협력사 watchlist 종목을 주기 수집 대상에 포함 |
| 전문 정제 | `Hana-OmniLens-API` | 허용된 원문 기사와 OpenDART document에서 전문, 대표 이미지, 원문 링크 저장 |
| AI 분석 | `Hannah-Montana-AI` | 종목 매핑, 이벤트 분류, 감성, 중요도, What/Why/Impact 3줄 요약, 중복 키, confidence 생성 |
| 번역 | `Hana-OmniLens-API` | DeepL로 제목·요약·전문을 번역하고 실패 시 원문 fallback과 cache 상태 제공 |
| 용어 설명 | `Hana-OmniLens-API`, `Hannah-Montana-AI` | 한국 금융 용어 설명, evidence, confidence, 번역 품질 플래그 제공 |
| 전송 | `Hana-OmniLens-API` | 뉴스·공시 목록/상세 REST API와 협력사/종목 WebSocket 이벤트 제공 |
| 사용자 매칭 | `Stock-exchange-BE` | 보유종목/watchlist 사용자에게 알림함 저장과 push fanout 수행 |
| 화면 | `Stock-exchange-FE` | 종목 상세 K-News, 통합 알림함, 원문 링크, 번역 요약 표시 |

흐름:

`Naver/OpenDART -> Hana 수집·저장 -> Hannah 분석 -> DeepL 번역 -> Hana REST/WebSocket -> Stock-exchange-BE 매칭 -> Flutter 앱`

## 핵심 기능 02. 실시간 주식 정보 제공 및 주문 제한 필터링 시스템

목표는 협력사 앱이 한국 주식 시세와 주문 전 제한 신호를 안정적으로 표시할 수 있게 하는 것이다.

| 기능 | 담당 | 내용 |
| --- | --- | --- |
| 종목 마스터 | `Hana-OmniLens-API` | KIS/KRX 기반 종목코드, 국문명, 영문명, 시장, 기준가, 발행주식수 관리 |
| 실시간 시세 | `Hana-OmniLens-API` | KIS 실시간 체결가·호가를 수집해 cache와 `/ws/market/quotes`로 제공 |
| REST snapshot | `Hana-OmniLens-API` | 단건, 다건, 전체 종목, 지수, 호가, 환율 적용 현재가 제공 |
| 과거 시세 | `Hana-OmniLens-API` | KRX 일별 OHLCV를 저장하고 차트 API로 제공 |
| 환율 | `Hana-OmniLens-API` | KRW 기준값과 USD 등 현지통화 환산 가격, 기준시각, stale flag 제공 |
| 외국인 한도 | `Hana-OmniLens-API` | KRX snapshot 기반 보유수량, 보유율, 한도수량, 한도소진율 제공 |
| 예측 boundary | `Hana-OmniLens-API`, `Hannah-Montana-AI` | 당일 외국인 한도소진율 min/base/max와 modelVersion, confidence 제공 |
| 주문 제한 신호 | `Hana-OmniLens-API` | VI, 단일가, 상·하한가, 거래정지, 외국인 한도 경고 사유 반환 |
| FE용 재가공 | `Stock-exchange-BE` | Hana snapshot/stream을 watchlist, 보유종목, 종목 상세, 주문 화면 API로 전달 |
| 화면 | `Stock-exchange-FE` | 시장 탭, 종목 상세, 차트, 주문 제한 배지와 경고 표시 |

흐름:

`KIS/KRX/FX -> Hana cache/DB -> Hana REST/WebSocket -> Stock-exchange-BE cache/API/WebSocket -> Flutter 앱`

주문 제한 API는 실제 주문 명령이 아니다. 협력사 시스템이 최종 주문 차단, 체결, 잔고, 정산을 책임지고, Hana는 판단 근거와 제한 신호만 제공한다.

## 보안 기준

- 협력사 서버 간 요청은 `X-HANA-OMNILENS-API-KEY`로 인증한다.
- API key 원문은 저장하지 않고 SHA-256 hash, rate limit, 감사 로그를 적용한다.
- 운영 요청 서명과 mTLS는 설정으로 강제할 수 있다.
- `Stock-exchange-FE`에는 Hana/KIS/KRX/Naver/OpenDART/DeepL credential을 두지 않는다.
- 외부 provider credential은 환경 변수 또는 Secret Manager로만 주입한다.
