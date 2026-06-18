# 전체 구현 순서

이 문서는 Hana-OmniLens-API, Hannah-Montana-AI, Stock-exchange-BE, Stock-exchange-FE를 하네스 기준으로 구현할 때 따르는 공통 실행 순서와 완료 기준을 정의한다.

## 0. 기준 고정
- 최신 기능정의서를 기준으로 네 레포의 책임 범위를 먼저 고정한다.
- Hana-OmniLens-API는 한국 주식 데이터, 환율, 뉴스, 공시, AI 분석 결과, 세무 상태를 협력사에 제공한다.
- Hannah-Montana-AI는 뉴스와 공시의 번역, 요약, 감성, 중요도, 리스크 분석을 담당한다.
- Stock-exchange-BE는 현지 거래소의 회원, mock USD 계좌, watchlist, 보유종목, 자체 mock 매수·매도, 알림 대상 매칭을 담당한다.
- Stock-exchange-FE는 Flutter 기반 iOS/Android 앱으로 영어 UI와 USD 표시를 제공한다.

완료 기준:
- 각 레포의 `docs/FEATURE_CLASSIFICATION.md`, `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`가 위 책임 범위와 충돌하지 않는다.
- 주문 기능은 KIS 모의투자 API가 아니라 Stock-exchange-BE의 자체 mock ledger로 구현한다고 명시되어 있다.
- Flutter 앱은 Docker 실행 대상이 아니며, iOS/Android 로컬 SDK와 시뮬레이터 기준으로 검증한다고 명시되어 있다.

## 1. 공통 백엔드 하네스
- 모든 백엔드는 REST API 공통 응답 형식을 사용한다.
- 커스텀 예외, 예외 코드, 글로벌 예외 처리를 사용한다.
- Swagger/OpenAPI 문서를 제공한다.
- 백엔드는 Docker Compose로 로컬 실행과 smoke test가 가능해야 한다.
- 시크릿은 gitignore된 local 파일이나 환경변수로만 관리한다.

완료 기준:
- Spring 백엔드는 `./gradlew test --no-daemon`이 통과한다.
- FastAPI 백엔드는 import/compile 검증과 가능한 테스트가 통과한다.
- Docker Compose 실행 후 health endpoint 또는 동등한 smoke test가 통과한다.
- OpenAPI 문서 endpoint가 확인된다.

## 2. API 계약 우선 고정
- Hana-OmniLens-API와 Stock-exchange-BE 사이의 시장 데이터 계약을 먼저 고정한다.
- 실시간 시세는 WebSocket stream을 기본으로 하고, REST snapshot은 초기 로딩, 복구, 캐시 조회 용도로 사용한다.
- 과거 차트 데이터는 Hana-OmniLens-API가 KRX 기반으로 수집·정규화·저장한 데이터를 REST로 제공한다.
- 시세 응답은 원화 가격과 실시간 환율이 적용된 USD 가격을 함께 제공한다.
- 뉴스와 공시 분석 이벤트는 원문 링크, 번역, 요약, 감성, 중요도, 리스크, 관련 종목, 중복 키를 포함한다.

완료 기준:
- OpenAPI 또는 별도 contract 문서에 request, response, error code, event payload가 고정되어 있다.
- WebSocket topic, reconnect, replay, backpressure, stale 정책이 문서화되어 있다.
- 환율 기준시각, 출처, stale flag, fallback 정책이 문서화되어 있다.

## 3. Hana-OmniLens-API 구현
- KIS 현재가 REST와 실시간 체결가·호가 WebSocket adapter를 구현한다.
- KRX 모든 국내 주식 과거 시세 수집 batch와 정규화 DB schema를 구현한다.
- 종목 마스터, 외국인 보유율, 상·하한가, VI 상태, 환율 cache를 구현한다.
- Hannah-Montana-AI 분석 API를 호출해 뉴스·공시 분석 결과를 저장하고 협력사로 전달한다.
- 협력사 인증, rate limit, 감사 로그, 장애 추적을 적용한다.

완료 기준:
- 전체 한국 주식 대상 현재가, 실시간 stream, 과거 차트 REST가 동작한다.
- quote payload에 KRW, USD, 환율, 환율 기준시각, 출처, stale flag가 포함된다.
- 뉴스·공시 이벤트가 원문 링크와 AI 분석 결과를 함께 포함한다.
- contract test와 통합 smoke test가 있다.

## 4. Hannah-Montana-AI 구현
- 뉴스와 공시 입력 schema를 고정한다.
- 번역, 요약, 감성, 중요도, 리스크, 종목 매핑 결과를 표준 응답으로 제공한다.
- 금융 용어 normalization과 중복 제거 로직을 개선한다.
- 모델 버전, 평가 기준, fallback rule을 문서화한다.

완료 기준:
- Hana-OmniLens-API에서 호출 가능한 안정 API가 있다.
- 분석 응답에 모델 버전과 판단 근거가 포함된다.
- 기준 평가 데이터셋과 최소 품질 검증이 있다.

## 5. Stock-exchange-BE 구현
- Java Spring Boot 구조를 유지하고 Hana-OmniLens-API와 유사한 계층 구조를 사용한다.
- 아이디/비밀번호 회원가입과 mock USD 계좌 생성을 구현한다.
- 실제 결제 없는 달러 충전과 자체 mock ledger 기반 매수·매도를 구현한다.
- 전체 한국 주식, watchlist, 보유종목의 REST snapshot과 WebSocket 실시간 시세를 제공한다.
- 과거 차트 데이터는 Hana-OmniLens-API의 KRX 기반 API를 사용한다.
- 보유종목과 watchlist를 기준으로 뉴스·공시 분석 push 대상자를 매칭한다.
- 매도 실현손익을 세무 환급 기능의 입력 데이터로 연결한다.

완료 기준:
- KIS 모의투자 API 호출 없이 자체 mock 거래가 가능하다.
- FE가 요구하는 시세, 차트, 주문, 계좌, watchlist, 알림 API가 있다.
- 실시간 stream 장애 시 REST snapshot 복구 정책이 동작한다.
- 세무 환급 기능이 매도 실현손익을 참조한다.

## 6. Stock-exchange-FE 구현
- Flutter 기반 iOS/Android 앱으로 구현한다.
- UI 기본 언어는 영어이며, 금액의 기본 화폐 단위는 USD다.
- 종목 검색, 전체 종목 시세, watchlist, 보유종목, 차트, 주문, 계좌, 알림, 세무 환급 화면을 구현한다.
- WebSocket 실시간 tick을 화면에 반영하고 stale 상태와 재연결 상태를 보여준다.
- 뉴스·공시 분석 알림은 원문 링크와 함께 표시한다.

완료 기준:
- `flutter analyze`, `flutter test`, iOS/Android simulator 실행 기준이 문서화되어 있다.
- Docker 실행을 앱 검증 기준으로 사용하지 않는다.
- 영어 UI와 USD 표시가 주요 화면에 적용되어 있다.

## 7. 통합 검증
- Hana-OmniLens-API와 Hannah-Montana-AI 분석 연동을 검증한다.
- Hana-OmniLens-API와 Stock-exchange-BE의 시세, 차트, 뉴스·공시 이벤트 연동을 검증한다.
- Stock-exchange-BE와 Stock-exchange-FE의 REST/WebSocket 연동을 검증한다.
- 장애 상황에서 REST snapshot 복구, WebSocket reconnect, stale 표시, 중복 알림 방지를 검증한다.

완료 기준:
- 백엔드는 Docker Compose로 로컬 통합 검증이 가능하다.
- Flutter 앱은 iOS/Android simulator에서 주요 사용자 흐름이 확인된다.
- PR 체크에 단위 테스트, 문서 검증, 최소 smoke test가 포함된다.

## 8. 브랜치와 PR 진행
- 작업 브랜치는 `Feat/...`, `Fix/...`, `Docs/...` 형식을 따른다.
- 커밋 메시지는 한글로 작성하되 conventional prefix와 scope는 영문을 유지한다.
- 1차 PR은 작업 브랜치에서 `feature`로 올린다.
- 체크 통과 후 `feature`에 merge한다.
- 2차 PR은 `feature`에서 `main`으로 올린다.
- 체크 통과 후 `main`에 merge하고 작업 브랜치를 삭제한다.

완료 기준:
- PR 설명에 구현 범위, 검증 결과, 남은 리스크가 한글로 정리되어 있다.
- 체크가 실패한 PR은 merge하지 않는다.
- 문서와 테스트가 코드 변경과 함께 갱신되어 있다.
