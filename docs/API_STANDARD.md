# API Standard

모든 비즈니스 REST API는 공통 응답 envelope를 사용한다. Actuator health처럼 인프라가 직접 소비하는 운영 endpoint는 예외로 둔다.

## Success Response

```json
{
  "success": true,
  "status": 200,
  "code": "COMMON_000",
  "message": "OK",
  "data": {},
  "timestamp": "2026-06-18T00:00:00Z"
}
```

## Error Response

```json
{
  "success": false,
  "status": 400,
  "code": "COMMON_002",
  "message": "Request validation failed",
  "errors": [
    {
      "field": "getQuote.stockCode",
      "reason": "must match \"\\d{6}\""
    }
  ],
  "timestamp": "2026-06-18T00:00:00Z"
}
```

## Error Codes

| Code | HTTP | Meaning |
| --- | --- | --- |
| `COMMON_000` | 200 | Success |
| `COMMON_001` | 400 | Invalid request |
| `COMMON_002` | 400 | Validation failed |
| `COMMON_003` | 404 | Resource not found |
| `COMMON_999` | 500 | Internal server error |
| `AUTH_001` | 401 | Invalid API key |
| `AUTH_002` | 503 | API key hash is not configured |

## Swagger

- Swagger UI: `/swagger-ui/index.html`
- OpenAPI JSON: `/v3/api-docs`
- Server-to-server 인증은 `X-HANA-OMNILENS-API-KEY` header로 문서화한다.

## Foreign Ownership History

- `POST /api/v1/market/foreign-ownership/collect`
- 전일 또는 지정 기준일의 KRX Data Marketplace 외국인 보유 snapshot을 종목별로 수집해 `foreign_ownership_daily_snapshot`에 upsert한다.
- `POST /api/v1/market/foreign-ownership/backfill`
- `fromDate`부터 `toDate`까지 이미 저장된 날짜를 제외하고 비어 있는 평일만 과거 provider에서 조회해 저장한다.
- 과거 provider가 비어 있으면 현재 snapshot을 과거 날짜로 복제하지 않고 `PROVIDER_EMPTY`, `PARTIAL`, `FAILED` 상태로 반환한다.
- 시장 quote/detail의 `marketDataTime`은 응답 생성 시각이 아니라 실제 가격의 체결·수집 시각이다. 휴장일 fallback은 가장 최근 정규장 분봉을 사용하며, `source`로 `KIS_INTRADAY_PRICE_SNAPSHOT`을 반환한다.
- history는 최근 7일 요청 범위 안의 저장된 정규장 분봉을 실제 OHLCV로 집계할 수 있으며, 이 경우 `source`는 `KIS_REALTIME_TRADE_DAILY_AGGREGATE`다.

## Global Peer Matching

- `GET /api/v1/market/stocks/{stockCode}/global-peers`
- 종목 상세 화면에서 피어 종목 보기 버튼을 눌렀을 때 호출하는 API다.
- 응답은 headline, summary, primary peer, 기존 peers, comparisons, keyStrengths, confidence, model version, source를 포함한다.
- 각 peer는 `sector`, `industry`, `businessModel`, `scaleBucket`, `marketCapUsd`, `revenueUsd`, `operatingIncomeUsd`, `netIncomeUsd`, `financialDataSource`, `financialSimilarityScore`, `matchedFactors`, `rationale`을 포함한다.
- 정상 AI 응답의 `comparisons`는 1~3개이며 `dimension`, 비교 설명, 기존 peer와 같은 구조의 `peer`를 포함한다. `dimension`은 서버 allowlist 값만 허용한다.
- 정상 AI 응답의 `keyStrengths`는 정확히 4개이며 해당 국내 종목 자체의 강점을 설명하는 `title`, `description`, `iconKey`를 포함한다. `iconKey`는 앱 자산과 합의한 allowlist 값만 허용한다.
- Hannah의 `key_strengths[].icon_key` snake_case 계약은 OmniLens가 검증한 뒤 `keyStrengths[].iconKey` camelCase로 변환한다.
- 정상 경로는 Hannah-Montana-AI 글로벌 피어 모델이다. AI 장애나 계약 위반은 `MARKET_DATA_UNAVAILABLE`로 반환하며 임의 peer fallback을 만들지 않는다.

## Tax Document OCR

- `POST /api/v1/tax/documents/verify`
- 원본 파일 바이트와 문서 유형을 Hannah-Montana-AI의 공용 템플릿·영역 OCR 파이프라인으로 전달한다.
- 내부 사용자·계정 ID는 문서에 존재해야 하는 OCR 필드로 전달하지 않으며, 국가·문서 자체 필드와 진위 검사 결과만 검증 근거로 사용한다.

## Korean Financial Local Term Explanation

- `POST /api/v1/korean-financial-terms/explain`
- 뉴스·공시 본문에서 사용자가 클릭한 한국 금융 고유어·전문용어를 외국인 투자자가 이해할 수 있는 영어 해설로 반환한다.
- `개미`는 문장 번역에서 자연스러운 `Ants`, glossary 라벨에서는 정규형 `Ant`로 제공하며 `Retail investors`로 일반화하지 않는다.
- 정상 경로는 Hannah-Montana-AI 단일 검증 사전이며, 응답은 explanation, evidence, confidence, display mode, cacheable flag를 포함한다.
- OmniLens는 검증된 Hannah 응답만 TTL cache에 저장하고, confidence가 낮은 신조어는 review 대상 상태로 반환한다.
- `GET /api/v1/korean-financial-terms/stats`
- 클릭 통계는 salted SHA-256 사용자/세션 해시만 저장해 인기 용어와 검수 후보를 집계한다.
