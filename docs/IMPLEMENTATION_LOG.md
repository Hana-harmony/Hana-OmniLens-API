# 구현 기록

## 2026-07-22 · OpenDART 공시 영속 작업 큐

- OpenDART 검색 결과를 Qwen 호출 전에 `disclosure_processing_job`에 멱등 등록하고, 공식 원문을 가져온 즉시 작업 레코드에 영속화한다.
- Qwen timeout·품질 gate·일시 provider 장애는 공시를 폐기하지 않고 시도 횟수와 다음 실행 시각을 보존한 `RETRY`로 전이한다. 만료된 처리 lease도 자동 회수한다.
- 전체 품질 gate를 통과한 작업만 `alert_event`와 WebSocket으로 승격하며, 기존의 조회·원문 다운로드·Qwen·저장을 한 요청에서 처리하던 동기 폐기 경로를 제거했다.

## 2026-07-21 · Qwen 단일 기사 처리와 공시 재수집 복구

- 제목, What/Why/Impact, 영문 전문은 Hannah `alerts/analyze`의 같은 Qwen 분석 결과만 사용하며 개별 번역 API, 규칙 요약, 제목별 하드코딩 보정으로 후퇴하지 않는다.
- Qwen 실패 또는 품질 gate 실패 시 레코드를 완성된 것처럼 보정하지 않고 게시·재처리를 실패 상태로 유지한다.
- V26 마이그레이션이 영문 전문이 없는 과거 공시를 제거하고 Redis 수집·AI dedupe namespace를 `v2`로 교체해 기존 24시간 키에 막히지 않고 공식 OpenDART 원문부터 재수집한다.
- 수집 진척도와 기존 문서 판정은 전체 행이 아니라 완성된 영문 What/Why/Impact와 전문을 가진 이벤트만 계산한다.
- 종목 뉴스와 시장 뉴스 보강은 Qwen 동시 추론 한도 2개에 맞춰 서로 독립된 두 worker가 한 건씩 병렬 처리한다.

## 2026-07-21 · OpenDART 공시 피드 복구

- `document.xml`의 HTTP 200 오류 envelope를 공시 본문으로 저장하던 원인을 제거하고 `status != 000` 응답은 다음 수집 주기에 재시도한다.
- 과거 `014 파일이 존재하지 않습니다` 값을 본문으로 저장한 공시만 V25 마이그레이션으로 삭제해 실제 ZIP 원문이 준비된 뒤 재수집되게 했다.
- 종목별 수집 순서를 공시 우선으로 바꿔 Qwen 뉴스 처리 시간이 공식 공시 복구를 막지 않게 했다.

## 2026-07-21 · 상세 전문 번역 보장과 거래소 요청 제한 정책

- 뉴스·공시는 전체 영문 본문과 품질 gate를 완료한 후에만 목록·상세·WebSocket에 공개한다.
- OmniConnect와 Hannah의 이중 본문 분할을 제거했다. 현재는 종목 뉴스와 시장 뉴스를 독립 worker로 한 건씩 처리한다.
- 일반 협력사 요청 제한은 분당 120건으로 유지하고, 신뢰된 거래소 파트너만 DB 정책으로 rate limit을 면제한다. API key·HMAC 서명·nonce 검증은 동일하게 유지한다.

## 2026-07-21 · 운영 협력사 요청 제한과 Grafana 외부 링크 정합화

- 거래소 BE 한 개의 협력사 키에 여러 최종 사용자 요청이 집계되는 운영 구조를 반영해 production 한도를 분당 600개로 조정했다.
- 요청 제한 응답 코드를 인증 실패로 오해되는 `AUTH_003`에서 공통 제한 코드 `COMMON_004`로 분리했다.
- OCI Grafana 공식 외부 URL과 Nginx HTTPS 하위 경로를 `https://api.hanaomni.cloud/grafana/`로 고정해 Discord 오류 알림 링크의 `localhost` 생성을 제거했다.

## 2026-07-21 · API 요청 제한 만료시간 자동 복구

- Redis 요청 제한 카운터에 만료시간이 없는 기존 키가 남아도 다음 요청에서 현재 제한 구간의 만료시간을 원자적으로 다시 설정한다.
- 카운터 증가와 만료시간 복구를 하나의 Lua script로 처리해 동시 요청에서도 영구 누적 키가 생기지 않게 했다.
- 실제 Redis에 만료시간 없는 카운터를 만든 뒤 다음 요청에서 TTL이 복구되는 통합 테스트를 추가했다.

## 2026-07-21 · 후속 기사 Qwen 재분석

- 후속 작업도 제목, What/Why/Impact, 전문을 Hannah `alerts/analyze`의 Qwen 결과로 함께 갱신한다.
- 실패 결과를 기존 요약 번역이나 제목 하드코딩으로 보정하지 않고 Redis `SET NX` 유예 뒤 다시 시도한다.
- 완성된 전문이 없는 레코드는 목록·상세에 노출하지 않는다.

## 2026-07-20 · 뉴스·공시 초기 적재와 전체 본문 번역 분리

- 인기 종목과 외국인 취득한도 제한 종목의 주기 수집을 전체 본문 Qwen 번역 완료에서 분리했다.
- 시장뉴스 수집 단계는 완전한 원문·영문 What/Why/Impact를 내부 대기 레코드로 저장하고, 전문 번역 완료 전에는 외부에 노출하지 않는다.
- 종목 알림과 시장뉴스는 수집 중 `FULL` 번역을 완료한 건만 저장·발행한다. 상시 보강 worker는 제거했다.
- 한국 증시 시장뉴스는 완전한 원문과 전체 영문 본문이 완성되면 목록·상세에 노출하며 상세 조회에서 장시간 전체 번역을 실행하지 않는다.
- `FULL` 분석 결과의 제목·요약·본문 번역은 그대로 재사용해 시장뉴스의 중복 번역 호출을 제거했다.

## 2026-07-20 · 포털 API 키 상시 재조회와 신청자 식별

- 활성 API 키의 소유 회원이 현재 비밀번호를 재확인하면 횟수 제한 없이 같은 키를 다시 조회하도록 변경했다.
- 조회 뒤 포털 암호문을 삭제하던 1회성 동작을 제거하고, 응답의 `no-store` 정책과 소유권 검증을 유지했다.
- 관리자에게는 API 키 원문을 제공하지 않고 신청자 이름·아이디·기술용 파트너 ID를 분리해 반환한다.

## 2026-07-20 · 관리자 시작 비밀번호 제거

- 관리자 비밀번호를 GitHub Secret과 애플리케이션 시작 환경변수로 전달하던 경로를 제거했다.
- 시작 시 관리자 계정을 생성·변경하던 컴포넌트와 설정·배포·테스트 의존성을 삭제했다.
- 기존 Flyway 이력을 변경하지 않고 후속 마이그레이션에서 알려진 레거시 관리자 해시만 제거해 기존 운영 Argon2id 계정은 보존한다.
- 관리자는 제한된 OCI DB 운영 절차로만 생성·초기화하며 세션 버전 증가와 다음 로그인 비밀번호 변경을 강제한다.

## 2026-07-20 · OCI SSH 키·비밀번호 연속 인증

- 운영 배포 SSH가 고정 private key 인증 뒤 계정 비밀번호를 추가로 요구하는 OCI 정책을 지원한다.
- 비밀번호는 GitHub `production` 환경의 `PROD_SSH_PASSWORD`로만 받아 OpenSSH `SSH_ASKPASS`에 전달하며 파일·명령 인자·로그에 기록하지 않는다.
- 고정 host key, 전용 identity와 단일 password prompt를 강제한다. Docker 그룹 변경 뒤 새 로그인 권한이 반영되도록 SSH·SCP 연결은 재사용하지 않는다.

## 2026-07-20 · 포털 발급 API key 단일화

- 전역 API key hash fallback과 bootstrap 전용 협력사 key 강제 rotation API를 제거했다.
- 보호 REST·WebSocket 요청은 포털 승인·재발급으로 `partner_api_credential`에 저장된 활성 협력사 key만 인증한다.
- bootstrap 호환용 전역 WebSocket topic 발행을 제거하고 협력사별 topic만 허용한다.
- 초기 관리자 임시 비밀번호는 신규 DB 최초 기동에만 허용하고 최초 비밀번호 변경 후 GitHub Secret에서 삭제할 수 있도록 배포 검사를 변경했다.
- 테스트도 전역 설정 key 대신 실제 DB credential을 등록해 운영 인증 경로와 동일하게 검증한다.

## 2026-07-19 · 거래소 HMAC 상호운용 readiness 고정

- 보호 REST·WebSocket 요청의 API key, UTC timestamp, 192-bit nonce, HMAC-SHA256 계약을 공개 OpenAPI와 보안·배포 문서에 일치시켰다.
- `GET /api/v1/partner/readiness`가 다른 비즈니스 API와 같은 인증 필터를 통과한 뒤 `hmac-sha256-v1` 계약 버전을 반환하도록 추가했다.
- Stock-exchange-BE와 동일한 고정 서명 벡터를 검증해 canonical request 또는 header 규격이 한쪽에서 바뀌면 두 저장소 CI가 실패하도록 했다.
- 거래소 readiness가 서명된 호출 결과를 포함하므로 뒤처진 이미지나 잘못된 키가 실행 중인 상태를 정상으로 보고하지 않는다.

## 2026-07-15 · K-FNSPID v4 출처별 시장영향 전문가 전파

- Hana Montana AI(KF-DeBERTa + K-FNSPID)가 뉴스와 공시 요청을 각각 전용 시장영향 전문가로 라우팅하고 출처 불일치 추론을 거부하도록 변경했다.
- K-FNSPID v4는 뉴스 524,696건·공시 722,989건, 총 1,247,685문서와 파일 기반 일별 시세 10,691,998행을 사용한다. OmniConnect 운영 DB의 `market_daily_price`를 학습 원천으로 연결하지 않는다.
- 시간 Test에서 뉴스 전문가는 9,560건 macro F1 0.3745 / QWK 0.4754, 공시 전문가는 4,615건 macro F1 0.3216 / QWK 0.1550을 기록했다. 두 출처 모두 자체 TF-IDF 기준선보다 높고 거래일 군집 부트스트랩 95% CI가 0보다 크다.
- 공시 TF-IDF 기준선은 독립 배포 gate를 통과하지 못하므로 공시 Transformer 장애 시 부적격 기준선으로 후퇴하지 않고 시장영향 필드를 생략한다. 의미 중요도와 나머지 분석은 계속 제공한다.
- API 계약 필드는 변경하지 않고 요청 출처별 복합 `modelVersion`과 시장영향 3개 필드를 REST·STOMP·raw WebSocket에 무손실 전파한다.

## 2026-07-14 · 경정청구서 PDF 직접 편집 계약

- Stock-exchange-BE가 발급하는 세무 신청 ID 규격인 `TAX-` + 영숫자 12자리를 OmniConnect 동기화·관리자 API 전체에 동일하게 적용했다.
- 메서드 경로 검증 예외를 공통 400 envelope으로 처리해 잘못된 ID가 `Internal server error`로 노출되지 않게 했다.
- 서버의 신뢰된 2쪽 PDF 양식을 PNG로 렌더링하고, PDF 출력과 동일한 필드 좌표·크기·한글 라벨을 관리자 전용 API로 제공한다.
- 웹 편집 입력과 최종 PDF 생성이 같은 좌표 목록을 사용하며 템플릿 페이지, 좌표, 12자리 실제 신청 ID를 통합 테스트로 고정했다.

## 2026-07-13 · Hana Montana AI(KF-DeBERTa + K-FNSPID) v3 문서 동기화

- 서비스 모델명을 `Hana Montana AI(KF-DeBERTa + K-FNSPID)`로 통일했다.
- K-FNSPID v3의 550,662문서·10,691,998행 일별 시세·공시 원문 보유 문서 8,972건은 Hannah 저장소 정본으로 유지한다. 내부 운영 공시 4건을 더한 전체본문 학습자료는 공시 8,976건이며, 기본 뉴스/공시 Gold 680건과 학습 비중복 공시 stress Gold 310건을 별도로 검증한다.
- 시장영향 KF-DeBERTa는 Validation 전용 class-prior 보정을 포함하며 OmniConnect는 보정 완료된 독립 시장영향 필드와 복합 모델 버전을 그대로 전파한다.
- seed 17/42/73 중 Validation으로 선택된 seed 73 시장영향 모델의 시간 Test 10,750건 성능은 accuracy 0.5095 / macro F1 0.3820 / QWK 0.4694이며, OmniConnect는 이 버전을 파싱하거나 축약하지 않는다.
- 공시 의미 중요도는 Gold를 보지 않고 2026 Validation의 macro F1·Brier score로 제목+요약 뷰를 선택한다. 모델 단독 기본 Gold 600건은 accuracy 0.9850 / macro F1 0.9470이고, 존속위험 정책을 포함한 기본+stress Gold 910건은 0.9989 / 0.9962다. 기존 로직 대비 정확도 차이 95% bootstrap CI [0.0747, 0.1132], macro F1 차이 CI [0.1420, 0.2132], McNemar p=1.14e-24다.
- OmniConnect는 모델 학습이나 시세 dataset export를 구현하지 않고 Hannah의 복합 `modelVersion`과 분석 결과를 무손실 전파한다.
- 의미 중요도와 예측 가격충격을 분리해 `importance`와 `marketImpactImportance/Score/Confidence`로 각각 전파한다.
- 정적 OpenAPI에 전문·What/Why/Impact·본문 가용상태·중복 cluster와 세 시장영향 필드를 모두 반영하고 문서 회귀 테스트로 고정했다. 거래소 백엔드용 raw WebSocket에도 시장영향·confidence·복합 모델 출처를 추가해 REST·STOMP·raw stream이 같은 신호를 보존한다.
- 시장영향 등급·점수·confidence는 모두 제공하거나 모두 생략하도록 요청 검증을 추가하고, 미제공 raw WebSocket 등급은 빈 문자열이 아닌 `null`로 전파한다.
- AI provider 응답 생성 경계에서도 시장영향 3개 필드의 all-or-none, 등급, 0~1 범위를 검증해 내부 생성 경로가 Bean Validation을 우회하지 못하게 했다.

## 2026-07-13 · KF-DeBERTa·K-FNSPID 복합 모델 출처 전파

- Hannah의 이벤트·KF-DeBERTa 감성·K-FNSPID 시장영향 복합 `modelVersion`을 파싱·축약하지 않고 REST·WebSocket에 전파한다.
- 복합 출처가 잘리지 않도록 alert 계약의 최대 길이를 240으로 늘리고 OpenAPI·역직렬화 회귀 테스트를 맞췄다.
- 대용량 시세·라벨 파일은 Hannah 저장소의 파일 데이터셋으로 유지하고 OmniConnect DB export 의존을 추가하지 않았다.

## 2026-07-13 · K-FNSPID 중요도 모델 버전 전파

- Hannah가 품질 gate를 통과한 파일 기반 K-FNSPID 시장영향 모델을 의미 기반 중요도와 결합한다.
- OmniConnect는 복합 `modelVersion`을 변경하지 않고 뉴스 REST/WebSocket 계약에 전파한다.
- K-FNSPID 시세는 독립 파일 스냅샷으로 생성하며 OmniConnect 운영 DB export endpoint를 만들지 않는다.

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

## 2026-07-11 05:08 KST · 로컬 Qwen 4B 번역 계약 고정(폐기)

- 당시에는 뉴스·공시 한국어 번역을 Hannah의 개별 번역 경로로 고정했으나, 2026-07-21에 해당 경로와 클라이언트를 삭제하고 Qwen 기사 단일 분석으로 대체했다.
- model version 예시와 테스트를 `local-llm:Qwen3-4B-GGUF-Q4`로 최신화했다.
- 현재는 Qwen 기사 분석 품질 gate를 통과하지 못한 기사를 외부에 게시하지 않는다.

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
# 2026-07-21 · 전문 번역 완료 후 공개

- 장문 전문 번역을 상세 GET 수명에서 제거했다.
- 전문 미완료 레코드는 마이그레이션으로 제거하며 신규 수집에서는 저장하지 않는다. 목록·상세 조회는 번역 작업을 시작하지 않는다.
- 거래소 FE의 반복 재조회와 로딩 문구를 제거해 상세 진입 시 목록에 이미 포함된 영문 전문을 즉시 표시한다.

# 2026-07-21 · 지수 종가 fallback 전일 대비 보정

- 실전 KIS 지수 인증 장애 시 Yahoo 최근 종가 fallback이 등락값과 등락률을 0으로 고정하던 오류를 제거했다.
- 최근 5거래일 정규장 데이터와 저장 분봉에서 직전 거래일 종가를 찾아 실제 전일 대비를 계산한다.
- 직전 종가를 확보하지 못하면 `+0 / +0.00%`를 성공 데이터로 노출하지 않는다.
