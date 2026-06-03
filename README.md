# Hana OmniLens API

해외 협력사 거래소·브로커가 한국 주식 정보 서비스를 쉽게 붙일 수 있도록 제공하는 B2B 정보 API다.

## 범위
- Real-time Korea Market Data API
- Watchlist News & Disclosure Alert API
- 실제 주문, 체결, 정산, 환전, 옴니버스 계좌 처리는 제외

## 빠른 시작
```bash
docker compose -f compose.local.yml up -d
./gradlew test
./gradlew bootRun
```

로컬 설정 파일 `src/main/resources/application-local.yml`은 gitignore 대상이다. 운영 설정 파일 `src/main/resources/application-prod.yml`은 실제 파일로 커밋하고, 민감값은 GitHub Secrets로 생성한 원격 서버 env 파일에서 주입한다.

알림은 수동 `collect-and-publish` 호출뿐 아니라 설정 기반 협력사 watchlist 스케줄러로도 수집·분석·WebSocket 발행할 수 있다.

## API 계약
- `GET /openapi.yaml`
- OpenAPI 문서는 API key 보호 대상이며 REST endpoint와 WebSocket endpoint/topic 계약을 함께 기록한다.

## 주요 엔드포인트
- `GET /api/v1/market/stocks/{stockCode}/quote`
- `GET /api/v1/market/stocks/{stockCode}/orderbook`
- `GET /api/v1/market/stocks/search?query=삼성`
- `POST /api/v1/alerts/events`
- `POST /api/v1/alerts/analyze-and-publish`
- `POST /api/v1/alerts/collect-and-publish`
- `WS /ws/alerts`, topic `/topic/partners/{partnerId}/alerts`

## 문서
- [기여 가이드](CONTRIBUTING.md)
- [아키텍처](docs/ARCHITECTURE.md)
- [운영](docs/OPERATIONS.md)
- [배포](docs/DEPLOYMENT.md)
- [보안](docs/SECURITY.md)
- [테스트](docs/TESTING.md)
- [구현 기록](docs/IMPLEMENTATION_LOG.md)
- [로드맵](docs/ROADMAP.md)
- [깃 전략](docs/GIT_STRATEGY.md)
