# 운영 로드맵

## 현재 운영 기준

- KIS 활성 종목 마스터, 현재가·호가·지수·분봉, KRX 일봉과 외국인 보유 snapshot을 DB·cache·REST/WebSocket으로 제공한다.
- Naver News·OpenDART 수집, 전문 정제, Hannah 분석·번역, 재처리와 협력사 alert stream을 제공한다.
- 글로벌 피어, 금융 용어, 외국인 예측과 세무 OCR을 Hannah AI 계약으로 orchestration한다.
- API key hash, partner scope, rate limit, request signature nonce, mTLS, correlation ID와 감사 로그를 구현했다.
- PostgreSQL·Redis, GHCR 이미지와 Docker Compose 운영 배포 경로를 구성했다.

## 다음 운영 기준

- 실제 provider 계정으로 KIS·KRX·Naver·OpenDART 장중·장외 통합 smoke와 quota 알림을 자동화한다.
- Redis·DB 장애, provider circuit open, WebSocket 재연결·replay의 복구 목표를 부하·장애 주입 테스트로 검증한다.
- 세무 문서 검증 호출의 계정 간 격리, 원본 비저장, 감사 이벤트와 보존 정책을 운영 환경에서 검증한다.
- API key rotation, mTLS 인증서 만료, 서명 nonce store 복구 runbook을 정기 훈련한다.
- SLO, latency·error budget, provider별 비용·quota dashboard와 경보를 확정한다.

운영 항목은 자동 테스트, 관측 지표, rollback 절차와 담당자가 문서화된 경우에만 완료 처리한다.
