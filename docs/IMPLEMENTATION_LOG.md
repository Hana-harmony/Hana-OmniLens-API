# 구현 기록

## 2026-07-11 KIS 활성 종목 스냅샷

- `stock_master`에 `active`, `master_synced_at`을 추가했다.
- KOSPI·KOSDAQ·KONEX 마스터를 시장별 트랜잭션으로 reconcile해 상장폐지·합병·이전상장 종목이 현재 검색과 AI universe에 남지 않게 했다.
- 한 시장의 다운로드 실패가 다른 시장 스냅샷을 무효화하지 않도록 실패 경계를 분리했다.

## 2026-07-11 글로벌 피어 더미 fallback 제거

- Hannah AI 피어 호출 실패 시 `MSFT`, `HALO` 등을 임의 반환하던 fallback을 삭제했다.
- 정상 `HANNAH_GLOBAL_PEER_HYBRID_RANKER` 응답만 1~3개 comparison과 4개 Key Strength를 전달하며, AI 장애는 `MARKET_DATA_UNAVAILABLE`로 종료한다.

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
