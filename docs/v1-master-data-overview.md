# V1 竖切总览：主数据管理（PC）

状态：草案，待评审。范围以 [章程](charter-v1-master-data.md) 为准，栈与边界以 [架构备忘](architecture.md) 为准。本文只细化到后端能开干。

## 阅读顺序

1. 本文：边界、共享与厂级扩展、统一约定、认证与工厂上下文、跨厂引用、安全、性能、开发环境、假设
2. [架构备忘 · 工艺路线画布](architecture.md#工艺路线画布)：编辑器视图与领域模型投影（`@xyflow/react`）
3. [数据模型](v1-data-model.md)：表、约束、状态、ER
4. [接口规范](v1-api.md)：REST 约定、端点清单、示例
5. [Flyway 草案](sql/V2__master_data.sql)：可直接执行的 DDL，评审后移入 migration

## 目标

PC 端能维护章程列出的主数据，多厂隔离，主数据可共享并按厂扩展，按厂配置自定义字段，支持 Excel 导入导出。物料、BOM、工艺路线有草稿 / 发布 / 作废。

不做：章程「范围外」各项；鉴权本身（`auth` 只留上下文接口）；空业务模块。

## 模块边界

一个进程，按包分。包内分 `api`（Controller + DTO）、`app`（服务）、`domain`（实体、规则）、`infra`（Mapper）。跨包只调对方 `app` 层公开的服务接口或只读查询接口，不直接用对方 Mapper。

| 包 | 表前缀 | 拥有 | 依赖 |
| --- | --- | --- | --- |
| `org` | `org_` | 工厂、车间、工作中心、人员、工厂任职、简单角色、班组 | `config`（自定义字段校验、编码） |
| `material` | `mat_` | 物料分类、计量单位、物料 + 厂扩展、BOM + 行 | `config`、`org`（工厂存在性）、`routing`（BOM 行消耗工序，只读校验） |
| `routing` | `rtg_` | 工序 + 厂扩展、工艺路线版本、节点、返工边 | `material`（适用产品）、`org`（工作中心）、`config`（不良代码） |
| `resource` | `res_` | 设备 | `org`、`config` |
| `config` | `cfg_` | 自定义字段配置与值校验、数据字典、不良/原因代码、编码规则、文件元数据、导入导出框架 | 无业务包依赖 |
| `auth` | — | 登录、token、当前工厂与用户上下文（`CurrentContext`）。本竖切不落表，账号表在 `V3__auth.sql`（A15） | `org`（任职与角色，只读） |

`material` 与 `routing` 互相只读引用（BOM 行 → 工序；路线 → 物料），用接口打断循环：各自暴露 `XxxQuery` 接口，实现留在本包。

Excel 导入导出：`config` 提供通用框架（模板生成、解析、行错误报告、`ext.<key>` 列），各包注册自己的列映射与写入器。

## 共享与厂级扩展

三类表：

| 类型 | `plant_id` | 自定义字段值放哪 | 例子 |
| --- | --- | --- | --- |
| 全局 | 无此列 | 不支持 | 工厂、人员、角色、物料分类、计量单位、字典类型 |
| 共享型 | 可空：空 = 全厂共享；非空 = 该厂私有 | `*_plant_ext.ext_values`（每厂每条至多一行） | 物料、工序；BOM、路线、不良/原因代码、字典项、编码规则（无 ext） |
| 厂自有 | 非空 | 本行 `ext_values` | 车间、工作中心、班组、设备；工厂任职（即人员的厂扩展） |

矩阵（✓ = 支持自定义字段）：

| 实体 | 归属 | 厂级差异怎么放 | 自定义字段 `entity_type` |
| --- | --- | --- | --- |
| 工厂 | 全局 | — | — |
| 车间 / 工作中心 | 厂自有 | 本行 | ✓ `workshop` / `work_center` |
| 人员 | 全局 | `org_person_plant`（任职、车间、岗位、角色） | ✓ `person`（值在任职行） |
| 班组 | 厂自有 | 本行 | — |
| 角色 | 全局 | 按任职授予 | — |
| 物料分类 / 计量单位 | 全局 | — | — |
| 物料 | 共享型 | `mat_material_plant_ext`：本厂启用、扩展值 | ✓ `material` |
| BOM | 共享型 | 结构有差异时建该厂专用版本（`plant_id` 非空） | —【假设 A6】 |
| 工序 | 共享型 | `rtg_operation_plant_ext`：本厂启用、默认工作中心、标准工时覆盖、扩展值 | ✓ `operation` |
| 工艺路线 | 共享型 | 同 BOM | —【假设 A6】 |
| 设备 | 厂自有 | 本行 | ✓ `equipment` |
| 不良 / 原因代码 | 共享型 | 厂私有代码 | — |
| 数据字典 | 类型全局，项共享型 | 厂追加项 | — |
| 编码规则 | 共享型 | 厂规则优先于共享规则 | — |

可见性：在工厂 P 中查询共享型数据，返回 `plant_id IS NULL OR plant_id = P`。厂自有数据只返回 `plant_id = P`。共享型数据的厂扩展只读写 P 那一行。

## 统一约定

| 项 | 约定 |
| --- | --- |
| 主键 | `uuid`，库默认 `gen_random_uuid()`；Java 侧 `UUID`，MyBatis-Plus `IdType.INPUT` 或交给库默认，二选一后统一【假设 A1】 |
| 业务编码 | `code`。共享型与全局表全局唯一；厂自有表厂内唯一。创建后不可改 |
| 审计 | `created_at / created_by / updated_at / updated_by`，MyBatis-Plus `MetaObjectHandler` 填充。`*_by` 取 `CurrentContext` 的用户 id，auth 未落地前为空 |
| 乐观锁 | `lock_version integer`，MyBatis-Plus `@Version`。与 BOM / 路线的业务版本 `version_no` 区分。API 字段名 `lockVersion` |
| 逻辑删除 | 仅聚合根有 `deleted boolean`，`@TableLogic`。唯一索引带 `WHERE NOT deleted`，删后可复用编码。子表（BOM 行、路线节点 / 边、成员、角色授予）随根整体替换，物理删。扩展表物理删 |
| 停用 vs 删除 | 被引用的数据只能停用（`enabled = false`），不能删。发布过的版本只能作废 |
| 时间 | `timestamptz`，API 用 ISO-8601 带偏移 |
| 枚举 | `varchar` + `CHECK`，大写蛇形。字典值存 `varchar`，不建外键，写入时校验 |
| 工厂上下文 | 业务代码只读 `auth.CurrentContext`（`plantId`、`userId`、`roles`），不直接读请求头或 token。来源见下节 |
| 结构变更 | 只走 Flyway。本竖切为一个 `V2__master_data.sql` |

## 版本状态

物料、BOM、工艺路线共用：

```
DRAFT ──发布──▶ RELEASED ──作废──▶ OBSOLETE
  │
  └─删除（仅草稿）
```

- 草稿可改、可删；发布后头和子表只读，只有厂扩展值仍可改；作废后只读。
- BOM / 路线：同一父件（或路线编码）同一范围至多一个 `RELEASED`。发布新版本时，同范围旧发布版本在同一事务内自动作废【假设 A4】。
- 新版本从已有版本「复制为草稿」得到。
- 物料只有生命周期状态，不分多个版本【假设 A3】。

## 认证、角色与当前工厂

分两步，业务代码不感知差别。

| 阶段 | `mes.auth.mode` | 当前工厂 | 当前用户 | 说明 |
| --- | --- | --- | --- | --- |
| 骨架期（本竖切开工时） | `header` | 请求头 `X-Plant-Id` | 请求头 `X-User-Id`，可空 | 只在 `dev` profile 可开；生产启动时若为 `header` 直接失败【假设 A2】 |
| V1 交付 | `jwt` | token 的 `plt` 声明 | token 的 `sub` | 以 token 为准；请求若另带 `X-Plant-Id` 且与 token 不一致，返回 400 `PLANT_MISMATCH` |

V1 登录（`auth` 包，单独迁移 `V3__auth.sql`，不在本竖切 DDL 内）【假设 A15】：

- 账号表 `auth_account`（用户名、BCrypt 密码哈希、`person_id`、启用、失败次数、锁定至）。账号与 `org_person` 一对一。
- `POST /api/v1/auth/login` 用户名 + 密码，返回 access token 与 refresh token。连续失败 5 次锁 15 分钟。
- Access token：JWT，HS256，密钥来自部署配置（私有化单进程，不需要非对称），有效期 30 分钟。声明：`sub`（账号 id）、`pid`（人员 id）、`plt`（当前工厂 id）、`roles`（当前工厂的角色编码数组）、`exp`、`iat`、`jti`。
- Refresh token：随机串，库内只存哈希，有效期 7 天，用一次换一次；登出即作废。
- 前端存储：access token 放内存，refresh token 放 `HttpOnly; Secure; SameSite=Strict` cookie，路径 `/api/v1/auth`。
- 切换工厂：`POST /api/v1/auth/switch-plant { plantId }`。服务端校验该人员在目标厂有有效任职（`org_person_plant.active`），签发新 access token（`plt` 与 `roles` 换成目标厂）。前端切厂后清掉所有缓存的列表与 schema。
- 登录时 `plt` 取上次使用的工厂；没有则取第一个有效任职；一个任职都没有则拒绝登录 403 `NO_PLANT_ASSIGNMENT`。

角色（`org_role`，按工厂任职授予，V2 DDL 种子三个）【假设 A12】：

| 角色 | 能做 |
| --- | --- |
| `VIEWER` | 读本厂可见数据（共享 + 本厂），导出 |
| `EDITOR` | 另加：维护本厂私有数据与厂扩展值（含厂专用 BOM / 路线的发布与作废），导入 |
| `ADMIN` | 另加：维护共享主数据（`plant_id` 为空的记录）及其发布与作废、工厂、角色授予、自定义字段配置、字典、编码规则 |

权限点只到这三档，不做菜单 / 按钮级授权。`header` 模式下角色视为 `ADMIN`，但仍校验 `X-Plant-Id` 对应工厂存在；不存在 → 403 `PLANT_FORBIDDEN`。

## 跨厂引用

默认禁止，由应用层在写入时校验（数据库外键不表达范围），违规返回 422 `CROSS_PLANT_REFERENCE`。

规则：一条记录的「范围」是它的 `plant_id`（共享型为空时是「共享」，扩展记录是它的 `plant_id`）。

- 共享记录只能引用全局或共享记录。例：共享 BOM 的组件必须是共享物料；共享路线的节点不能填工作中心。
- 厂 P 的记录（含 P 的扩展记录）只能引用全局、共享或 P 自己的记录。例：P 专用 BOM 可用共享物料和 P 私有物料，不能用 Q 私有物料；P 的设备只能挂 P 的工作中心。
- `plant_id` 创建后不可改：私有转共享、共享转私有都不支持，需要时新建【假设 A16】。
- 班组成员、任职车间、团队负责人必须在同一厂有有效任职。

读侧同理：查询只返回全局、共享与当前厂数据；按 id 读他厂私有记录返回 404（不是 403，不暴露存在性）。

## 安全

### 文件上传（`image` / `file` → MinIO）

- 只经 API 上传：`POST /api/v1/files`，需登录，按当前厂落 `cfg_file_object`。不发前端直传凭证。
- 大小：`image` ≤ 5 MB，`file` ≤ 20 MB；Spring multipart 总上限 25 MB【假设 A14】。
- 类型白名单，按文件头魔数判定，不信扩展名与 `Content-Type`：
  - `image`：PNG、JPEG、WebP。不收 SVG、GIF。
  - `file`：PDF、PNG、JPEG、WebP、XLSX、DOCX、TXT、CSV。不收可执行文件、脚本、压缩包、HTML。
- 对象键由服务端生成：`plants/{plantId|shared}/{yyyy}/{MM}/{uuid}`，不含原文件名。桶私有。
- 下载：`GET /api/v1/files/{id}/content`，校验文件所属厂对当前厂可见；响应 `Content-Disposition: attachment`（图片预览用 `inline`）与 `X-Content-Type-Options: nosniff`。
- 自定义字段写入 `image` / `file` 值时，校验该键存在于 `cfg_file_object`、对当前厂可见、类型符合字段类型。
- 无引用文件的清理留到后续（定时任务），V1 不删。

### 自定义字段 `validation.pattern`（防 ReDoS）【已拍板】

- 后端**只用** RE2J（`com.google.re2j`）编译与匹配，禁止 `java.util.regex`【假设 A17】。
- `pattern` 长度上限 **200 字符**（按 Unicode 码点计）；超长 → 422 `PATTERN_TOO_LONG`。
- 保存配置时用 RE2J 试编译：失败（环视、反向引用、RE2 不支持的语法）→ 422 `PATTERN_INVALID`；不设编译超时（RE2 编译本身线性）。
- 字符串值长度上限 2000（即使配置未写 `maxLength`），**先查长度再匹配**。
- 前端可用 JS `RegExp` 做即时提示，**以后端结果为准**。

### 自定义字段 Schema 未知键【已拍板】

写入 `cfg_custom_field_schema.schema` 时做**严格白名单**，不静默忽略：

- 根对象只允许：`schemaVersion`、`fields`。
- 每个 field 对象只允许：`key`、`type`、`title`、`required`、`options`、`validation`、`layout`。
- `options[]` 只允许：`value`、`label`；`layout` 只允许：`order`、`group`、`span`；`validation` 只允许架构备忘列出的约束词。
- 出现 `widget`、`x-component`、`x-decorator`、`x-designable-id`、`x-reactions` 或任何未列键 → 422 `SCHEMA_UNKNOWN_KEY`（`errors[].field` 用 JSON Pointer，如 `/fields/0/widget`）。
- **读**配置时：渲染器仍可忽略不认识的键（兼容历史脏数据）；**写**一律拒绝。值对象 `ext_values` 的未知字段 key 见 A19，同样拒绝。

### `boolean` + `required` 与 Switch 语义【已拍板】

- `required: true` 表示请求的 `ext_values`（或表单提交）里**必须出现该 key**，且值类型为布尔；**不要求值为 `true`**。
- Ant Design `Switch` 未打开 = 值 **`false`**（显式布尔），算已填，可通过必填校验。
- 缺 key、或值为 `null` / 非布尔 → 422，`errors[].code` 为 `REQUIRED` / `EXT_TYPE_MISMATCH`（与接口规范一致）。
- 前端 Schema 渲染器：`required` 的 boolean 字段初始值用 `false`（受控），不要用 `undefined`。

### 字段数量上限【已拍板】

- 每厂每 `entity_type` 最多 **50** 个字段（硬上限，性能与表单可用性）【假设 A19】。
- 保存配置时 `fields.length > 50` → 422 `SCHEMA_TOO_MANY_FIELDS`。
- 单条 `ext_values` JSON ≤ 64 KB；列表/详情仍按性能表目标。

### Excel 导入导出

- 只收 `.xlsx`，≤ 10 MB，≤ 5000 数据行【假设 A11】。用流式读写（Apache POI SXSSF / 事件模型，或 FastExcel，后端选定）。
- 解压炸弹防护：限制解压后大小（POI `ZipSecureFile` 默认阈值）。
- 导出时以 `=`、`+`、`-`、`@`、制表符开头的单元格加前导 `'`，防公式注入。
- 模板、校验、失败报告的格式见 [接口规范](v1-api.md)「导入导出」。

### CSRF

- Access token 放 `Authorization: Bearer`，浏览器不自动带，**不需要 CSRF**。
- Refresh token 仅 `HttpOnly` cookie，路径限 `/api/v1/auth`；对该路径的写请求（`refresh` / `logout`）校验 `Origin`/`Referer` 同源，或要求自定义头 `X-Requested-With: XMLHttpRequest`。登录响应不设可被脚本读的 CSRF cookie。

### 其他

- CORS 默认关闭：前端经同源反向代理访问（开发用 Vite 代理，部署用 nginx）。需跨域时配置 `mes.cors.allowed-origins` 白名单，不允许 `*`。
- 所有写接口记审计列；登录、切厂、发布、作废、导入记应用日志（含 traceId）。专门的审计日志表留到后续。

## 性能目标（粗指标）

基准【假设 A18】：单机 4C / 8G，PostgreSQL 同机或同机房；单厂；物料 20 万、BOM 2 万（每个 ≤ 500 行）、路线 1 万（每条 ≤ 100 节点）、设备 5000、人员 5000；并发在线 50 人。

| 操作 | 目标（p95，服务端） |
| --- | --- |
| 列表页（20 条，按编码 / 名称模糊 + 状态 / 分类过滤） | ≤ 500 ms |
| 详情（含厂扩展值；BOM ≤ 500 行、路线 ≤ 100 节点） | ≤ 300 ms |
| 普通保存 | ≤ 500 ms |
| BOM / 路线发布（含校验） | ≤ 2 s |
| 自定义字段配置读取 | ≤ 100 ms（前端按厂 + 实体缓存，切厂清空） |
| 导入 5000 行：校验 / 提交 | ≤ 15 s / ≤ 30 s |
| 导出 ≤ 50000 行 | ≤ 60 s，超出返回 422 `EXPORT_TOO_LARGE`，提示加过滤 |

名称模糊搜索先用 `ILIKE`；压测不达标再加 `pg_trgm` 索引。

## 开发环境

V1 开发时：

- `api`、`postgres`、`minio` 用 Compose：`docker compose -f deploy/docker-compose.yml up --build`。也可只起 `postgres` 与 `minio`，本机 `./mvnw spring-boot:run` 跑 API。
- `web` 本机 `npm run dev`，Vite 把 `/api` 代理到 `localhost:8080`。Compose 暂不包含 web 服务。
- 骨架期 `mes.auth.mode=header`，前端在请求里带 `X-Plant-Id`（开发用工厂下拉选）。
- MinIO 桶名 `mes`，API 启动时不存在则创建【假设 A14】。

## 【假设】清单

| 编号 | 假设 | 影响 |
| --- | --- | --- |
| A1 | 主键用 UUID（库默认生成），不用雪花 / 自增 | 表结构、MyBatis-Plus 配置 |
| A2 | 骨架期当前工厂取请求头 `X-Plant-Id`、用户取 `X-User-Id`（仅 dev）；V1 交付前切到 JWT，只换 `CurrentContext` 实现，接口不变 | 所有接口 |
| A3 | 物料不做多版本，状态即生命周期；图号版次放 `drawing_no` 或自定义字段 | 物料表 |
| A4 | 发布新 BOM / 路线版本自动作废同范围旧发布版本；V1 无生效日期 | 发布逻辑 |
| A5 | 共享型表与全局表的 `code` 全局唯一；厂自有表的 `code` 厂内唯一（与「统一约定」一致）。共享型的厂私有行（`plant_id` 非空）仍占全局编码空间 | 唯一索引 |
| A6 | V1 自定义字段只开放给 物料、工序、人员（任职）、车间、工作中心、设备。BOM / 路线需要时按同模式加 `*_plant_ext` | `entity_type` 枚举 |
| A7 | 自定义字段配置每厂每实体一份，没有集团级默认配置；字段 `key`、`type` 定后不可改，删字段只从配置移除，已存值保留不显示 | 配置接口、校验 |
| A8 | BOM / 路线的厂级差异用「厂专用版本」表达（`plant_id` 非空），取用时厂专用发布版本优先于共享发布版本 | 取版本规则 |
| A9 | 工艺路线主路线 = 非返工节点按 `step_no` 升序；返工只用边表达（REWORK 出、RETURN 回）；节点上的工作中心只在厂专用路线上可填 | 路线模型 |
| A10 | V1 无计量单位换算；BOM 行单位与组件主单位不同时只记录不换算 | 物料、BOM |
| A11 | Excel 导入同步执行、单文件上限 5000 行，先校验后提交（`dryRun`），整批事务，任一行错整批不入 | 导入接口 |
| A12 | 角色只有 `VIEWER` / `EDITOR` / `ADMIN` 三档，按工厂任职授予；共享主数据与配置仅 `ADMIN` 可改 | 授权 |
| A13 | 通用幂等表不做：创建靠编码唯一冲突返回 409；发布 / 作废对已在目标状态的对象重复调用返回 200 | 接口 |
| A14 | 文件只经 API 上传到 MinIO 私有桶 `mes`，`cfg_file_object` 记元数据，自定义字段存 `object_key`；图片 ≤ 5 MB、文件 ≤ 20 MB、魔数白名单 | 文件、自定义字段 |
| A15 | V1 登录用本地账号 + BCrypt + HS256 JWT（30 分钟）+ 轮换 refresh token（7 天）；不接 LDAP / SSO。账号表放 `V3__auth.sql` | auth |
| A16 | 跨厂引用一律禁止；`plant_id` 创建后不可改 | 写校验 |
| A17 | 正则校验用 RE2J，`pattern` ≤ 200 字符，字符串值 ≤ 2000 字符 | 依赖、配置校验 |
| A18 | 性能基准按单机 4C/8G、单厂 20 万物料、50 并发估算 | 索引、压测 |
| A19 | 自定义字段值：写入拒绝未知 `key`；每厂每实体 ≤ 50 字段；配置变更不迁移已存值，下次保存按新配置整体校验 | 扩展值读写 |
| A20 | 保存字段配置时 Schema 顶层/字段级未知键一律拒绝（白名单）；读侧渲染器可忽略未知键 | 配置接口、PR#3 渲染器 |
| A21 | `boolean`+`required`：必须出现 key；`false`（Switch 未选）为合法值，不算空 | 校验、前端初始值 |
| A22 | 自定义字段 `options` 自包含，不引用数据字典 | 配置、字典边界 |
| A23 | 工艺路线画布用 `@xyflow/react`（可编辑草稿，非只读）；真源仍是 step+edge；`layout_json` 白名单校验（未知键/孤儿键拒绝），仅草稿可写，不进发布业务校验 | 前端、路线模型 |

评审时逐条确认；被推翻的假设在对应文档里改，并删去本表条目。
