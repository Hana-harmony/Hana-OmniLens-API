# 테스트

## 로컬 검증
```bash
docker compose -f compose.local.yml up -d
./gradlew test --no-daemon
./gradlew bootJar --no-daemon
```

## 현재 테스트 범위
- API key 인증 성공
- API key 누락 시 `401`
- API key 해시 미설정 시 `503`
- API key rate limit 초과 시 `429`와 `Retry-After`
- health endpoint 공개
- OpenAPI 문서 API key 보호와 핵심 REST/WebSocket 계약 포함 여부
- 시장 데이터 응답 계약
- KIS Open API 현재가 provider 요청 헤더·쿼리·응답 매핑
- KIS WebSocket 체결·호가 구독 frame JSON 계약
- KIS WebSocket `H0STCNT0`, `H0STASP0` 실시간 payload 파싱
- KIS WebSocket raw message ingestion과 실시간 cache 저장
- KIS WebSocket session runner의 disabled no-op, 구독 frame 생성, 수신 메시지 cache 반영
- 시장 데이터 quote/orderbook의 KIS 실시간 cache 우선 사용
- 시장 데이터 quote의 KIS 현재가 우선 사용과 공공데이터 fallback
- 공공데이터 주식시세 provider 성공·fallback
- KRX 외국인보유량 provider 요청·응답 매핑
- 시장 데이터 quote의 KRX 외국인 보유수량·지분율·한도소진율 반영
- KRX 외국인보유량 날짜 재시도와 장애 시 전일 캐시 fallback
- 협력사 입력 환율 저장과 quote의 환율 캐시 fallback
- 한국수출입은행 환율 provider 요청·응답 매핑과 cache refresh
- `deal_bas_r`의 `KRW -> 현지통화` 변환 및 `JPY(100)` 단위 처리
- 환율 refresh scheduler의 disabled no-op, 기준일 offset, 통화별 장애 격리
- Redis 환율 cache TTL 저장, payload 조회, 장애 시 in-memory fallback
- quote 요청 `fxRate`가 저장된 환율보다 우선되는 계산 계약
- Naver News Search 응답 정규화
- OpenDART 공시검색 응답 매핑
- Hannah-Montana-AI 분석 클라이언트 계약
- 수집된 뉴스·공시의 AI 분석 후 WebSocket 알림 발행
- API key handshake 기반 WebSocket subscription 계약
- 시장/알림 API 입력 validation 실패와 ProblemDetail 응답 계약
- provider 수집 결과의 중복 URL 재발행 방지
- Redis TTL dedupe와 Redis 장애 시 in-memory fallback
- 협력사 watchlist 주기 수집 스케줄러 disabled/성공/실패 격리

## 추가 예정
- Redis integration testcontainer 기반 연결 테스트
