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

## Global Peer Match

- `GET /api/v1/market/stocks/{stockCode}/global-peers`
- 종목 상세 화면에서 피어 종목 보기 버튼을 눌렀을 때 호출하는 API다.
- 응답은 headline, summary, primary peer, peers, confidence, model version, source를 포함한다.
- 각 peer는 `sector`, `industry`, `businessModel`, `scaleBucket`, `matchedFactors`, `rationale`을 포함한다.
- 정상 경로는 Hannah-Montana-AI 글로벌 피어 모델이며, AI 장애 시 검증된 anchor fallback만 제한적으로 사용한다.
