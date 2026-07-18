# 구현 기록

## 2026-07-19 · 거래소 HMAC 상호운용 readiness 고정

- 보호 REST·WebSocket 요청의 API key, UTC timestamp, 192-bit nonce, HMAC-SHA256 계약을 공개 OpenAPI와 보안·배포 문서에 일치시켰다.
- `GET /api/v1/partner/readiness`가 다른 비즈니스 API와 같은 인증 필터를 통과한 뒤 `hmac-sha256-v1` 계약 버전을 반환하도록 추가했다.
- Stock-exchange-BE와 동일한 고정 서명 벡터를 검증해 canonical request 또는 header 규격이 한쪽에서 바뀌면 두 저장소 CI가 실패하도록 했다.
- 거래소 readiness가 서명된 호출 결과를 포함하므로 뒤처진 이미지나 잘못된 키가 실행 중인 상태를 정상으로 보고하지 않는다.

## 2026-07-15 · K-FNSPID v4 출처별 시장영향 전문가 전파

- Hana Montana AI(KF-DeBERTa + K-FNSPID)가 뉴스와 공시 요청을 각각 전용 시장영향 전문가로 라우팅하고 출처 불일치 추론을 거부하도록 변경했다.
- K-FNSPID v4는 뉴스 524,696건·공시 722,989건, 총 1,247,685문서와 파일 기반 일별 시세 10,691,998행을 사용한다. OmniLens 운영 DB의 `market_daily_price`를 학습 원천으로 연결하지 않는다.
- 시간 Test에서 뉴스 전문가는 9,560건 macro F1 0.3745 / QWK 0.4754, 공시 전문가는 4,615건 macro F1 0.3216 / QWK 0.1550을 기록했다. 두 출처 모두 자체 TF-IDF 기준선보다 높고 거래일 군집 부트스트랩 95% CI가 0보다 크다.
- 공시 TF-IDF 기준선은 독립 배포 gate를 통과하지 못하므로 공시 Transformer 장애 시 부적격 기준선으로 후퇴하지 않고 시장영향 필드를 생략한다. 의미 중요도와 나머지 분석은 계속 제공한다.
- API 계약 필드는 변경하지 않고 요청 출처별 복합 `modelVersion`과 시장영향 3개 필드를 REST·STOMP·raw WebSocket에 무손실 전파한다.

## 2026-07-14 · 경정청구서 PDF 직접 편집 계약

- Stock-exchange-BE가 발급하는 세무 신청 ID 규격인 `TAX-` + 영숫자 12자리를 OmniLens 동기화·관리자 API 전체에 동일하게 적용했다.
- 메서드 경로 검증 예외를 공통 400 envelope으로 처리해 잘못된 ID가 `Internal server error`로 노출되지 않게 했다.
- 서버의 신뢰된 2쪽 PDF 양식을 PNG로 렌더링하고, PDF 출력과 동일한 필드 좌표·크기·한글 라벨을 관리자 전용 API로 제공한다.
- 웹 편집 입력과 최종 PDF 생성이 같은 좌표 목록을 사용하며 템플릿 페이지, 좌표, 12자리 실제 신청 ID를 통합 테스트로 고정했다.

## 2026-07-13 · Hana Montana AI(KF-DeBERTa + K-FNSPID) v3 문서 동기화

- 서비스 모델명을 `Hana Montana AI(KF-DeBERTa + K-FNSPID)`로 통일했다.
- K-FNSPID v3의 550,662문서·10,691,998행 일별 시세·공시 원문 보유 문서 8,972건은 Hannah 저장소 정본으로 유지한다. 내부 운영 공시 4건을 더한 전체본문 학습자료는 공시 8,976건이며, 기본 뉴스/공시 Gold 680건과 학습 비중복 공시 stress Gold 310건을 별도로 검증한다.
- 시장영향 KF-DeBERTa는 Validation 전용 class-prior 보정을 포함하며 OmniLens는 보정 완료된 독립 시장영향 필드와 복합 모델 버전을 그대로 전파한다.
- seed 17/42/73 중 Validation으로 선택된 seed 73 시장영향 모델의 시간 Test 10,750건 성능은 accuracy 0.5095 / macro F1 0.3820 / QWK 0.4694이며, OmniLens는 이 버전을 파싱하거나 축약하지 않는다.
- 공시 의미 중요도는 Gold를 보지 않고 2026 Validation의 macro F1·Brier score로 제목+요약 뷰를 선택한다. 모델 단독 기본 Gold 600건은 accuracy 0.9850 / macro F1 0.9470이고, 존속위험 정책을 포함한 기본+stress Gold 910건은 0.9989 / 0.9962다. 기존 로직 대비 정확도 차이 95% bootstrap CI [0.0747, 0.1132], macro F1 차이 CI [0.1420, 0.2132], McNemar p=1.14e-24다.
- OmniLens는 모델 학습이나 시세 dataset export를 구현하지 않고 Hannah의 복합 `modelVersion`과 분석 결과를 무손실 전파한다.
- 의미 중요도와 예측 가격충격을 분리해 `importance`와 `marketImpactImportance/Score/Confidence`로 각각 전파한다.
- 정적 OpenAPI에 전문·What/Why/Impact·본문 가용상태·중복 cluster와 세 시장영향 필드를 모두 반영하고 문서 회귀 테스트로 고정했다. 거래소 백엔드용 raw WebSocket에도 시장영향·confidence·복합 모델 출처를 추가해 REST·STOMP·raw stream이 같은 신호를 보존한다.
- 시장영향 등급·점수·confidence는 모두 제공하거나 모두 생략하도록 요청 검증을 추가하고, 미제공 raw WebSocket 등급은 빈 문자열이 아닌 `null`로 전파한다.
- AI provider 응답 생성 경계에서도 시장영향 3개 필드의 all-or-none, 등급, 0~1 범위를 검증해 내부 생성 경로가 Bean Validation을 우회하지 못하게 했다.

## 2026-07-13 · KF-DeBERTa·K-FNSPID 복합 모델 출처 전파

- Hannah의 이벤트·KF-DeBERTa 감성·K-FNSPID 시장영향 복합 `modelVersion`을 파싱·축약하지 않고 REST·WebSocket에 전파한다.
- 복합 출처가 잘리지 않도록 alert 계약의 최대 길이를 240으로 늘리고 OpenAPI·역직렬화 회귀 테스트를 맞췄다.
- 대용량 시세·라벨 파일은 Hannah 저장소의 파일 데이터셋으로 유지하고 OmniLens DB export 의존을 추가하지 않았다.

## 2026-07-13 · K-FNSPID 중요도 모델 버전 전파

- Hannah가 품질 gate를 통과한 파일 기반 K-FNSPID 시장영향 모델을 의미 기반 중요도와 결합한다.
- OmniLens는 복합 `modelVersion`을 변경하지 않고 뉴스 REST/WebSocket 계약에 전파한다.
- K-FNSPID 시세는 독립 파일 스냅샷으로 생성하며 OmniLens 운영 DB export endpoint를 만들지 않는다.

## 2026-07-13 · 연결형 포털·세무·뉴스 운영 완성

- 회원·관리자 API 키 취소, 승인·반려, 재발급, 폐기 상태 전이를 실제 credential rotation·비활성화와 연결했다.
- 금융 고유어 설명 클릭을 거래소 화면에서 호출하고 일·월·년·전체 날짜 시계열로 집계하도록 포털 분석 계약을 추가했다.
- 거래소 원본 세 문서를 hash 검증 후 보관·열람하고 Hannah OCR 공통 필드를 서버 고정 경정청구서 양식에 적용해 저장·다운로드·승인하도록 변경했다. 수익 추정값은 포털 계약에서 제거했다.
- 이미 본문이 저장된 뉴스도 대표 이미지가 비어 있으면 원문 metadata를 다시 수집해 저장하도록 상세 조회 복구 경로를 보강했다.
- 경정청구서 출력은 실제 A4 양식의 행·열 좌표와 날짜의 년·월·일 칸을 기준으로 시각 검증해 라벨·첨부서류 영역 침범을 제거했다.

## 2026-07-13 · 세무 환급 케이스 동기화 금액 타입 보정

- Stock-exchange-BE의 문자열 금액 계약은 API 경계에서 최대 16자리·소수 2자리의 0 이상 값으로 검증한다.
- PostgreSQL `numeric` 컬럼에는 `BigDecimal`로 바인딩해 신청 건 생성·갱신이 동일한 타입 계약을 사용한다.
- 회귀 테스트는 저장소에 금액이 문자열이 아닌 `BigDecimal`로 전달되는지 검증한다.

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
## 2026-07-15 · 감성 평가 프로토콜 동기화

- 공개 재현 Test 932건의 KF-DeBERTa LoRA macro F1 0.8849와 KR-FinBERT-SC 0.7266은 동일 표본의 재현 비교로 기록한다.
- 공개 Test의 과거 반복 조회를 명시하고 독립 SOTA 주장에 사용하지 않는다.
- 실제 뉴스 Gold 정확도 0.8625가 운영 gate 0.90에 미달한 신규 후보는 승격하지 않는 fail-closed 정책을 운영 문서에 반영했다.
