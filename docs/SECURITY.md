# 보안

## 현재 기준
- 모든 운영 API 요청은 `X-HANA-OMNILENS-API-KEY`를 사용한다.
- 서버는 API key 원문을 저장하지 않고 `application-prod.yml`의 SHA-256 해시와 상수 시간 비교한다.
- API key 해시가 없으면 운영 API는 실패 닫힘 방식으로 `503`을 반환한다.
- CORS는 profile별 설정 파일의 허용 목록만 사용한다.
- 세션은 stateless로 유지한다.

## 시크릿 관리
- `application-local.yml`은 커밋하지 않는다.
- `application-prod.yml`은 커밋하되 `${...}` 환경변수 placeholder만 사용한다.
- 로컬 시크릿은 `application-local.yml`에만 둔다.
- 운영 시크릿은 GitHub Secrets로 주입하고 원격 서버의 `application-prod.env`에만 생성한다.
- `application-prod.env`는 커밋하지 않는다.
- GHCR pull token은 원격 서버의 `deploy-prod.env`에만 생성하고 앱 컨테이너에는 주입하지 않는다.
- `deploy-prod.env`는 커밋하지 않는다.

## 향후 강화
- 협력사별 key rotation
- mTLS 또는 요청 서명
- WebSocket handshake 인증 강화
- rate limit과 abuse detection
- 감사 로그 무결성 보장
