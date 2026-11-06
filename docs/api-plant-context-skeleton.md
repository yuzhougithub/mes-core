# API skeleton: plant context + dynamic-form metadata

Branch `feat/api-plant-context-skeleton` (based on `main`), plus follow-up
`feat/api-plant-context-followup` (PR#4 review hardenings).

## What landed

- **Plant context**: `PlantContext` (ThreadLocal `String` plantId) + `PlantContextFilter`.
  Prefer **`CurrentContext`** in business code (PR#5 façade; plantId + optional userId).
- **Auth mode** (`mes.auth.mode`, default `header`):
  - `header` — read `X-Plant-Id` / `X-User-Id` (dev/skeleton only).
  - any other value (e.g. future `jwt`) — **ignore** those headers; JWT must set context from the token.
  - **After JWT lands, never trust a client-supplied `X-Plant-Id`.**
- Missing `X-Plant-Id` does not fail the filter; business uses `PlantContext.require()` /
  `CurrentContext.requirePlantId()` → `PlantNotSetException` → HTTP 400 `PLANT_NOT_SET`.
- Present-but-blank or multi-value `X-Plant-Id` → HTTP 400 `PLANT_HEADER_INVALID`.
- `PlantContext.set` rejects null/blank (`IllegalArgumentException`).
- ThreadLocal **does not cross async**; pass plantId explicitly or use a `TaskDecorator` (not shipped).
- **MyBatis plant line**: placeholder `PlantLineInterceptor` (Javadoc only) for future `plant_id` filtering.
- **Dynamic-form stub** (`config.form`): closed `FieldType` (8 types), DTO records,
  `DynamicFormSchemaValidator` with strict write-config whitelist + RE2J `pattern` (≤200 code points),
  `layout.span` 1–24, unique option values, non-blank labels, `fields.length` ≤ 50,
  `schemaVersion` default 1 / reject others. Unknown type → `SCHEMA_INVALID_TYPE` (not bare IAE → 500).
- Module packages remain stubs; no domain DDL; no JWT / Formily.

## Follow-up vs original skeleton

| Topic | Convention |
| --- | --- |
| Header trust | Only `mes.auth.mode=header` |
| Invalid header | 400 `PLANT_HEADER_INVALID` |
| Business context API | `CurrentContext` → `PlantContext` |
| Schema write | Whitelist; codes `SCHEMA_UNKNOWN_KEY`, `PATTERN_*`, `SCHEMA_TOO_MANY_FIELDS`, … |
| Row filter | `PlantLineInterceptor` placeholder |

## Test (no Postgres)

```bash
source /home/box/.mes-java.env
cd apps/api
./mvnw -B -Dtest=PlantContextTest,PlantContextFilterTest,DynamicFormSchemaValidatorTest,PlantContextMvcTest test
./mvnw -B -DskipTests package
```

Full `verify` / `HealthEndpointTest` still needs Postgres from `deploy/docker-compose.yml`.
