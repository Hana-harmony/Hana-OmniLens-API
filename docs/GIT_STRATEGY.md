# Git 전략

## 브랜치

- `main`: 운영 배포 기준
- `feature`: 검증된 개발 변경 통합
- 작업 브랜치: 최신 `feature`에서 생성한 `<type>/<kebab-case-description>`
- type: `feat`, `fix`, `hotfix`, `refactor`, `docs`, `test`, `security`, `chore`, `release`

```bash
git switch feature
git pull --ff-only origin feature
git switch -c docs/refresh-current-implementation
```

작업 브랜치 → `feature` PR을 먼저 병합한다. 체크 통과 후 `feature` → `main` PR을 병합한다. 보호 브랜치에 직접 push하지 않고 작업 브랜치만 병합 후 삭제한다.

## 커밋과 PR

- 커밋과 PR 제목: `type(scope): 한글 제목`
- 제목은 변경 목적을 50자 안팎으로 표현한다.
- 본문은 배경, 변경 사항, 검증 결과, 영향 범위, rollback을 실제 Markdown 줄바꿈으로 작성한다.
- 로컬 커밋 템플릿은 `.gitmessage.txt`, PR 템플릿은 `.github/PULL_REQUEST_TEMPLATE.md`를 사용한다.
- CI가 통과한 PR만 squash merge한다.

문서 변경도 코드 계약, 보안·민감정보와 링크 검사를 완료한 뒤 같은 흐름으로 병합한다.
