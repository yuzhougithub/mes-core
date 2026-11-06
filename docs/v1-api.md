# V1 接口规范：主数据

状态：草案，待评审。数据含义见 [数据模型](v1-data-model.md)，【假设】编号见 [总览](v1-master-data-overview.md)。实现后以 springdoc 生成的 OpenAPI（`/v3/api-docs`）为机器可读契约，本文为评审基准。

## 基本约定

| 项 | 约定 |
| --- | --- |
| 前缀 | 统一 `/api/v1`。`/actuator`、`/v3`、`/swagger-ui` 不在此前缀下。破坏性变更才升 `v2` |
| 风格 | 资源名小写复数、中划线：`/work-centers`。状态迁移等动作用子路径 `POST /{id}/release` |
| 格式 | JSON，UTF-8，字段 camelCase。时间 ISO-8601 带偏移；数量用 JSON 数字，后端 `BigDecimal` |
| id | UUID 字符串 |
| 认证 | `Authorization: Bearer <access token>`（`jwt` 模式）。骨架期 `header` 模式见下 |
| 当前工厂 | 来自认证上下文（token 的 `plt`）。骨架期用请求头 `X-Plant-Id`【假设 A2】。缺失返回 400 `PLANT_REQUIRED`；工厂不存在或无任职返回 403 `PLANT_FORBIDDEN` |
| 链路 | 响应头 `X-Trace-Id`；错误体同样带 `traceId` |
| CORS | 默认关闭，同源访问（Vite 代理 / nginx）。跨域需配置白名单 `mes.cors.allowed-origins` |

### 范围字段

共享型资源（物料、工序、BOM、路线、不良 / 原因代码、字典项、编码规则）的读写模型带：

| 字段 | 说明 |
| --- | --- |
| `scope` | `SHARED` 或 `PLANT`。创建时给出；`PLANT` 表示归当前工厂。创建后不可改（A16） |
| `plantId` | 只读。`SHARED` 时为 `null` |

厂自有资源不带 `scope`，`plantId` 只读，等于当前工厂。

### 乐观锁

- 读模型带 `lockVersion`。`PUT`、状态迁移的请求体必须带回；`DELETE` 用查询参数 `?lockVersion=`。
- 不一致返回 409 `STALE_VERSION`，客户端重新读取。
- 更新整体替换（`PUT`），不提供 `PATCH`。聚合子集合（BOM 行、路线节点与边、班组成员、任职角色）随根一起整体提交。

### 幂等

不做通用幂等键（A13）：

- 创建：编码唯一，重复提交返回 409 `DUPLICATE_CODE`，前端按冲突提示。编码由规则生成时，前端防重复点击。
- 发布 / 作废：对象已在目标状态时返回 200 与当前对象。
- 导入：`dryRun=true` 无副作用；提交整批事务。

### 分页、排序、过滤

列表都分页：

| 参数 | 说明 |
| --- | --- |
| `page` | 从 1 开始，默认 1 |
| `size` | 默认 20，最大 200 |
| `sort` | `字段,asc` 或 `字段,desc`，可重复，只认各资源白名单字段（默认 `code,asc`） |
| `q` | 编码 / 名称模糊 |
| 资源过滤 | 如 `status`、`categoryId`、`enabled`、`scope`、`ids`（逗号分隔，≤ 200，用于回显引用） |

```json
{ "items": [], "page": 1, "size": 20, "total": 0 }
```

小字典类资源（计量单位、角色、字典项）同样分页，前端下拉取 `size=200`。

### 错误体

RFC 9457 Problem Details（Spring `ProblemDetail`），`Content-Type: application/problem+json`，扩展 `code`、`errors`、`traceId`：

```json
{
  "type": "about:blank",
  "title": "校验失败",
  "status": 422,
  "detail": "2 个字段不合法",
  "instance": "/api/v1/materials/0e0c…/plant-ext",
  "code": "VALIDATION_FAILED",
  "errors": [
    { "field": "values.surface_finish", "code": "EXT_OPTION_INVALID", "message": "不是可选值：chrome" }
  ],
  "traceId": "8f1c2a7d9b0e4c11"
}
```

`code` 稳定，前端按它分支；`title` / `message` 只供显示。

| HTTP | `code` | 场景 |
| --- | --- | --- |
| 400 | `BAD_REQUEST`、`PLANT_REQUIRED`、`PLANT_MISMATCH` | JSON 不合法、缺工厂、请求头与 token 不一致 |
| 401 | `UNAUTHENTICATED`、`TOKEN_EXPIRED` | |
| 403 | `FORBIDDEN`、`PLANT_FORBIDDEN`、`NO_PLANT_ASSIGNMENT` | 角色不够、无该厂任职 |
| 404 | `NOT_FOUND` | 不存在，或属于他厂（不暴露存在性） |
| 409 | `DUPLICATE_CODE`、`STALE_VERSION`、`STATE_CONFLICT`、`IN_USE` | 编码重复、乐观锁、状态不允许（如改已发布）、被引用不能删 |
| 413 | `PAYLOAD_TOO_LARGE` | 文件或导入超限 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 文件类型不在白名单 |
| 422 | `VALIDATION_FAILED`、`CROSS_PLANT_REFERENCE`、`RELEASE_CHECK_FAILED`、`PATTERN_INVALID`、`PATTERN_TOO_LONG`、`SCHEMA_UNKNOWN_KEY`、`SCHEMA_TOO_MANY_FIELDS`、`LAYOUT_INVALID`、`LAYOUT_TOO_LARGE`、`EXPORT_TOO_LARGE` | 字段或业务规则不过；`errors[]` 给明细 |
| 500 | `INTERNAL` | 不回显堆栈 |

`errors[]` 每项形如 `{ "field", "code", "message" }`。`field` 为 JSON Pointer 或点路径（配置用 Pointer，如 `/fields/0/widget`；业务体用点路径，如 `values.surface_finish`）。不另设 `pointer` 字段。

`errors[].code` 常用：`REQUIRED`、`TOO_LONG`、`OUT_OF_RANGE`、`PATTERN_MISMATCH`、`PATTERN_TOO_LONG`、`DICT_VALUE_INVALID`、`EXT_UNKNOWN_KEY`、`EXT_TYPE_MISMATCH`、`EXT_OPTION_INVALID`、`SCHEMA_UNKNOWN_KEY`、`SCHEMA_TOO_MANY_FIELDS`、`LAYOUT_INVALID`、`LAYOUT_TOO_LARGE`、`FILE_NOT_FOUND`、`BOM_CYCLE`、`REF_OBSOLETE`。

### 授权

见总览「认证、角色与当前工厂」。下表「角色」列为最低角色：读 = `VIEWER`；写本厂 = `EDITOR`；写共享 / 配置 = `ADMIN`。共享型资源的写入按记录范围判定：`SHARED` 需 `ADMIN`，`PLANT` 需 `EDITOR`。

## 端点清单

`CRUD` = `GET` 列表、`POST` 新建、`GET /{id}`、`PUT /{id}`、`DELETE /{id}`（逻辑删除，被引用 409 `IN_USE`）。

### auth（V1 交付前实现；骨架期只有 `/me`）

| 方法 路径 | 说明 |
| --- | --- |
| `POST /auth/login` | `{ username, password }` → `{ accessToken, expiresIn, plant, roles }`，refresh token 写 cookie |
| `POST /auth/refresh` | 凭 cookie 换新 access token，refresh token 轮换 |
| `POST /auth/logout` | 作废 refresh token |
| `POST /auth/switch-plant` | `{ plantId }` → 新 access token |
| `GET /auth/me` | 当前人员、当前工厂、可切换工厂列表、当前厂角色。`header` 模式也可用，返回请求头对应的值 |

以下路径都省略 `/api/v1` 前缀。

### org

| 路径 | 操作 | 角色 | 备注 |
| --- | --- | --- | --- |
| `/plants` | CRUD | 读 / `ADMIN` | 全局，不受当前厂过滤 |
| `/workshops` | CRUD | 读 / 写本厂 | 当前厂；体内 `extValues` |
| `/work-centers` | CRUD | 读 / 写本厂 | 过滤 `workshopId`；体内 `extValues` |
| `/persons` | CRUD | 读 / `ADMIN` | 全局身份；列表默认只返回在当前厂有任职的人，`all=true` 返回全部（`ADMIN`） |
| `/persons/{id}/assignment` | `GET` `PUT` `DELETE` | 读 / `ADMIN` | 当前厂任职：`workshopId`、`jobTitle`、`active`、`roleIds`、`extValues`、`lockVersion` |
| `/roles` | `GET` | 读 | V1 只读种子 |
| `/teams` | CRUD | 读 / 写本厂 | 体内 `memberIds` 整体替换 |

### material

| 路径 | 操作 | 角色 | 备注 |
| --- | --- | --- | --- |
| `/material-categories` | CRUD | 读 / `ADMIN` | `?tree=true` 返回整棵树（不分页） |
| `/uoms` | CRUD | 读 / `ADMIN` | |
| `/materials` | CRUD | 按范围 | 过滤 `status`、`materialType`、`categoryId`、`scope`、`enabledInPlant`。`code` 为空时按编码规则生成。只有草稿可 `PUT` / `DELETE` |
| `/materials/{id}/release` | `POST` | 按范围 | `{ lockVersion }` |
| `/materials/{id}/obsolete` | `POST` | 按范围 | `{ lockVersion }` |
| `/materials/{id}/plant-ext` | `GET` `PUT` | 读 / 写本厂 | 当前厂扩展：`{ enabled, values, lockVersion }`。首次 `PUT` 时 `lockVersion` 传 `0` |
| `/boms` | CRUD | 按范围 | 过滤 `materialId`、`status`、`scope`。详情含 `lines[]`；`PUT` 整体替换行。只有草稿可改删 |
| `/boms/{id}/release` / `obsolete` | `POST` | 按范围 | 发布时自动作废同范围旧发布版本，响应带 `obsoletedId` |
| `/boms/{id}/copy` | `POST` | 按范围 | `{ versionNo, scope }` → 新草稿。复制共享版本为 `PLANT` 即得厂专用版本 |
| `/boms/effective` | `GET` | 读 | `?materialId=` → 当前厂生效的发布版本（厂专用优先，A8），无则 404 |

### routing

| 路径 | 操作 | 角色 | 备注 |
| --- | --- | --- | --- |
| `/operations` | CRUD | 按范围 | 无版本，用 `enabled` |
| `/operations/{id}/plant-ext` | `GET` `PUT` | 读 / 写本厂 | `{ enabled, defaultWorkCenterId, stdTimeSec, values, lockVersion }` |
| `/routings` | CRUD | 按范围 | 过滤 `code`、`materialId`、`status`、`scope`。详情含 `steps[]`、`edges[]`、可选 `layout`；`PUT` 整体替换。边用节点 `stepNo` 引用（不用节点 id），服务端换成 id；请求里的主序视觉边不要传（或忽略）。唯一键为 `(code, plant_id, version_no)`，共享与厂专用可同 `code` 并存。画布投影见 [架构备忘](architecture.md#工艺路线画布) |
| `/routings/{id}/release` / `obsolete` / `copy` | `POST` | 按范围 | 同 BOM：`copy` 可指定 `scope=PLANT` 从共享复制为厂专用，并复制 `layout`；发布只作废同 `(code, plant_id)` 的旧 `RELEASED`，发布后布局冻结 |
| `/routings/effective` | `GET` | 读 | `?materialId=` 或 `?code=` → 当前厂生效发布版本（厂专用优先于共享，A8） |

`PUT /routings/{id}` 体在原有头字段与 `steps` / `edges` 之外可带 `layout`（仅 `DRAFT`；非草稿 → 409 `STATE_CONFLICT`）：

```json
{
  "layout": {
    "schemaVersion": 1,
    "nodes": { "10": { "x": 120, "y": 80 }, "20": { "x": 320, "y": 80 } }
  }
}
```

`layout` 白名单与拒绝策略见 [架构备忘 · 工艺路线画布](architecture.md#工艺路线画布)：根仅 `schemaVersion`/`nodes`；节点仅 `x`/`y`；`nodes` 键必须属于本请求 `steps[].stepNo`（含返工）；未知键或孤儿键 → 422 `LAYOUT_INVALID`（**拒绝，不裁剪**）；序列化 > 64 KB → 422 `LAYOUT_TOO_LARGE`。省略 `layout` = 不改；显式 `null` = 清空。不参与发布业务规则。

`POST /routings/{id}/copy` 将源版本的 `layout_json` **一并复制**到新草稿（V1 保持 `stepNo`，坐标键可直拷）。

### resource

| 路径 | 操作 | 角色 | 备注 |
| --- | --- | --- | --- |
| `/equipment` | CRUD | 读 / 写本厂 | 过滤 `workCenterId`、`equipmentType`、`status`；体内 `extValues` |

### config

| 路径 | 操作 | 角色 | 备注 |
| --- | --- | --- | --- |
| `/custom-field-schemas/{entityType}` | `GET` `PUT` | 读 / `ADMIN` | 当前厂的配置；`PUT` 体 `{ schema, lockVersion }`。`entityType` 见数据模型 |
| `/dict-types` | CRUD | 读 / `ADMIN` | 系统字典不可删 |
| `/dict-types/{code}/items` | `GET` `POST` | 读 / 按范围 | 返回共享项 + 当前厂项 |
| `/dict-items/{id}` | `GET` `PUT` `DELETE` | 按范围 | |
| `/defect-codes`、`/reason-codes` | CRUD | 按范围 | |
| `/code-rules` | CRUD | `ADMIN` | |
| `/code-rules/preview` | `GET` | 读 | `?entityType=` → 下一个编码样例，不占号 |
| `/files` | `POST` | 写本厂 | multipart，字段 `file`，参数 `kind`（`image` 或 `file`）→ `{ id, objectKey, fileName, contentType, sizeBytes }` |
| `/files/{id}` | `GET` | 读 | 元数据 |
| `/files/{id}/content` | `GET` | 读 | 流式下载；`?inline=true` 仅图片 |
| `/files/by-key` | `GET` | 读 | `?keys=k1,k2` → 元数据，供表单回显扩展值里的存储键 |

### 导入导出

`{entity}` ∈ `materials`、`material-categories`、`uoms`、`boms`、`operations`、`routings`、`equipment`、`persons`、`workshops`、`work-centers`、`defect-codes`、`reason-codes`、`dict-items`。

| 路径 | 说明 |
| --- | --- |
| `GET /io/{entity}/template` | 下载 `.xlsx` 模板，含当前厂该实体的 `ext.*` 列 |
| `POST /io/{entity}/import?dryRun=true&mode=CREATE` | multipart `file`。`mode`：`CREATE`（编码已存在即报错）或 `UPSERT`（按编码更新；已发布对象只更新厂扩展值） |
| `GET /io/{entity}/export` | 过滤参数同列表；≤ 50000 行，超出 422 `EXPORT_TOO_LARGE` |

角色：模板 / 导出 = 读；导入 = 写本厂，含共享数据的行需 `ADMIN`。

模板格式：

- 工作表 `data`。第 1 行中文表头，必填列带 `*`；第 2 行列键（如 `code`、`uomCode`、`ext.surface_finish`），可隐藏，解析以第 2 行为准；数据从第 3 行起。第二张表 `说明` 列出各列类型、可选值与示例。
- 引用列用编码不用 id：`categoryCode`、`uomCode`、`componentCode` 等。范围列 `scope` 取 `SHARED` / `PLANT`。
- 多行结构扁平化：BOM 每行一个组件，按 `materialCode + versionNo` 分组成头；路线每行一个节点，返工边放第二张表 `edges`（`fromStepNo`、`toStepNo`、`edgeType`、`defectCode`）。
- 扩展值：`enum` 填 `value` 或 `label`；`multi_enum` 用 `;` 分隔；`datetime` 用 Excel 日期单元格或 ISO 字符串；`boolean` 填 `是/否` 或 `true/false`；`image` / `file` 不可导入（导出为文件名，导入时忽略该列）。
- 枚举与字典列在模板里带下拉数据验证。

校验与结果：

- 先全量校验再写；任一行错则整批不写（A11），`dryRun` 只校验。
- 校验与界面保存相同（必填、字典、引用存在、跨厂、扩展值、状态）。
- 响应：

```json
{
  "dryRun": true,
  "totalRows": 1200,
  "okRows": 1197,
  "created": 0,
  "updated": 0,
  "errors": [
    { "row": 15, "column": "uomCode", "code": "NOT_FOUND", "message": "计量单位 KGS 不存在" },
    { "row": 88, "column": "ext.surface_finish", "code": "EXT_OPTION_INVALID", "message": "不是可选值：镀铬" }
  ],
  "errorReportFileId": "6b8d…"
}
```

- `errors` 最多返回 500 条；完整报告是原文件加「错误」列的 `.xlsx`，存为 `cfg_file_object`，经 `/files/{id}/content` 下载，24 小时后可清理。
- 行号 `row` 是 Excel 行号（含两行表头）。

## 示例

以下省略 `Authorization`；骨架期换成 `X-Plant-Id`。当前工厂为一厂 `2f7a…01`。

### 1. 配置物料自定义字段

```http
PUT /api/v1/custom-field-schemas/material
Content-Type: application/json
X-Plant-Id: 2f7a0c1e-0000-4000-8000-000000000001

{
  "lockVersion": 0,
  "schema": {
    "schemaVersion": 1,
    "fields": [
      {
        "key": "surface_finish",
        "type": "enum",
        "title": "表面处理",
        "required": true,
        "options": [
          { "value": "anodize", "label": "阳极氧化" },
          { "value": "passivate", "label": "钝化" }
        ],
        "layout": { "order": 10, "group": "质量", "span": 12 }
      },
      {
        "key": "customer_pn",
        "type": "string",
        "title": "客户料号",
        "validation": { "maxLength": 40, "pattern": "^[A-Z0-9-]+$" },
        "layout": { "order": 20, "group": "客户", "span": 12 }
      },
      {
        "key": "rohs",
        "type": "boolean",
        "title": "RoHS",
        "layout": { "order": 30, "group": "质量", "span": 6 }
      },
      {
        "key": "drawing",
        "type": "file",
        "title": "客户图纸",
        "layout": { "order": 40, "group": "客户", "span": 12 }
      }
    ]
  }
}
```

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "entityType": "material",
  "plantId": "2f7a0c1e-0000-4000-8000-000000000001",
  "schema": { "schemaVersion": 1, "fields": [ "…同请求…" ] },
  "lockVersion": 1,
  "updatedAt": "2026-11-06T17:30:00+08:00"
}
```

### 2. 读共享物料并写本厂扩展值

```http
GET /api/v1/materials/0e0c5d3a-7b41-4c7e-9a52-1d2f3e4a5b6c
X-Plant-Id: 2f7a0c1e-0000-4000-8000-000000000001
```

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": "0e0c5d3a-7b41-4c7e-9a52-1d2f3e4a5b6c",
  "scope": "SHARED",
  "plantId": null,
  "code": "MAT-20261106-0001",
  "name": "铝合金外壳",
  "spec": "6061-T6 120×80×15",
  "materialType": "SEMI",
  "categoryId": "a1b2…",
  "uomId": "c3d4…",
  "drawingNo": "DW-1001",
  "status": "RELEASED",
  "releasedAt": "2026-11-06T15:02:11+08:00",
  "lockVersion": 3,
  "plantExt": {
    "enabled": true,
    "values": { "surface_finish": "anodize", "rohs": true },
    "lockVersion": 1
  },
  "createdAt": "2026-11-06T14:10:00+08:00",
  "updatedAt": "2026-11-06T15:02:11+08:00"
}
```

`plantExt` 是当前厂的扩展；本厂没有扩展行时为 `{ "enabled": true, "values": {}, "lockVersion": 0 }`。字段定义另取 `GET /custom-field-schemas/material`，前端缓存后交给 Schema 渲染器。

写扩展值（物料已发布，主表只读，扩展值仍可写）：

```http
PUT /api/v1/materials/0e0c5d3a-7b41-4c7e-9a52-1d2f3e4a5b6c/plant-ext
Content-Type: application/json
X-Plant-Id: 2f7a0c1e-0000-4000-8000-000000000001

{
  "enabled": true,
  "lockVersion": 1,
  "values": {
    "surface_finish": "anodize",
    "customer_pn": "HX-88021-A",
    "rohs": true,
    "drawing": "plants/2f7a0c1e-0000-4000-8000-000000000001/2026/11/9d1e4b6a-2c3f-4a5b-8c7d-6e5f4a3b2c1d"
  }
}
```

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "materialId": "0e0c5d3a-7b41-4c7e-9a52-1d2f3e4a5b6c",
  "plantId": "2f7a0c1e-0000-4000-8000-000000000001",
  "enabled": true,
  "values": {
    "surface_finish": "anodize",
    "customer_pn": "HX-88021-A",
    "rohs": true,
    "drawing": "plants/2f7a0c1e-0000-4000-8000-000000000001/2026/11/9d1e4b6a-2c3f-4a5b-8c7d-6e5f4a3b2c1d"
  },
  "lockVersion": 2
}
```

不合法时：

```http
HTTP/1.1 422 Unprocessable Content
Content-Type: application/problem+json

{
  "title": "校验失败",
  "status": 422,
  "code": "VALIDATION_FAILED",
  "errors": [
    { "field": "values.surface_finish", "code": "REQUIRED", "message": "表面处理必填" },
    { "field": "values.customer_pn", "code": "PATTERN_MISMATCH", "message": "客户料号格式不符" },
    { "field": "values.colour", "code": "EXT_UNKNOWN_KEY", "message": "未配置的字段：colour" }
  ],
  "traceId": "8f1c2a7d9b0e4c11"
}
```

### 3. 建厂专用 BOM 草稿并发布

```http
POST /api/v1/boms
Content-Type: application/json
X-Plant-Id: 2f7a0c1e-0000-4000-8000-000000000001

{
  "scope": "PLANT",
  "materialId": "f9e8d7c6-0000-4000-8000-0000000000aa",
  "versionNo": "B",
  "baseQty": 1,
  "remark": "一厂改用国产螺钉",
  "lines": [
    { "lineNo": 10, "componentMaterialId": "0e0c5d3a-7b41-4c7e-9a52-1d2f3e4a5b6c", "qty": 1, "uomId": "c3d4…", "scrapRate": 0.01 },
    { "lineNo": 20, "componentMaterialId": "77aa…", "qty": 4, "uomId": "c3d4…", "scrapRate": 0, "operationId": "5c5c…" }
  ]
}
```

```http
HTTP/1.1 201 Created
Location: /api/v1/boms/4d3c2b1a-0000-4000-8000-0000000000b2
Content-Type: application/json

{ "id": "4d3c2b1a-0000-4000-8000-0000000000b2", "scope": "PLANT", "plantId": "2f7a0c1e-0000-4000-8000-000000000001", "status": "DRAFT", "versionNo": "B", "lockVersion": 0, "lines": [ "…" ] }
```

```http
POST /api/v1/boms/4d3c2b1a-0000-4000-8000-0000000000b2/release
Content-Type: application/json
X-Plant-Id: 2f7a0c1e-0000-4000-8000-000000000001

{ "lockVersion": 0 }
```

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": "4d3c2b1a-0000-4000-8000-0000000000b2",
  "status": "RELEASED",
  "releasedAt": "2026-11-06T17:42:05+08:00",
  "lockVersion": 1,
  "obsoletedId": "1a2b3c4d-0000-4000-8000-0000000000b1"
}
```

`obsoletedId` 是同一父件、一厂范围内原来的发布版本（无则 `null`）；共享版本不受影响。之后一厂 `GET /boms/effective?materialId=f9e8…` 返回本版本，其他厂仍返回共享发布版本。

发布校验不过时返回 422 `RELEASE_CHECK_FAILED`，`errors[]` 如 `{ "field": "lines[1].componentMaterialId", "code": "CROSS_PLANT_REFERENCE" }`、`{ "field": "lines", "code": "BOM_CYCLE" }`。
