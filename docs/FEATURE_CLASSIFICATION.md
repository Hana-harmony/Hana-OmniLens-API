# 기능 분류와 레포 책임

## 1. 한국 주식 뉴스·공시 인텔리전스

| 기능 | 책임 | 상태 |
| --- | --- | --- |
| Naver News Search 기반 뉴스 발견 | Hana-OmniLens-API | Done |
| 허용 원문 기사 전문·이미지 수집 | Hana-OmniLens-API | Done |
| OpenDART 공시 목록·document 원문 수집 | Hana-OmniLens-API | Done |
| 인기 종목, 외국인 보유 제한 종목, watchlist 종목 주기 수집 | Hana-OmniLens-API | Done |
| 종목 매핑, 이벤트 분류, 감성, 중요도, 3줄 요약, 중복 키, confidence | Hannah-Montana-AI | Done |
| DeepL 제목·요약·전문 번역 | Hana-OmniLens-API | Done |
| 한국 금융 용어 설명과 번역 품질 메타데이터 | Hana-OmniLens-API, Hannah-Montana-AI | Done |
| 뉴스·공시 REST 목록/상세와 WebSocket 이벤트 | Hana-OmniLens-API | Done |
| 보유종목/watchlist 사용자 알림 매칭 | Stock-exchange-BE | Done |
| 종목 상세 K-News와 통합 알림함 | Stock-exchange-FE | Done |

## 2. 실시간 주식 정보 제공 및 주문 제한 필터링 시스템

| 기능 | 책임 | 상태 |
| --- | --- | --- |
| KIS/KRX 종목 마스터와 현재가 snapshot | Hana-OmniLens-API | Done |
| KIS 실시간 체결가·호가 수집과 `/ws/market/quotes` 송신 | Hana-OmniLens-API | Done |
| 지수, 호가, 과거 시세, 환율 적용 가격 제공 | Hana-OmniLens-API | Done |
| KRX 외국인 보유수량, 보유율, 한도소진율 snapshot cache | Hana-OmniLens-API | Done |
| 당일 외국인 한도소진율 예측 boundary | Hana-OmniLens-API, Hannah-Montana-AI | Done |
| VI, 단일가, 상·하한가, 거래정지 신호 | Hana-OmniLens-API | Done |
| 주문 가능 여부 API와 제한 사유 제공 | Hana-OmniLens-API | Done |
| FE용 quote/chart/orderability API와 WebSocket 재배포 | Stock-exchange-BE | Done |
| 시장 탭, 종목 상세, 차트, 주문 제한 UI | Stock-exchange-FE | Done |

## 경계

- Hana-OmniLens-API는 실제 주문 명령, 체결, 정산, 환전을 수행하지 않는다.
- AI 모델 학습과 추론 로직은 Hannah-Montana-AI 책임이다.
- 사용자별 알림 매칭, 푸시 발송, 알림함 저장은 Stock-exchange-BE 책임이다.
