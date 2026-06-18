# 기능 분류와 레포 책임

## 1. 한국 주식 주문 지원

Hana-OmniLens-API는 실제 주문 명령을 받지 않는다. 대신 현지 거래소가 모든 한국 상장주식의 영어/USD 앱 화면과 자체 모의 거래에 사용할 시장 데이터와 매매제한 신호를 제공한다.

| 기능 | 책임 | 상태 |
| --- | --- | --- |
| KIS 현재가 REST, 실시간 체결가·호가 WebSocket 수집 | Hana-OmniLens-API | Done |
| KIS 실시간 시세 캐시와 단건 quote snapshot API | Hana-OmniLens-API | Done |
| 전체/다건 종목 quote snapshot API | Hana-OmniLens-API | Planned |
| 협력사용 market quote WebSocket stream | Hana-OmniLens-API | Planned |
| 실시간 또는 최신 환율 수집과 환율 캐시 | Hana-OmniLens-API | Done |
| KRW 가격과 현지통화 환산 가격을 함께 포함한 quote payload | Hana-OmniLens-API | Done |
| KRX 모든 국내 주식 과거 시세 수집과 DB 저장 | Hana-OmniLens-API | Planned |
| 과거 시세 chart REST API | Hana-OmniLens-API | Planned |
| KIS 종목 마스터 파일 파싱, 종목 마스터 DB 적재 | Hana-OmniLens-API | Done |
| KRX 전일 외국인 보유수량, 보유율, 한도소진율 캐시 | Hana-OmniLens-API | Done |
| 당일 외국인 보유율/한도소진율 예측 boundary | Hana-OmniLens-API, Predict Engine | Planned |
| VI 발동, 단일가 매매, 상·하한가 상태 감지 | Hana-OmniLens-API | Planned |
| 종목 상세/주문 상태 JSON API | Hana-OmniLens-API | Done |
| 모의 매수·매도 기준 가격과 주문 가능 여부 판단 | Stock-exchange-BE | Done |
| 아이디/비밀번호 회원가입, mock USD 계좌 생성, 달러 충전 | Stock-exchange-BE | Done |
| KIS 모의투자 API가 아닌 자체 mock ledger 기반 가짜 매수·매도 | Stock-exchange-BE | Done |
| 매도 실현손익을 세무 환급/선지급 입력 데이터로 연결 | Stock-exchange-BE | Done |
| 외국인 한도 게이지, VI/상·하한가 배지, 주문 제한 팝업 | Stock-exchange-FE | Planned |

## 2. 한국 주식 정보 취득 및 분석

Hana-OmniLens-API는 수집, 번역 공급자 연동, 협력사 전송을 담당하고 Hannah-Montana-AI는 금융 NLP 분석을 담당한다.

| 기능 | 책임 | 상태 |
| --- | --- | --- |
| Naver News Search 수집 | Hana-OmniLens-API | Done |
| OpenDART 공시 수집 | Hana-OmniLens-API | Done |
| 종목 매핑, 이벤트 분류, 감성, 중요도, 중복 제거 | Hannah-Montana-AI | Partial |
| Papago 전문 번역 어댑터 | Hana-OmniLens-API | Done |
| DeepL 전문 번역 어댑터 | Hana-OmniLens-API | Planned |
| 분석·번역 완료 이벤트 WebSocket 송신 | Hana-OmniLens-API | Done |
| 이벤트 수신, 저장, 보유/관심종목 대상자 매칭, 푸시 발송 | Stock-exchange-BE | Done |
| 종목별 K-News 피드, 통합 알림함, 원문 링크 UI | Stock-exchange-FE | Planned |

## 3. 최종투자자별 세무 전산화 및 환급금 선지급

세무 기능은 금융/규제 검토가 필요한 planned 영역이다. 문서상 계약은 두되 실제 운영 전 법무, 컴플라이언스, 보안 검토를 필수로 한다.

| 기능 | 책임 | 상태 |
| --- | --- | --- |
| 거주자증명서, 제한세율신청서, 거래원장 수집 게이트웨이 | Stock-exchange-BE, Hana-OmniLens-API | Partial |
| OCR 텍스트 추출, 위변조/딥페이크 1차 검증 | Hannah-Montana-AI 또는 별도 OCR service | Planned |
| 한국·홍콩 조세조약 케이스 판정 | Hana-OmniLens-API | Planned |
| 환급금 선지급 산정, 수수료, 사후 환수 상태 계약 | Hana-OmniLens-API | Partial |
| 분기별 경정청구 배치 상태 조회 | Hana-OmniLens-API | Planned |
| 서류 업로드, 상태 타임라인, 환급 신청, 입금 완료 UI | Stock-exchange-FE | Planned |

## 기존 기획에서 보존할 항목

- 협력사 인증은 `X-HANA-OMNILENS-API-KEY`와 SHA-256 해시 비교를 기본으로 한다.
- AI 서비스는 협력사 API key를 받지 않고 내부 네트워크로 격리한다.
- WebSocket topic은 협력사 단위와 종목 단위를 모두 유지한다.
- 실제 사용자별 알림 매칭, 푸시 발송, 알림함 저장은 현지 거래소 백엔드 책임이다.
- 실제 주문 실행, 체결, 정산, 환전은 현재 프로젝트 범위 밖이다. Stock-exchange-BE의 거래 기능은 실제 거래가 아니라 자체 mock ledger 체험 기능이다.
