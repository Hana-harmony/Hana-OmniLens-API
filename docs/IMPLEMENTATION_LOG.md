# 구현 기록

## 2026-06-03 하네스 구축
- Spring Boot 3.5.14, Java 17, Gradle Wrapper 기반 API 프로젝트 생성
- Real-time Korea Market Data API 계약 초안 구현
- Watchlist News & Disclosure Alert API WebSocket 송신 계약 초안 구현
- API key SHA-256 해시 검증 필터 구현
- Git 전략, PR 템플릿, CI 하네스 추가

## 2026-06-04 profile 기반 설정 분리
- 로컬 설정을 gitignore된 `application-local.yml`로 분리
- 운영 설정을 커밋되는 실제 `application-prod.yml`과 GitHub Secrets 기반 env 파일로 분리
- 로컬 Postgres, Redis 컨테이너용 `compose.local.yml` 추가
- `main` push 시 GitHub Secrets로 원격 서버의 `application-prod.env`를 생성하는 배포 job 추가

## 2026-06-04 GHCR 기반 배포 전환
- jar scp 배포를 GHCR 이미지 push/pull 방식으로 변경
- `compose.prod.yml` 추가
- GHCR pull용 `deploy-prod.env`와 앱 런타임용 `application-prod.env` 분리
- CI/CD가 원격 서버에 `deploy.sh`까지 전송하고 Docker Compose로 재시작하도록 변경

## 2026-06-04 외부 제공자 클라이언트 하네스
- 공공데이터 주식시세, Naver News Search, OpenDART 공시검색 설정을 profile 기반으로 분리
- 운영 설정은 `PUBLIC_DATA_SERVICE_KEY`, `NAVER_NEWS_CLIENT_ID`, `NAVER_NEWS_CLIENT_SECRET`, `OPEN_DART_API_KEY` 환경변수로만 주입
- 로컬 실제 키는 gitignore된 `application-local.yml`에만 저장
- 제공자별 `RestClient` 어댑터를 추가하고 테스트에서는 `MockRestServiceServer`로 네트워크 없이 요청 헤더, 쿼리, 응답 매핑을 검증
- 키가 비어 있으면 호출 시점에 예외를 발생시켜 외부 API를 잘못 호출하지 않도록 처리

## 2026-06-04 시장 데이터 제공자 어댑터 연결
- `MarketDataService`가 공공데이터 주식시세 snapshot을 우선 사용하도록 변경
- 최근 7일 범위에서 전일 기준 가격 데이터를 탐색하고, 미설정·장애·무응답 시 목 시세로 fallback
- 종목 검색과 quote의 종목명 보강을 위해 인메모리 종목 마스터 저장소 추가
- 로컬 실제 키가 있어도 테스트가 외부망을 타지 않도록 컨트롤러 테스트에서 provider key를 비움
- 서비스 단위 테스트로 provider 성공, provider 실패 fallback, 종목 마스터 검색을 검증

## 2026-06-04 KRX 외국인보유량 snapshot 연결
- KRX 외국인보유량(개별종목) 화면의 `MDCSTAT03702` 데이터 계약을 격리한 `KrxForeignOwnershipClient` 추가
- 종목 마스터에 ISIN을 추가해 KRX 개별종목 조회에 필요한 `isuCd`를 전달
- KRX 응답의 외국인 보유수량, 외국인 지분율, 외국인 한도수량, 한도소진율을 `KrxForeignOwnershipSnapshot`으로 변환
- `MarketDataService`가 KRX snapshot을 우선 사용하고, 호출 실패 또는 미응답 시 기존 fallback 값을 유지
- source 필드에 가격 provider와 외국인보유 provider 사용 여부를 함께 표시
- MockRestServiceServer 테스트로 KRX form 요청과 숫자 포맷 파싱을 검증

## 2026-06-04 Hannah-Montana-AI 분석 클라이언트
- 내부 FastAPI 서비스 `POST /api/v1/alerts/analyze` 호출용 `HannahAiAnalysisClient` 추가
- AI 요청·응답 DTO는 Python API 계약에 맞춰 snake_case JSON 필드로 고정
- 스프링 컨테이너 내부 통신 전제에 따라 서비스 토큰 헤더는 사용하지 않음
- 운영 설정은 secret이 아닌 `HANNAH_AI_BASE_URL` 또는 기본 내부 서비스명으로 처리
- 테스트에서 토큰 헤더 미전송, stock universe payload, 분석 응답 매핑을 검증

## 2026-06-04 알림 분석 후 발행 endpoint
- `/api/v1/alerts/analyze-and-publish` endpoint 추가
- 수집된 뉴스·공시 원문과 종목 universe를 Hannah-Montana-AI에 전달하고 분석 결과를 WebSocket 알림 이벤트로 변환
- AI가 종목을 매핑하지 못하면 `422 Unprocessable Entity`로 발행을 중단
- 번역 어댑터가 붙기 전까지 `translatedTitle`은 원문 제목으로 대체
- MockMvc 테스트로 AI 분석 결과가 기존 알림 이벤트 payload로 발행되는지 검증

## 2026-06-04 뉴스·공시 provider 수집 후 발행 endpoint
- `/api/v1/alerts/collect-and-publish` endpoint 추가
- 협력사가 `partnerId`, `stockCodes`, 뉴스 수집 개수, 공시 조회 기간을 전달하면 종목별 provider 수집을 수행
- 인메모리 종목 마스터에 OpenDART 고유번호를 추가해 주식코드에서 공시검색 `corp_code`를 찾도록 구성
- Naver News Search 결과를 `NEWS` 분석 요청으로 변환하고 Hannah-Montana-AI 분석 후 기존 WebSocket 발행 로직을 재사용
- OpenDART 공시검색 결과를 `DISCLOSURE` 분석 요청으로 변환하고 접수번호 기반 원문 URL을 알림 payload에 포함
- 같은 partner, source type, original URL 조합은 bounded in-memory dedupe로 중복 재발행을 방지
- AI가 종목을 매핑하지 못한 건은 발행하지 않고 실패 카운트에 반영
- MockMvc 테스트로 뉴스·공시 수집, 중복 URL skip, AI 분석, 알림 발행 응답을 검증

## 현재 구현 로직
- 시장 데이터는 공공데이터 주식시세 snapshot을 우선 사용하고, 사용할 수 없으면 fallback 데이터로 표준 응답 구조를 유지한다.
- 외국인 보유수량, 외국인 지분율, 한도소진율은 KRX 외국인보유량 snapshot을 우선 사용하고 장애 시 fallback 데이터로 응답 구조를 유지한다.
- 현지 통화 환산가는 `currentPriceKrw * fxRate`로 계산한다.
- 알림 이벤트는 `/api/v1/alerts/events`로 수신한 뒤 `/topic/partners/{partnerId}/alerts`, `/topic/stocks/{stockCode}/alerts`로 전송한다.
- 알림 분석 발행 endpoint는 AI 분석 결과를 받아 기존 알림 이벤트 송신 로직을 재사용한다.
- 알림 수집 발행 endpoint는 Naver 뉴스와 OpenDART 공시를 수집한 뒤 AI 분석과 WebSocket 발행을 순차 수행한다.
- provider 수집 기반 알림은 프로세스 단위 bounded in-memory dedupe로 같은 원문 URL의 반복 발행을 줄인다.
- Naver News 응답의 HTML 태그와 entity를 정규화해 제목과 snippet으로 변환한다.
- OpenDART 공시검색 응답의 접수번호로 원문 공시 URL을 생성한다.
- 공공데이터 주식시세 응답은 첫 번째 종목 항목을 `PublicDataStockPriceSnapshot`으로 변환한다.
- Hannah-Montana-AI 분석 응답은 알림 이벤트 생성 단계에서 사용할 표준 분석 결과 DTO로 수신한다.

## 외부 연동 예정
- KIS, 한국수출입은행 환율은 현재 포트만 정의된 상태다.
- KRX 외국인보유량 provider는 화면 기반 endpoint이므로 운영 전 Redis/DB 전일 캐시와 장애 재시도 정책을 붙인다.
- Redis 또는 DB 기반 저장형 dedupe와 주기 수집 스케줄러를 추가한다.
