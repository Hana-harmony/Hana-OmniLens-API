# 테스트

## 로컬 검증
```bash
docker compose -f compose.local.yml up -d
./gradlew test --no-daemon
./gradlew bootJar --no-daemon
```

## 현재 테스트 범위
- 배포 환경 분리 guardrail
  - `application-local.yml` gitignore 유지
  - `application-prod.yml` 필수 secret placeholder 유지
  - `compose.prod.yml` prod profile과 외부 env file 사용
  - GitHub Actions main push GHCR 배포 흐름
- API key 인증 성공
- API key 누락 시 `401`
- API key 해시 미설정 시 `503`
- DB active partner credential 인증 성공
- inactive partner credential 거부
- bootstrap 운영 키 기반 협력사 API key rotation, 기존 키 비활성화, 새 키 1회 반환
- DB credential 기반 rotation API 호출 거부
- 인증 partner와 요청 partner 불일치 시 알림 API `403`
- API key rate limit 초과 시 `429`와 `Retry-After`
- HMAC 요청 서명 성공
- HMAC 요청 서명 누락, nonce 재사용, stale timestamp 거부
- 요청 body 변조 시 HMAC 서명 불일치 거부
- mTLS 활성화 시 보호 API의 client certificate 필수 검증과 health endpoint 예외
- Redis 기반 HMAC nonce `SETNX` TTL 저장과 duplicate nonce 거부
- Redis/in-memory/unavailable HMAC nonce store 설정 선택
- Testcontainers Redis 실제 연결 기반 HMAC nonce, 알림 dedupe, 환율 cache, 외국인 보유율 cache 통합 검증
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
- KIS REST 호가 snapshot 요청·응답 매핑과 실시간 호가 cache 공백 시 orderbook fallback
- orderability의 KIS 실시간 체결 기반 상·하한가, VI, 단일가, 거래정지 상태 반영
- raw `/ws/market/quotes` WebSocket quote tick 수신과 replay 요청
- 시장 데이터 quote의 KIS 현재가 우선 사용, `EGW00201` 1회 재시도, 공공데이터 보강, provider 부재 시 `MARKET_002` 실패
- 공공데이터 주식시세 provider 성공·provider 부재 실패
- KIS 현재가 provider의 외국인 보유수량·소진율 응답 매핑
- 시장 데이터 quote의 KIS 외국인 보유수량·지분율·한도소진율 반영
- 외국인 보유율 예측 engine의 snapshot-only, 실시간 거래량 조정, 시계열 추세 보정, snapshot 부재 fallback
- KIS 실시간 체결가·호가 WebSocket payload에는 외국인 보유량 필드가 없고, 외국인 한도 정보는 KIS 현재가 REST snapshot cache에서만 공급되는지 문서 계약 검증
- Redis 외국인 보유율 cache TTL 저장, payload 조회, 장애 시 in-memory fallback
- 협력사 입력 환율 저장, Frankfurter 환율 cache 사용, 환율 부재 시 `MARKET_002` 실패
- Frankfurter 환율 provider 요청·응답 매핑과 cache refresh
- 환율 refresh scheduler의 disabled no-op, 기준일 offset, 통화별 장애 격리
- Redis 환율 cache TTL 저장, payload 조회, 장애 시 in-memory fallback
- quote 요청 `fxRate`가 저장된 환율보다 우선되는 계산 계약
- KRX KOSPI/KOSDAQ/KONEX 일별매매정보 provider 요청·응답 매핑
- KRX 과거 시세 수집 service의 stock master 필터링, 시장별 실패 격리, DB 저장 계약
- KRX 수집 실패 시 KIS 일봉 chart API로 기준일 데이터를 보강 저장하는 계약과 provider rate limit pacing
- `KIS_DAILY_CHART` provider 모드에서 KRX를 호출하지 않고 KIS 일봉 chart API만으로 수집 성공을 반환하는 계약
- 저장된 KRX row가 없을 때 KIS 일봉 chart API로 history를 보강하는 계약
- KIS REST 현재가와 호가 API가 `EGW00201` 초당 제한을 반환하면 backoff 후 재시도하는 계약
- 과거 시세 history API의 공동 응답 envelope과 OHLCV/거래대금 payload
- Naver News Search 응답 정규화
- OpenDART 공시검색 응답 매핑
- DeepL 번역 요청 계약과 응답 매핑
- 알림 제목 번역 DeepL 우선, 번역 장애 시 원문 fallback
- `scripts/build_deepl_translation_smoke_report.py`가 DeepL live smoke 결과와 Papago `legacy_disabled` 상태를 secret 없이 `reports/deepl-translation-smoke-report.json`에 기록하는지 검증
- Hannah-Montana-AI 분석 클라이언트 계약
- 외부 provider 공통 timeout 설정 기본값
- 외부 provider 재시도, circuit open, 비네트워크 예외 no-retry 정책
- 수집된 뉴스·공시의 AI 분석 후 WebSocket 알림 발행
- AI 분석 `duplicateKey`, `modelVersion` 추적 메타데이터 전파
- AI 분석 `glossaryTerms`, `translationQualityFlags` 번역 품질 메타데이터 전파
- AI 분석 confidence 메타데이터 전파
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
- tax refund case sync API의 공동 응답 envelope, 입력 validation, 환급/선지급 상태 판정
- tax treaty case classification API의 CASE_01 판정, 수동 검토 사유, 입력 validation
- tax rectification batch status API의 분기 window, 진행 상태, case count, path variable validation
