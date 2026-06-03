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

## 현재 구현 로직
- 시장 데이터는 공공데이터 주식시세 snapshot을 우선 사용하고, 사용할 수 없으면 fallback 데이터로 표준 응답 구조를 유지한다.
- 현지 통화 환산가는 `currentPriceKrw * fxRate`로 계산한다.
- 알림 이벤트는 `/api/v1/alerts/events`로 수신한 뒤 `/topic/partners/{partnerId}/alerts`, `/topic/stocks/{stockCode}/alerts`로 전송한다.
- Naver News 응답의 HTML 태그와 entity를 정규화해 제목과 snippet으로 변환한다.
- OpenDART 공시검색 응답의 접수번호로 원문 공시 URL을 생성한다.
- 공공데이터 주식시세 응답은 첫 번째 종목 항목을 `PublicDataStockPriceSnapshot`으로 변환한다.

## 외부 연동 예정
- KIS, KRX 외국인 보유율, 한국수출입은행 환율은 현재 포트만 정의된 상태다.
- Hannah-Montana-AI가 뉴스·공시 분석 결과를 제공하면 알림 송신 payload에 연결한다.
