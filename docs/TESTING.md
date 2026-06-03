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

## 추가 예정
- WebSocket subscription 계약 테스트
- 외부 API 어댑터 contract test
- 입력 validation 실패 케이스
- rate limit 정책 테스트
- 장애 상황 fallback 테스트
