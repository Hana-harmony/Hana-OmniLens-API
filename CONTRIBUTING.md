# 기여 가이드

## 개발 흐름
- 브랜치와 커밋 규칙은 [Git 전략](docs/GIT_STRATEGY.md)을 따른다.
- 일반 작업은 최신 `feature`에서 작업 브랜치를 생성한다.
- PR은 `feature` 대상으로 생성하고, 운영 반영은 `feature`에서 `main`으로 릴리스 PR을 만든다.

## 커밋 템플릿
```bash
git config commit.template .gitmessage.txt
```

## 변경 기준
- API 계약 변경은 README, 아키텍처 문서, 테스트를 함께 갱신한다.
- 보안 설정 변경은 보안 문서와 운영 문서를 함께 갱신한다.
- 외부 API 연동은 어댑터 경계를 지키고 credential을 환경 변수로만 주입한다.

## 로컬 검증
```bash
./gradlew test --no-daemon
./gradlew bootJar --no-daemon
```
