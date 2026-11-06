# 架构备忘

一个 Spring Boot 进程，按领域分包，不拆微服务。

| 包 | 边界 |
| --- | --- |
| `org` | 工厂、车间、工作中心；人员、班组、工厂任职、简单角色 |
| `material` | 物料分类、计量单位、物料、BOM |
| `routing` | 工序、工艺路线（版本、返工分支） |
| `resource` | 设备 |
| `config` | 自定义字段、数据字典、不良/原因代码、编码规则 |
| `auth` | 登录与按工厂授权（JWT，尚未实现） |

### V1 竖切文档索引

第一竖切「主数据管理 PC」的规格（草案，待评审）：

- [竖切总览](v1-master-data-overview.md)：边界、共享与厂级扩展、统一约定、认证、安全、性能、假设
- [数据模型](v1-data-model.md)
- [接口规范](v1-api.md)
- [Flyway 草案](sql/V2__master_data.sql)：评审后由后端移入 migration
- 本文「工艺路线画布」：编辑器视图选型与投影规则

## 工厂隔离

部署形态是私有化多工厂，不是 SaaS 多租户。

- 工厂自有数据带 `plant_id`，访问时限制在当前工厂。
- 主数据默认可跨工厂共享；工厂差异用扩展记录挂在共享主数据上，不整份复制。
- 当前工厂来自认证上下文。骨架阶段不落表、不发 token。

结构变更只走 Flyway。数据访问用 MyBatis-Plus。文件进 MinIO。

## 动态表单

确认于 2026-11-06。自定义字段的录入用动态表单：字段定义是后端元数据，前端按定义渲染。

### V1 栈

- Web 仍是 React + Ant Design。表单用 Ant Design Form。
- 自研一层薄的 Schema 渲染器。它只做一件事：读字段定义，按 `type` 映射到 Ant Design 控件。
- 这张映射表是唯一绑定组件库的地方。领域元数据里不写组件名。
- V1 依赖不加入 Formily、Designable、FormRender。

| `type` | 章程中的类型 | V1 控件 |
| --- | --- | --- |
| `string` | 文本 | Input |
| `number` | 数字 | InputNumber |
| `datetime` | 日期时间 | DatePicker（含时间） |
| `enum` | 单选 | Select |
| `multi_enum` | 多选 | Select（多选） |
| `boolean` | 布尔 | Switch |
| `image` | 图片 | Upload（图片） |
| `file` | 文件 | Upload |

### 范围内

- 渲染上表八种类型。定义来自 `config` 的自定义字段，按工厂配置。
- 必填、选项、校验、布局提示。
- 值写在该厂扩展记录上，键为字段 `key`。

### 明确不做

- V1 没有可视化表单设计器，不引入 Designable。
- 不采用 Formily。
- V1 不接入 FormRender，也不把已存定义改写成任何渲染库的专有协议。

### 迁往 FormRender 的预留

以后若改用阿里 FormRender（npm 包 `form-render`），只替换渲染器与映射表。已入库的字段定义不改写。

一份配置是字段列表，不是 FormRender 或 Formily 的 schema 文档：

```json
{
  "schemaVersion": 1,
  "fields": [
    {
      "key": "surface_finish",
      "type": "enum",
      "title": "表面处理",
      "required": true,
      "options": [{ "value": "anodize", "label": "阳极氧化" }],
      "layout": { "order": 10, "group": "质量", "span": 12 }
    }
  ]
}
```

`schemaVersion` 缺省当作 `1`。

| 键 | 要求 |
| --- | --- |
| `key` | 稳定字符串，同一份配置内唯一。扩展记录用它存值；将来生成 JSON Schema 时用它做属性名 |
| `type` | 上表枚举，闭合。不要另造组件名 |
| `title` | 显示名 |
| `required` | 布尔，缺省为否 |
| `options` | 仅 `enum` / `multi_enum`，且至少一项。元素为 `{ value, label }`，`value` 是字符串。`label` 只用于显示 |
| `validation` | 可选。只用下面列出的 JSON Schema 约束词 |
| `layout` | 可选。缺省时渲染器仍排成单列表单 |

`validation` 与 JSON Schema 同名，映射时可抄进生成的 schema：

- 文本：`minLength`、`maxLength`、`pattern`（正则表达式字符串，不带两侧斜杠）
- 数字：`min`、`max`
- 多选：`minItems`、`maxItems`
- 日期时间的格式由 `type` 决定，不要再写 `format`

值的形状与 `type` 绑定：

| `type` | 存值 |
| --- | --- |
| `string`、`enum` | 字符串。`enum` 必须等于某个 `options[].value` |
| `number` | 数字 |
| `boolean` | 布尔 |
| `datetime` | ISO-8601 `date-time` 字符串 |
| `multi_enum` | 字符串数组，元素必须来自 `options[].value` |
| `image`、`file` | 对象存储键（字符串）。V1 每个字段一个文件 |

`image` / `file` 不是 JSON Schema 的标准 `type`。适配 FormRender 时投影成字符串（存储键）加上传控件；领域侧仍保留这两个 `type`。

`layout` 只影响排布，不承载业务含义，渲染器可以忽略。同一 `group` 的字段排在一起，组间顺序取该组最小的 `order`；没有 `order` 时按 `fields` 的先后。

| 键 | 含义 |
| --- | --- |
| `order` | 数字，小的在前 |
| `group` | 分组标题 |
| `span` | 1–24 的列宽提示，不是某个组件库的参数 |

不要写入 `widget`、`x-component`、`x-decorator`、`x-designable-id`、Formily 的 `x-reactions`，或其他渲染库专有字段。读侧渲染器可忽略不认识的键；写入配置时按白名单校验，未知键拒绝，不入库（见总览 A20）。

迁到 FormRender 时，映射层把同一份 `fields` 收成 JSON Schema：`properties` 以 `key` 为名，`type` 按下表投影，字段级 `required: true` 收成对象级 `required` 数组，`options` 交给对应控件，`layout` 只进布局适配。

| `type` | JSON Schema 投影 |
| --- | --- |
| `string` | `{ "type": "string" }` |
| `number` | `{ "type": "number" }` |
| `datetime` | `{ "type": "string", "format": "date-time" }` |
| `enum` | `{ "type": "string", "enum": ["<options[].value>"] }` |
| `multi_enum` | `{ "type": "array", "items": { "type": "string", "enum": ["<options[].value>"] } }` |
| `boolean` | `{ "type": "boolean" }` |
| `image`、`file` | `{ "type": "string" }`。控件由映射层另接，不把 `image` / `file` 写进 JSON Schema 的 `type` |

## 工艺路线画布

确认于 2026-11-06（产品拍板）。工艺路线**草稿编辑**用可交互流程画布（非只读预览）；持久化与发布校验仍走领域模型，不另起一套图库模型。

### 选型

| 项 | 约定 |
| --- | --- |
| 库 | `@xyflow/react`（React Flow） |
| 不用 | `@xrenders/xflow`、Formily Designable、XFlow 自带 FormRender |
| 右侧属性面板 | Ant Design Form（与自定义字段 Schema 渲染器同一套路：业务字段受控表单，不上画布库自带表单） |
| V1 范围 | 草稿编辑 + 可选自动布局；**不做**运行态高亮、仿真、执行进度 |

### 职责边界

- **画布** = 编辑器视图。拖拽、连线、选中、自动排布只影响前端交互与可选布局坐标。
- **领域真相** = `rtg_routing` + `rtg_routing_step` + `rtg_routing_edge`（见 [数据模型](v1-data-model.md#routing)）。
- 主路线：`is_rework = false` 的节点按 `step_no` 升序；**不靠边表达主序**（A9）。画布上可画「主序连线」仅作视觉辅助，保存时不落 `rtg_routing_edge`。
- 返工：只用边。`REWORK` 从主路线节点出（到返工节点，或回到前序主路线节点）；`RETURN` 从返工节点回主路线。

### 节点与边（画布 ↔ 领域）

| 画布 | 领域 | 说明 |
| --- | --- | --- |
| 节点类型 `OPERATION` | `rtg_routing_step`，`is_rework = false` | 主路线工序节点；属性含 `stepNo`、`operationId`、可选 `workCenterId`（仅厂专用路线）、`stdTimeSec`、`remark` |
| 节点类型 `REWORK_OPERATION` | `rtg_routing_step`，`is_rework = true` | 返工专用节点，不在主路线排序里 |
| 边类型 `MAIN`（可选、仅视觉） | **不持久化** | 主序相邻节点；保存时丢弃，加载时按 `step_no` 升序重画 |
| 边类型 `REWORK` | `rtg_routing_edge.edge_type = REWORK` | 可带 `defectCodeId` |
| 边类型 `RETURN` | `rtg_routing_edge.edge_type = RETURN` | 返工回主路线 |

客户端临时节点用本地 `clientKey`（或 React Flow `id`）；提交时 `steps[]` / `edges[]` 仍用 `stepNo` 互指（与现有 API 一致），服务端落库后换成 UUID。

### 投影规则

**保存（画布 → 领域，写 `PUT /routings/{id}`）**

1. 收集所有 `OPERATION` / `REWORK_OPERATION` 节点 → `steps[]`（`stepNo`、`operationId`、`isRework`、…）。**全路线**（含返工节点）`stepNo` 唯一且 > 0，与 `uk_rtg_routing_step_no` 一致；建议主路线用 10、20、30，返工用另一号段（如 110、120）。
2. 只把 `REWORK` / `RETURN` 边写入 `edges[]`（`fromStepNo`、`toStepNo`、`edgeType`、`defectCodeId`）。忽略或剥离 `MAIN` 边。
3. 可选：把各节点画布坐标写入 `layout`（见下），与业务字段同请求提交。仅 `DRAFT` 可写；发布后 `layout_json` **冻结**（与步骤/边一并只读）。
4. 业务校验与发布规则与现有一致（至少一个主路线节点；每个返工节点至少一条入 `REWORK`、一条出 `RETURN`；`REWORK` 回主路线时只能指向 `step_no` ≤ 起点的节点；工序未停用；跨厂规则等）。画布不另设业务校验通道；`layout` 仅做白名单与体积校验。

**加载（领域 → 画布）**

1. `steps[]` → 节点：`isRework` 决定类型；标签优先显示工序名称（前端另查工序列表）。
2. 按主路线 `step_no` 升序生成视觉 `MAIN` 边（不来自库）。
3. `edges[]` → `REWORK` / `RETURN` 边。
4. 若有 `layout.nodes[stepNo]`，用其 `{ x, y }`；否则走自动布局（见下）。

### 布局 `layout`（可选，不进业务）

存在 `rtg_routing.layout_json`（`jsonb`，可空）。API 字段名 `layout`。

```json
{
  "schemaVersion": 1,
  "nodes": {
    "10": { "x": 120, "y": 80 },
    "20": { "x": 320, "y": 80 },
    "30": { "x": 320, "y": 220 }
  }
}
```

| 规则 | 约定 |
| --- | --- |
| 用途 | 仅编辑器坐标；不参与发布校验；生效查询可不返回（前端编辑草稿才依赖） |
| 可写状态 | 仅 `DRAFT`。`RELEASED` / `OBSOLETE` 写入 `layout`（或改步骤）→ 409 `STATE_CONFLICT`；发布瞬间保留当时坐标并冻结 |
| 白名单（对齐 A20，**拒绝不裁剪**） | 根对象只允许 `schemaVersion`、`nodes`；`schemaVersion` 必须为 `1`；`nodes` 为对象。每个 `nodes` 值只允许 `x`、`y`（有限 number）。出现任何未知键 → 422 `LAYOUT_INVALID` |
| 键 | `nodes` 的键 = **本请求** `steps[].stepNo` 的十进制字符串（含返工）。显式提交 `layout` 时：键不属于本请求步骤、或非正整数字符串 → 422 `LAYOUT_INVALID`（**拒绝**，不裁剪） |
| 完整度 | 允许只给部分步骤坐标；缺坐标的步骤由前端自动布局补齐。显式 `layout` 不允许「多出来的」键 |
| 体积 | UTF-8 序列化后 ≤ 64 KB，否则 422 `LAYOUT_TOO_LARGE` |
| 省略 / 清空 | 请求**省略** `layout`：保留已存坐标，但在**同一事务**内按本请求新 `steps` **自动剔除**已存 `nodes` 中的孤儿键（避免删工序后再次提交原 layout 永久 422）。显式 `null` = 整段清空。读到 `null` 或某 step 无坐标 → 前端自动布局（V1：主路线横排，返工节点放在对应 `REWORK` 起点下方） |
| 复制 | `POST .../copy` **一律带上**源版本 `layout_json` 到新草稿。V1 copy 保持 `stepNo` 不变，坐标键可直接拷贝；若日后允许重编号，须按映射重写键 |

### 右侧属性

- 选中节点 / 边 → 右侧 Ant Design Form 编辑领域字段（工序、工作中心、工时、不良代码等）。
- 不使用 XFlow / Designable 的属性面板或 FormRender。
- 路线头信息（编码、版本、名称、适用物料、备注）仍在页头或侧栏普通表单，不塞进节点。

### 明确不做（V1）

- 运行态高亮、在制品位置、仿真节拍。
- 把主序边写入 `rtg_routing_edge`。
- 在布局 JSON 里存业务字段或校验规则。
- 引入第二套流程引擎或 BPMN 交换格式。
