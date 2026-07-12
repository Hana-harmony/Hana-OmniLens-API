# 구현 기록

## 2026-07-12 21:20 KST · 웹 포털 RBAC와 경정청구 승인

- 포털 CORS에 `Authorization`을 허용해 브라우저 회원·관리자 API preflight 차단을 해소했다.
- 포털 권한을 Spring Security `ROLE_ADMIN` RBAC로 강제하고, Flyway 초기 `admin` 계정·강제 비밀번호 변경·세션 버전 폐기를 추가했다.
- Hannah AI 검증 서류의 추출 값을 경정청구서 필드로 매핑하고, 수동 편집된 PDF와 SHA-256를 저장한 뒤에만 `REFUND_APPROVED`로 전이하게 했다.
- 실기동 검증에서 발견한 Spring Boot 기본 메모리 사용자 자동설정을 제거해 인증 경로를 DB 포털 계정과 파트너 API key로 한정했다.

## 2026-07-11 14:07 KST · KIS 활성 종목 스냅샷

- `stock_master`에 `active`, `master_synced_at`을 추가했다.
- KOSPI·KOSDAQ·KONEX 마스터를 시장별 트랜잭션으로 reconcile해 상장폐지·합병·이전상장 종목이 현재 검색과 AI universe에 남지 않게 했다.
- 한 시장의 다운로드 실패가 다른 시장 스냅샷을 무효화하지 않도록 실패 경계를 분리했다.

## 2026-07-11 09:34 KST · 글로벌 피어 더미 fallback 제거

- Hannah AI 피어 호출 실패 시 `MSFT`, `HALO` 등을 임의 반환하던 fallback을 삭제했다.
- 정상 `HANNAH_GLOBAL_PEER_HYBRID_RANKER` 응답만 1~3개 comparison과 4개 Key Strength를 전달하며, AI 장애는 `MARKET_DATA_UNAVAILABLE`로 종료한다.

## 2026-07-11 05:08 KST · 로컬 Qwen 4B 번역 계약 고정

- 뉴스·공시 한국어 번역 provider를 Hannah의 `/api/v1/translation/ko-en` 단일 경로로 고정했다.
- model version 예시와 테스트를 `local-llm:Qwen3-4B-GGUF-Q4`로 최신화했다.
- provider 장애나 품질 실패는 원문과 source-language fallback 상태로 전달한다.

## 2026-07-08 18:51 KST · 세무 OCR 검증

- 거주자 증명서, Apostille, 제한세율 적용신청서의 순차 업로드 계약을 Hannah OCR 검증과 연결했다.
- 예상 투자자 ID를 클라이언트가 주입하던 계약을 제거하고 OCR 추출 필드로 일관성을 검증한다.
- 이미지/PDF magic byte, MIME, 확장자와 12MB multipart 제한을 적용한다.

## 2026-07-03 · 글로벌 피어 계약

- 글로벌 비교 3개 차원과 핵심 강점 4개를 Hannah 응답에서 그대로 전달한다.
- 핵심 강점은 title, description, iconKey를 포함한다.
