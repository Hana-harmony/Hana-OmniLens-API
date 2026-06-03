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
- health endpoint 공개
- 시장 데이터 응답 계약
- 공공데이터 주식시세 provider 성공·fallback
- Naver News Search 응답 정규화
- OpenDART 공시검색 응답 매핑
- Hannah-Montana-AI 분석 클라이언트 계약
- 수집된 뉴스·공시의 AI 분석 후 WebSocket 알림 발행
- provider 수집 결과의 중복 URL 재발행 방지

## 추가 예정
- WebSocket subscription 계약 테스트
- 입력 validation 실패 케이스
- rate limit 정책 테스트
- Redis 또는 DB 기반 저장형 dedupe 테스트
