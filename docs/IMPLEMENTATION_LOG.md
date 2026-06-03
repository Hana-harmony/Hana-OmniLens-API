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

## 현재 구현 로직
- 시장 데이터는 `MarketDataService`의 목 데이터로 표준 응답 구조를 검증한다.
- 현지 통화 환산가는 `currentPriceKrw * fxRate`로 계산한다.
- 알림 이벤트는 `/api/v1/alerts/events`로 수신한 뒤 `/topic/partners/{partnerId}/alerts`, `/topic/stocks/{stockCode}/alerts`로 전송한다.

## 외부 연동 예정
- KIS, KRX, 한국수출입은행, Naver News, OpenDART는 현재 포트만 정의된 상태다.
- Hannah-Montana-AI가 뉴스·공시 분석 결과를 제공하면 알림 송신 payload에 연결한다.
