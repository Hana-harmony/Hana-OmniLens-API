# 구현 로드맵

전체 구현 순서와 단계별 완료 기준은 `docs/IMPLEMENTATION_SEQUENCE.md`를 따른다.

## M1 API 계약 안정화
- OpenAPI 문서 추가 완료
- 협력사 인증 정책 확정
- WebSocket subscription 계약 테스트 완료

## M2 시장 데이터 어댑터
- KIS 현재가 REST 연동 완료
- KIS 실시간 체결가·호가 WebSocket 계약·파서·캐시 하네스 완료
- KIS 실시간 체결가·호가 WebSocket session runner 완료
- 단건 quote snapshot API 구현 완료
- 전체/다건 quote snapshot API 구현 완료
- 협력사용 market quote WebSocket stream 구현 완료
- 종목 마스터 DB 적재 완료
- 종목 마스터 단건 조회 REST 계약 완료
- 외국인 보유율 프로세스 캐시와 장애 fallback 완료
- 외국인 보유율 Redis TTL cache와 in-memory fallback 완료
- 협력사 입력 환율 프로세스 캐시 완료
- Frankfurter 환율 provider adapter와 cache refresh service 완료
- Frankfurter 환율 주기 refresh scheduler 완료
- 환율 Redis TTL cache와 in-memory fallback 완료
- KRX 모든 국내 주식 과거 시세 수집·정규화·DB 저장·history API 완료

## M3 뉴스·공시 알림
- Naver News Search 수집 endpoint 연결 완료
- OpenDART 공시 수집 endpoint 연결 완료
- DeepL 번역 공급자 어댑터 완료
- Hannah-Montana-AI 분석 API 연동 완료
- Redis TTL 기반 중복 제거 완료
- 협력사별 watchlist 기반 주기 수집 스케줄러 완료
- 협력사별 watchlist DB 관리 API 완료
- full-content news v2: DB 이벤트 저장소, REST 목록·상세, v2 필드 포함 저장 이벤트 WebSocket, Hannah 전문 기반 분석·What/Why/Impact 요약 모델 연동, 사용 허가 원문 전문·이미지 수집, OpenDART document 전문 수집, DeepL 전문 chunk 번역 완료. 전체 종목 shard 운영 최적화와 provider별 권리 확인은 지속

## M4 운영 하드닝
- 협력사별 rate limit 완료
- 협력사별 API key registry와 partnerId 접근 제한 완료
- bootstrap 운영 키 기반 협력사별 API key rotation 완료
- WebSocket topic authorization 세분화 완료
- 서명 기반 인증과 Redis nonce replay 방어 완료
- mTLS client certificate gate 완료
- 감사 로그와 장애 추적 correlation id 완료
- 외부 provider timeout, retry, circuit breaker 완료
- Redis Testcontainers 통합 연결 하네스 완료
- 배포 환경 분리 완료
