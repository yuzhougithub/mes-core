# API skeleton: plant context + dynamic-form metadata

Branch `feat/api-plant-context-skeleton` (based on `main`).

## What landed

- **Plant context**: `PlantContext` (ThreadLocal `String` plantId) + `PlantContextFilter` reading header **`X-Plant-Id`**. Missing header does not fail the filter; business code uses `PlantContext.require()` → `PlantNotSetException` → HTTP 400 `{code,message}` via `ApiExceptionHandler`.
- **Dynamic-form stub** (`config.form`): closed `FieldType` (8 types), DTO records, `DynamicFormSchemaValidator` (unique keys, enum/multi_enum options, reject Formily/Designable keys). No persistence / controller.
- Module packages remain stubs; no domain DDL; no JWT / Formily.

## Waiting on

Architect master-data + custom-field DDL/API before the next vertical slice.

## Test (no Postgres)

```bash
source /home/box/.mes-java.env
cd apps/api
./mvnw -B -Dtest=PlantContextTest,PlantContextFilterTest,DynamicFormSchemaValidatorTest test
./mvnw -B -DskipTests package
```

Full `verify` / `HealthEndpointTest` still needs Postgres from `deploy/docker-compose.yml`.
