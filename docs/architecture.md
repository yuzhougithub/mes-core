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

不要写入 `widget`、`x-component`、`x-decorator`、`x-designable-id`、Formily 的 `x-reactions`，或其他渲染库专有字段。渲染器忽略不认识的键；存储原样保留。

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
