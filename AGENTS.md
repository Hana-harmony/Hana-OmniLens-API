# AGENTS.md

## 공통 지침
- 업계 실제 서비스에서 사용하는 최신 안정 방식과 보안 기준을 우선 적용한다.
- 시크릿, API key, 인증 토큰, 외부 API credential은 코드와 문서 예시에 원문으로 남기지 않는다.
- 주석은 한글로 작성하되 중요한 로직 설명만 짧게 남긴다.
- 불필요한 추상화보다 레포의 현재 구조와 명확한 경계를 우선한다.
- 변경 후 가능한 범위에서 테스트와 정적 검증을 실행하고 결과를 기록한다.

## 서비스 경계
- 이 레포는 Hana OmniLens API 서버다.
- 한국 주식 시장 데이터, 매매제한 판단 데이터, 뉴스·공시 인텔리전스, 세무 환급 상태를 협력사에 제공하는 B2B API를 구현한다.
- 주문 도메인은 실제 주문 명령이 아니라 현지 거래소 주문 화면이 참고할 외국인 한도, 당일 예측 지분율, VI, 상·하한가, 모의 주문 가능성 판단 데이터를 제공한다.
- 실제 주문 실행, 체결, 정산, 환전, 최종투자자 계정 관리는 현지 거래소·브로커 또는 별도 원장 시스템 책임으로 둔다.
- AI 분석 모델 학습과 추론 로직은 Hannah-Montana-AI 레포 책임이다.

## 구현 원칙
- Java 17, Spring Boot 3.5.x, Gradle Wrapper 기준을 유지한다.
- 로컬 시크릿은 gitignore된 `application-local.yml`에만 둔다.
- 운영 설정 파일 `application-prod.yml`은 커밋하고, 민감값은 GitHub Secrets가 만든 원격 서버 env 파일로 주입한다.
- 운영 배포는 GHCR 이미지 push/pull과 Docker Compose 재시작 흐름을 사용한다.
- 외부 API는 KIS, KRX, 한국수출입은행, Naver News, OpenDART, Papago, DeepL 어댑터로 분리한다.
- 파생 계산은 외국인 보유율 캐시, 당일 예측 boundary, VI/제한가격 상태, 세무 환급 상태처럼 출처와 계산 버전을 응답에 남긴다.
- 협력사 인증은 원문 API key 저장 없이 해시 비교 또는 더 강한 방식으로 확장한다.
- REST/WebSocket 계약 변경 시 문서와 테스트를 함께 갱신한다.

## 필수 확인
- `./gradlew test --no-daemon`
- `./gradlew bootJar --no-daemon`
