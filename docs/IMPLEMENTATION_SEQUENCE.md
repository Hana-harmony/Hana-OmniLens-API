# 변경 구현 순서

이 문서는 Hana-Omni-Connect-API 변경 시 적용할 저장소별 순서다. 전체 기능 설명은 README를 기준으로 한다.

1. KIS·KRX·뉴스·공시·환율·Hannah 계약과 소유 저장소를 확인한다.
2. `api` request/response와 오류 코드를 먼저 고정하고 controller test를 작성한다.
3. 조합 로직은 `application`, 외부 호출은 `provider`, 영속화는 JDBC repository와 Flyway migration에 구현한다.
4. API key hash, rate limit, signature nonce, mTLS, correlation ID와 감사 로그에 미치는 영향을 확인한다.
5. REST/WebSocket/OpenAPI 계약과 README·운영·보안·테스트 문서를 함께 갱신한다.
6. `./gradlew test --no-daemon`과 `./gradlew bootJar --no-daemon`을 실행한다.
7. `feature`에서 작업 브랜치를 만들고 PR 체크 통과 후 `feature`, 이어서 `main`에 병합한다.

세무 문서는 거주자 증명서·아포스티유·제한세율 적용신청서 유형을 유지하고, 원본·사용자 식별자를 로그에 기록하지 않는다.
