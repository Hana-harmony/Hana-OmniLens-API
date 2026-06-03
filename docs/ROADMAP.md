# 구현 로드맵

## M1 API 계약 안정화
- OpenAPI 문서 추가
- 협력사 인증 정책 확정
- REST/WebSocket 계약 테스트 추가

## M2 시장 데이터 어댑터
- KIS 현재가 REST 연동
- KIS 실시간 체결가·호가 WebSocket 연동
- 종목 마스터 DB 적재
- 외국인 보유율 전일 캐시
- 환율 캐시

## M3 뉴스·공시 알림
- Naver News Search 수집 endpoint 연결 완료
- OpenDART 공시 수집 endpoint 연결 완료
- 번역 공급자 어댑터
- Hannah-Montana-AI 분석 API 연동 완료
- Redis 또는 DB 기반 중복 제거와 재전송 방지
- 협력사별 watchlist 기반 주기 수집 스케줄러

## M4 운영 하드닝
- 협력사별 rate limit
- mTLS 또는 서명 기반 인증
- 감사 로그와 장애 추적
- 배포 환경 분리
