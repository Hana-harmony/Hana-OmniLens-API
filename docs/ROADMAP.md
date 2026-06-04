# 구현 로드맵

## M1 API 계약 안정화
- OpenAPI 문서 추가 완료
- 협력사 인증 정책 확정
- WebSocket subscription 계약 테스트 완료

## M2 시장 데이터 어댑터
- KIS 현재가 REST 연동 완료
- KIS 실시간 체결가·호가 WebSocket 계약·파서·캐시 하네스 완료
- KIS 실시간 체결가·호가 WebSocket session runner 완료
- 종목 마스터 DB 적재 완료
- KRX 외국인 보유율 quote 연결 완료
- 외국인 보유율 프로세스 캐시와 장애 fallback 완료
- 외국인 보유율 Redis TTL cache와 in-memory fallback 완료
- 협력사 입력 환율 프로세스 캐시 완료
- 한국수출입은행 환율 provider adapter와 cache refresh service 완료
- 한국수출입은행 환율 주기 refresh scheduler 완료
- 환율 Redis TTL cache와 in-memory fallback 완료

## M3 뉴스·공시 알림
- Naver News Search 수집 endpoint 연결 완료
- OpenDART 공시 수집 endpoint 연결 완료
- Papago NMT 번역 공급자 어댑터 완료
- Hannah-Montana-AI 분석 API 연동 완료
- Redis TTL 기반 중복 제거 완료
- 협력사별 watchlist 기반 주기 수집 스케줄러 완료

## M4 운영 하드닝
- 협력사별 rate limit 완료
- 서명 기반 인증과 Redis nonce replay 방어 완료
- mTLS
- 감사 로그와 장애 추적 correlation id 완료
- 외부 provider timeout, retry, circuit breaker 완료
- 배포 환경 분리
