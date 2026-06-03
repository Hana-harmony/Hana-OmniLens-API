# 보안

## 현재 기준
- 모든 운영 API 요청은 `X-HANA-OMNILENS-API-KEY`를 사용한다.
- 서버는 API key 원문을 저장하지 않고 `application-prod.yml`의 SHA-256 해시와 상수 시간 비교한다.
- API key 해시가 없으면 운영 API는 실패 닫힘 방식으로 `503`을 반환한다.
- 인증된 요청은 API key SHA-256 fingerprint 단위로 rate limit을 적용한다.
- rate limit 초과 요청은 `429 Too Many Requests`와 `Retry-After` 헤더를 반환한다.
- 운영에서 `OMNILENS_SIGNATURE_ENABLED=true`인 경우 모든 보호 API 요청은 HMAC-SHA256 요청 서명을 함께 검증한다.
- 요청 서명 canonical string은 `METHOD`, `URI_WITH_QUERY`, `TIMESTAMP`, `NONCE`, `SHA256_BODY_HEX`를 줄바꿈으로 연결한 값이다.
- 서명 헤더는 `X-HANA-OMNILENS-TIMESTAMP`, `X-HANA-OMNILENS-NONCE`, `X-HANA-OMNILENS-SIGNATURE`를 사용한다.
- timestamp는 허용 clock skew 안에 있어야 하고, nonce는 같은 API key fingerprint에서 한 번만 사용할 수 있다.
- CORS는 profile별 설정 파일의 허용 목록만 사용한다.
- WebSocket `/ws/alerts` handshake도 API key 검증 대상이다.
- 세션은 stateless로 유지한다.

## 시크릿 관리
- `application-local.yml`은 커밋하지 않는다.
- `application-prod.yml`은 커밋하되 `${...}` 환경변수 placeholder만 사용한다.
- 로컬 시크릿은 `application-local.yml`에만 둔다.
- 운영 시크릿은 GitHub Secrets로 주입하고 원격 서버의 `application-prod.env`에만 생성한다.
- `application-prod.env`는 커밋하지 않는다.
- GHCR pull token은 원격 서버의 `deploy-prod.env`에만 생성하고 앱 컨테이너에는 주입하지 않는다.
- `deploy-prod.env`는 커밋하지 않는다.

## 외부/운영 시크릿
- `KIS_APP_KEY`: KIS Open API app key
- `KIS_APP_SECRET`: KIS Open API app secret
- `KIS_ACCESS_TOKEN`: KIS Open API access token
- `KIS_APPROVAL_KEY`: KIS WebSocket approval key
- `PUBLIC_DATA_SERVICE_KEY`: 공공데이터포털 주식시세 계열 API 인증키
- `NAVER_NEWS_CLIENT_ID`: Naver News Search API Client ID
- `NAVER_NEWS_CLIENT_SECRET`: Naver News Search API Client Secret
- `OPEN_DART_API_KEY`: OpenDART 공시검색 API 인증키
- `KOREA_EXIM_AUTH_KEY`: 한국수출입은행 환율 API 인증키
- `PAPAGO_TRANSLATION_CLIENT_ID`: Papago NMT API Client ID
- `PAPAGO_TRANSLATION_CLIENT_SECRET`: Papago NMT API Client Secret
- `OMNILENS_SIGNATURE_SECRET`: 협력사 요청 서명 검증용 HMAC secret
- 외부 API 키는 로그, 예외 메시지, 테스트 fixture, 커밋 파일에 남기지 않는다.

## 내부 AI 통신
- Hannah-Montana-AI는 스프링 컨테이너 내부 네트워크에서만 접근 가능하게 배치한다.
- `HANNAH_AI_BASE_URL`은 주소 설정값이며 secret으로 분류하지 않는다.
- `KRX_BASE_URL`은 KRX 데이터 endpoint 주소 설정값이며 secret으로 분류하지 않는다.
- `KIS_BASE_URL`은 KIS endpoint 주소 설정값이며 secret으로 분류하지 않는다.
- `KIS_WEBSOCKET_URL`은 KIS WebSocket endpoint 주소 설정값이며 secret으로 분류하지 않는다.
- `KOREA_EXIM_BASE_URL`은 한국수출입은행 endpoint 주소 설정값이며 secret으로 분류하지 않는다.
- 별도 서비스 토큰 헤더는 사용하지 않는다.

## 향후 강화
- 협력사별 key rotation
- mTLS
- Redis 기반 signature nonce 공유 저장소
- WebSocket topic authorization 세분화
- abuse detection
- 감사 로그 무결성 보장
