# API skeleton: plant context + dynamic-form metadata

Branch `feat/api-plant-context-skeleton` (based on `main`), plus follow-up
`feat/api-plant-context-followup` (PR#4 review + PR#5 alignment).

## What landed

- **Business context API**: prefer **`CurrentContext.requirePlantId()`** / `plantId()` / `userId()`.
  `PlantContext` remains the ThreadLocal store written by the filter (and later JWT).
- **Auth mode** (`mes.auth.mode`, default `header`):
  - `header` — read `X-Plant-Id` / `X-User-Id` (dev/skeleton only).
  - any other value (e.g. future `jwt`) — **ignore** those headers.
  - **After JWT lands, never trust a client-supplied `X-Plant-Id`.**
- Missing `X-Plant-Id` does not fail the filter; business uses `CurrentContext.requirePlantId()`
  → `PlantRequiredException` → HTTP 400 **`PLANT_REQUIRED`** (RFC 9457 Problem Details).
- Present-but-blank or multi-value `X-Plant-Id` → HTTP 400 `PLANT_HEADER_INVALID` (`application/problem+json`).
- Errors from `ApiExceptionHandler`: `ProblemDetail` with `code`, `errors[]`, `traceId`.
- `PlantContext.set` rejects null/blank; ThreadLocal **does not cross async**.
- **MyBatis plant line**: `common.persistence.PlantLineInnerInterceptor` (V1 slice 1) — shared tables
  `plant_id IS NULL OR plant_id = :p`, plant-owned / `*_plant_ext` `plant_id = :p`, registry in `PlantScope`.
- **Dynamic-form stub**: whitelist write-config, RE2J `pattern`, `span` 1–24, unique options,
  non-blank labels, `fields.length` ≤ 50, **`fields: []` allowed**, `schemaVersion` default 1.
  Schema failures → HTTP 422 Problem Details with `errors[{field,code,message}]`.

## Follow-up vs original skeleton

| Topic | Convention |
| --- | --- |
| Header trust | Only `mes.auth.mode=header` |
| Missing plant | 400 `PLANT_REQUIRED` (not `PLANT_NOT_SET`) |
| Invalid header | 400 `PLANT_HEADER_INVALID` |
| Error body | RFC 9457 `application/problem+json` |
| Business context API | `CurrentContext` → `PlantContext` |
| Schema write | Whitelist; empty `fields` OK; `SCHEMA_*` / `PATTERN_*` |
| Row filter | `PlantLineInnerInterceptor` (slice 1) |

## Test (no Postgres)

```bash
source /home/box/.mes-java.env
cd apps/api
./mvnw -B -Dtest=PlantContextTest,PlantContextFilterTest,DynamicFormSchemaValidatorTest,PlantContextMvcTest test
./mvnw -B -DskipTests package
```

Since V1 slice 1 the full suite (`./mvnw -B test`) runs on an embedded PostgreSQL 17
(`io.zonky.test:embedded-postgres`), no Docker needed.
