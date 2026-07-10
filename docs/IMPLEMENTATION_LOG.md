# 구현 기록

## 2026-07-11 로컬 Qwen 4B 번역 계약 고정

- 뉴스·공시 한국어 번역 provider를 Hannah의 `/api/v1/translation/ko-en` 단일 경로로 고정했다.
- model version 예시와 테스트를 `local-llm:Qwen3-4B-GGUF-Q4`로 최신화했다.
- OpenAI/DeepL 비교 smoke 스크립트와 정적 리포트, 관련 credential 문서를 삭제했다.
- provider 장애나 품질 실패는 원문과 source-language fallback 상태로 전달한다.

## 2026-07-08 세무 OCR 검증

- 거주자 증명서, Apostille, 제한세율 적용신청서의 순차 업로드 계약을 Hannah OCR 검증과 연결했다.
- 예상 투자자 ID를 클라이언트가 주입하던 계약을 제거하고 OCR 추출 필드로 일관성을 검증한다.
- 이미지/PDF magic byte, MIME, 확장자와 12MB multipart 제한을 적용한다.

## 2026-07-03 글로벌 피어 계약

- 글로벌 비교 3개 차원과 핵심 강점 4개를 Hannah 응답에서 그대로 전달한다.
- 핵심 강점은 title, description, iconKey를 포함한다.
