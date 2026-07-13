# 기능 분류와 레포 책임

## 1. 한국 주식 뉴스·공시 인텔리전스

| 기능 | 책임 | 상태 |
| --- | --- | --- |
| Naver News Search 기반 뉴스 발견 | Hana-OmniLens-API | Done |
| 허용 원문 기사 전문·이미지 수집 | Hana-OmniLens-API | Done |
| OpenDART 공시 목록·document 원문 수집 | Hana-OmniLens-API | Done |
| 인기 종목, 외국인 보유 제한 종목, watchlist 종목 주기 수집 | Hana-OmniLens-API | Done |
| 종목 매핑, 이벤트 분류, 감성, 중요도, 3줄 요약, 중복 키, confidence | Hannah-Montana-AI | Done |
| Hannah Qwen3 제목·요약·전문 번역 | Hana-OmniLens-API, Hannah-Montana-AI | Done |
| 한국 금융 고유어·전문용어 해설, 번역 품질 메타데이터, 용어 클릭 통계 | Hana-OmniLens-API, Hannah-Montana-AI | Done |
| 시장 뉴스 목록, 상세, 트렌딩, 수동 수집 API | Hana-OmniLens-API | Done |
| 협력사 watchlist 조회·교체 API | Hana-OmniLens-API | Done |
| 수동 분석 발행과 provider 수집·분석·발행 API | Hana-OmniLens-API | Done |
| 뉴스·공시 REST 목록/상세와 WebSocket 이벤트 | Hana-OmniLens-API | Done |
| 보유종목/watchlist 사용자 알림 매칭 | Stock-exchange-BE | Done |
| 종목 상세 K-News와 통합 알림함 | Stock-exchange-FE | Done |

## 2. 실시간 주식 정보 제공 및 주문 제한 필터링 시스템

| 기능 | 책임 | 상태 |
| --- | --- | --- |
| KIS/KRX 종목 마스터와 현재가 스냅샷 | Hana-OmniLens-API | Done |
| 종목 검색, 종목 상세, 글로벌 피어 매칭 | Hana-OmniLens-API, Hannah-Montana-AI | Done |
| KIS 실시간 체결가·호가 수집과 `/ws/market/quotes` 송신 | Hana-OmniLens-API | Done |
| 인기 10종목·지수 3개 고정 구독과 상세 종목 40 TR LRU 추가·해제 | Hana-OmniLens-API | Done |
| 지수 스냅샷, 지수 1D 분봉, 호가, 환율 적용 가격 제공 | Hana-OmniLens-API | Done |
| 종목 1D 분봉, 일봉 히스토리, 과거 시세 수집, 차트 캐시 예열 | Hana-OmniLens-API | Done |
| KRX 외국인 보유수량, 보유율, 한도소진율 스냅샷 캐시 | Hana-OmniLens-API | Done |
| 외국인 보유 단건 갱신, 전체 수집, 누락 평일 백필 | Hana-OmniLens-API | Done |
| 당일 외국인 한도소진율 예측 boundary | Hana-OmniLens-API, Hannah-Montana-AI | Done |
| 외국인 제한 종목 ML 재학습과 예측 선계산 캐시 | Hana-OmniLens-API, Hannah-Montana-AI | Done |
| VI, 단일가, 상·하한가, 거래정지 신호 | Hana-OmniLens-API | Done |
| 주문 가능 여부 API와 제한 사유 제공 | Hana-OmniLens-API | Done |
| FE용 quote/chart/orderability API와 WebSocket 재배포 | Stock-exchange-BE | Done |
| 시장 탭, 종목 상세, 차트, 주문 제한 UI | Stock-exchange-FE | Done |

## 3. 글로벌 세무 처리 자동화(OCR)

| 기능 | 책임 | 상태 |
| --- | --- | --- |
| 거주자 증명서·아포스티유·제한세율 적용신청서 파일 선택 | Stock-exchange-FE | Done |
| 계정별 파일 격리 저장, 문서 메타데이터·검증 진행 상태 | Stock-exchange-BE | Done |
| 세무 문서 검증 orchestration API | Hana-OmniLens-API | Done |
| 이미지/PDF 형식·magic byte·크기 검증 | Stock-exchange-BE, Hannah-Montana-AI | Done |
| OCR, 문서별 필드 추출, 필수 필드·일관성·위변조 위험 검증 | Hannah-Montana-AI | Done |
| 모의 매도 실현손익 기반 환급 케이스와 Hana 상태 동기화 | Stock-exchange-BE | Done |
| 업로드·OCR 진행·검증·환급 상태와 위험 고지 UI | Stock-exchange-FE | Done |
| 실제 신고·정부 승인·환급 지급·환수 | 외부 세무·지급 시스템 | 범위 밖 |

## 경계

- Hana-OmniLens-API는 실제 주문 명령, 체결, 정산, 환전을 수행하지 않는다.
- AI 모델 학습과 추론 로직은 Hannah-Montana-AI 책임이다.
- 사용자별 알림 매칭, 푸시 발송, 알림함 저장은 Stock-exchange-BE 책임이다.
- Hana-OmniLens-API와 Hannah-Montana-AI는 세무 문서를 검증하지만 실제 신고·지급·환수 결정을 수행하지 않는다.
