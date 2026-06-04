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
- DB active partner credential 인증 성공
- inactive partner credential 거부
- 인증 partner와 요청 partner 불일치 시 알림 API `403`
- API key rate limit 초과 시 `429`와 `Retry-After`
- HMAC 요청 서명 성공
- HMAC 요청 서명 누락, nonce 재사용, stale timestamp 거부
- 요청 body 변조 시 HMAC 서명 불일치 거부
- mTLS 활성화 시 보호 API의 client certificate 필수 검증과 health endpoint 예외
- Redis 기반 HMAC nonce `SETNX` TTL 저장과 duplicate nonce 거부
- Redis/in-memory/unavailable HMAC nonce store 설정 선택
- correlation id 응답 헤더 echo/generation
- 보안 감사 로그의 API key fingerprint masking
- health endpoint 공개
- OpenAPI 문서 API key 보호와 핵심 REST/WebSocket 계약 포함 여부
- 시장 데이터 응답 계약
- Flyway `stock_master` schema 생성
- 종목 마스터 seed loader의 초기 적재와 중복 실행 방지
- JDBC 종목 마스터 저장소의 코드, 한글명, 영문명 검색
- 시장 데이터 종목 단건 조회 API의 DB seed 데이터 반환과 미지원 종목 404 ProblemDetail
- 시장 데이터 종목 검색 API의 DB seed 데이터 반환
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
- Redis 외국인 보유율 cache TTL 저장, payload 조회, 장애 시 in-memory fallback
- 협력사 입력 환율 저장과 quote의 환율 캐시 fallback
- 한국수출입은행 환율 provider 요청·응답 매핑과 cache refresh
- `deal_bas_r`의 `KRW -> 현지통화` 변환 및 `JPY(100)` 단위 처리
- 환율 refresh scheduler의 disabled no-op, 기준일 offset, 통화별 장애 격리
- Redis 환율 cache TTL 저장, payload 조회, 장애 시 in-memory fallback
- quote 요청 `fxRate`가 저장된 환율보다 우선되는 계산 계약
- Naver News Search 응답 정규화
- OpenDART 공시검색 응답 매핑
- Papago NMT 번역 요청 계약과 응답 매핑
- 알림 제목 번역 성공과 번역 장애 시 원문 fallback
- Hannah-Montana-AI 분석 클라이언트 계약
- 외부 provider 공통 timeout 설정 기본값
- 외부 provider 재시도, circuit open, 비네트워크 예외 no-retry 정책
- 수집된 뉴스·공시의 AI 분석 후 WebSocket 알림 발행
- AI 분석 `duplicateKey`, `modelVersion` 추적 메타데이터 전파
- API key handshake 기반 WebSocket subscription 계약
- DB credential WebSocket 세션의 partner-scoped topic 수신과 global stock topic 차단
- 시장/알림 API 입력 validation 실패와 ProblemDetail 응답 계약
- provider 수집 결과의 중복 URL 재발행 방지
- AI `duplicateKey` 기반 수집 알림 재발행 방지
- Redis TTL dedupe와 Redis 장애 시 in-memory fallback
- 협력사 watchlist 주기 수집 스케줄러 disabled/성공/실패 격리
- 협력사 watchlist DB 교체 저장, 조회, 순서 보존, 빈 목록 삭제
- 협력사 watchlist REST API의 중복 제거, validation, 미지원 종목 404
- 설정 기반 watchlist와 DB watchlist의 스케줄러 병합

## 추가 예정
- Redis integration testcontainer 기반 연결 테스트
