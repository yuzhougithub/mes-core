# V1 基础框架规格：用户、组织、权限、系统配置

状态：草案，待用户评审，未实现。基线 `main@9d1681d`。约定（uuid 主键、审计列、`lock_version`、`deleted`、错误体、分页、乐观锁）沿用 [总览](v1-master-data-overview.md) 与 [接口规范](v1-api.md)。参照若依 / JeecgBoot 的通用能力取舍，只做私有化多工厂 MES 用得上的部分。【假设 Fn】为待确认点。

## 1. 范围与拆分

| 能力 | V1 | 往后推 |
| --- | --- | --- |
| 登录、JWT、刷新令牌、切换工厂、登出 | ✔ | 单点登录（LDAP / OIDC）、扫码 / 刷卡登录（报工终端，V2） |
| 密码策略、失败锁定、首次登录强制改密、管理员重置 | ✔ | 密码历史、验证码、双因素 |
| 用户（账号 ↔ 人员）、用户-工厂多对多、默认工厂 | ✔ | 在线用户 / 强制下线 |
| 组织树：集团 → 工厂 → 车间 → 产线（只读投影） | ✔ | 多集团 / 多法人 |
| 部门（树，工厂内）、岗位 | ✔ | |
| 菜单 + 按钮权限点、角色、角色授权 | ✔ | 字段级权限、行为审批流 |
| 统一数据权限（5 种范围） | ✔ | 按字段值的规则式数据权限（JeecgBoot 那种） |
| 字典（已有，补管理页与缓存） | ✔ | |
| 系统参数 | ✔ | |
| 编码规则（已有，放开业务类型，补管理页） | ✔ | |
| 登录日志、操作审计日志 | ✔ | 日志归档 / 导出到外部 |
| 附件（已有元数据，补业务绑定与上传下载接口） | ✔ | 在线预览、分片上传 |
| 通知公告、站内消息 | | ✔ V1.x |
| 定时任务管理、代码生成器、多语言、多租户 | | 不做或 V2 |

拆分顺序（每项一个后端 PR + 一个前端 PR，前一项合并后再开下一项）：

1. **F1 认证与用户**：`V3__foundation.sql` 全量表 + 种子；登录 / 刷新 / 登出 / 切厂 / 改密；`jwt` 成为唯一生产模式；用户管理页。
2. **F2 菜单与角色**：菜单树、权限点、角色授权；后端 `@RequiresPerm` 全面替换三档角色；前端动态菜单与按钮显隐。
3. **F3 部门、岗位与数据权限**：部门树、岗位、组织树投影；`DataScopeInnerInterceptor`。
4. **F4 字典、系统参数、编码规则**管理页与缓存。
5. **F5 登录日志与操作日志**。
6. **F6 附件**业务绑定与上传下载。

## 2. 模块

后端仍是单进程按领域分包。新增 `system` 包，`auth` 包从骨架补全。

| 包 | 内容 |
| --- | --- |
| `auth` | 登录、令牌、密码策略、`CurrentContext`（用户、人员、工厂、部门、权限点、数据范围）、`@RequiresPerm` 校验 |
| `org` | 原有工厂 / 车间 / 工作中心 / 人员 / 班组；新增部门、岗位、组织树投影 |
| `system` | 菜单、角色授权、系统参数、登录日志、操作日志 |
| `config` | 原有字典、编码规则、附件、自定义字段 |
| `common.persistence` | 原有 `PlantLineInnerInterceptor`；新增 `DataScopeInnerInterceptor` |

## 3. 数据模型

迁移 `V3__foundation.sql`（后端在 F1 写，表一次建齐，按需分 PR 用）。只列关键列，审计列等略。

### 3.1 组织

组织树不新建统一节点表，避免和现有表双写：**集团**是单例（`sys_config` 的 `org.group.name`），**工厂 / 车间 / 产线**就是现有 `org_plant` / `org_workshop` / `org_work_center`（产线 = `work_center_type = LINE` 的工作中心）【假设 F1】。`GET /org/tree` 实时拼出这棵树。

| 表 | 关键列 | 说明 |
| --- | --- | --- |
| `org_dept` | `plant_id` 可空，`parent_id`，`code`，`name`，`ancestors`（`/根id/…/本id/`），`leader_person_id`，`sort_no`，`enabled` | 部门树。`plant_id` 空 = 集团级部门。`ancestors` 用于“本部门及以下”前缀匹配；移动节点时同事务改子树 `ancestors`。有子部门或有人员时不可删 |
| `org_post` | `code`，`name`，`sort_no`，`enabled` | 岗位，全局 |
| `org_person` | 加 `dept_id`（主部门） | |
| `org_person_post` | `person_id`，`post_id` | 多对多 |

### 3.2 账号与工厂绑定

人员与账号**严格一对一**（F2 已确认）：每个人员都有且只有一个账号，扫码报工的工人也不例外。新建人员与新建账号在同一事务内完成（`POST /users` 同时写 `org_person` 与 `sys_user`）；不单独新建人员，也不单独删除账号。主数据里已有的 `POST /persons` 并入 `POST /users`（F1 实现时下线或改为同义接口），`DELETE /persons/{id}` 同时逻辑删除账号。停用账号即停用人员登录，人员仍可在历史记录中被引用。

| 表 | 关键列 | 说明 |
| --- | --- | --- |
| `sys_user` | `username`（唯一，小写），`person_id`（唯一，非空，外键），`password_hash`（BCrypt），`status`（`ACTIVE`/`DISABLED`），`super_admin`，`must_change_pwd`，`pwd_changed_at`，`fail_count`，`locked_until`，`default_plant_id`，`last_login_at`，`last_login_ip` | 用户 = 账号；迁移时为 `org_person` 现有每条记录补建账号（`username` = 工号小写，`must_change_pwd = true`，随机初始密码由管理员重置后下发） |
| `org_person_plant` | 已有；加 `is_default`（每人至多一条 true，部分唯一索引） | 用户-工厂多对多即人员任职，不另建表 |
| `auth_refresh_token` | `user_id`，`token_hash`（SHA-256），`family_id`，`expires_at`，`revoked_at`，`ip`，`user_agent` | 用一次换一次；旧令牌被重放时整个 `family_id` 作废 |

登录时当前工厂取顺序：`default_plant_id`（且仍有有效任职）→ 上次工厂 → 第一个有效任职；都没有 → 403 `NO_PLANT_ASSIGNMENT`。

### 3.3 菜单、角色

| 表 | 关键列 | 说明 |
| --- | --- | --- |
| `sys_menu` | `parent_id`，`type`（`DIR`/`MENU`/`BUTTON`），`name`，`perm_code`，`path`，`component`，`icon`，`sort_no`，`visible`，`enabled` | `MENU` 对应前端路由；`BUTTON` 只承载 `perm_code`。页面可增删改三种类型（F3 已确认），迁移种子仅作初始数据。约束见下 |
| `org_role` | 已有；加 `data_scope`（见 §4.2），`enabled`，`sort_no`，`builtin` | `builtin` 角色不可删 |
| `sys_role_menu` | `role_id`，`menu_id` | 功能权限 |
| `sys_role_data_dept` | `role_id`，`dept_id` | `data_scope = CUSTOM` 时的部门集合 |
| `org_person_plant_role` | 已有 | 角色按工厂授予：同一人在不同工厂可有不同角色 |

菜单约束：

- 层级：`DIR` 的父节点只能是根或 `DIR`；`MENU` 的父节点只能是 `DIR` 或根；`BUTTON` 的父节点必须是 `MENU`，且不能有子节点。违反 → 422 `MENU_PARENT_INVALID`。
- `perm_code`：全局唯一（部分唯一索引 `WHERE perm_code IS NOT NULL AND NOT deleted`），重复 → 409 `DUPLICATE_PERM_CODE`。`BUTTON` 必填；`MENU` 选填（填了表示进入页面需要该权限点）；`DIR` 不填。格式 `^[a-z][a-z0-9-]*(:[a-z][a-z0-9-]*){1,3}$`，否则 422。
- `MENU` 必填 `path`（同级唯一）与 `component`（前端已注册的页面键，未注册的前端显示“页面不存在”，后端不校验）。
- 删除：有子节点 → 409 `MENU_HAS_CHILDREN`；被 `sys_role_menu` 引用 → 409 `MENU_IN_USE`（返回引用角色编码），需先在角色里取消授权。逻辑删除。
- 移动：不能移到自身或子孙下（422 `MENU_PARENT_INVALID`），同样受层级规则约束。菜单的创建、修改、删除在事务开头取 `pg_advisory_xact_lock(hashtext('sys_menu_tree'))`，然后再读祖先链。库内另有 DEFERRABLE 约束触发器，用递归 CTE 拒绝成环。
- 祖先链上任一节点 `enabled = false` 或已删除时，子孙按钮的 `perm_code` 不进入权限快照，调用即 403。
- 改 `perm_code` 或删除后，持有相关角色的用户权限版本 `ver` 加一。停用父节点时，持有其子孙菜单的用户同样 `ver` 加一。
- 后端代码里 `@RequiresPerm` 用到但库中不存在的 `perm_code`，启动时告警（不失败），便于发现菜单被误删。

权限点编码 `模块:资源:动作`，全小写，例 `md:material:list`、`md:material:create`、`md:material:update`、`md:material:delete`、`md:material:release`、`md:material:import`、`md:material:export`、`sys:user:reset-pwd`。动作集合固定为 `list / view / create / update / delete / enable / release / obsolete / import / export`，特殊动作单列。种子角色：`ADMIN`（全部权限点，数据范围 `PLANT`）、`EDITOR`、`VIEWER`，与原三档语义一致。`super_admin = true` 的用户跳过功能和数据权限，种子一个 `admin`，`must_change_pwd = true`。

### 3.4 系统配置与日志

| 表 | 关键列 | 说明 |
| --- | --- | --- |
| `sys_config` | `key`（唯一），`value`，`value_type`（`STRING`/`INT`/`BOOL`/`JSON`），`name`，`builtin`，`remark` | 系统参数。密码策略、锁定阈值、会话时长等走这里 |
| `cfg_code_rule` | 去掉 `entity_type` 的 CHECK，改名 `biz_type`，值来自字典 `code_rule_biz_type` | 后续工单、批次号等直接加字典项即可 |
| `cfg_file_object` | 加 `biz_type`，`biz_id`，`sha256` | 附件绑定；`biz_id` 空 = 已上传未绑定，24 小时后清理 |
| `sys_login_log` | `username`，`user_id`，`result`（`SUCCESS`/`BAD_CREDENTIALS`/`LOCKED`/`DISABLED`/`NO_PLANT`），`ip`，`user_agent`，`created_at` | 只增不改。按月分区【假设 F4】 |
| `sys_oper_log` | `user_id`，`plant_id`，`module`，`action`，`biz_id`，`method`，`path`，`result`（`OK`/`FAIL`），`error_code`，`duration_ms`，`request_summary`（jsonb，已脱敏），`trace_id`，`created_at` | 只记写操作。只增不改，按月分区；默认保留 180 天（`sys_config`） |

## 4. 权限模型

请求依次过三道，任何一道失败都拒绝，不降级：

```
认证（JWT） → 功能权限（@RequiresPerm） → 数据权限（工厂行过滤 + 数据范围）
```

### 4.1 认证

- 生产只有 `jwt` 模式。`header` 模式（`X-Plant-Id` / `X-User-Id`）只在 `dev` profile 生效；非 `dev` profile 配成 `header` 启动即失败（已有 `AuthModeGuard`）。
- `jwt` 模式下令牌缺失、签名错、过期、`jti` 已登出、用户已停用或已锁、`plt` 已无有效任职，一律 401 / 403 拒绝。请求另带 `X-Plant-Id` 且与 `plt` 不一致 → 400 `PLANT_MISMATCH`。
- Access token：HS256，30 分钟；声明 `sub`（用户 id）、`pid`（人员 id）、`plt`、`ver`（用户权限版本）、`jti`、`iat`、`exp`。**不放角色和权限点**，避免令牌膨胀和授权变更滞后。
- 权限快照（权限点集合、数据范围、部门 id）由服务端按 `(userId, plantId, ver)` 缓存（Caffeine，单进程）。改用户的角色 / 任职 / 部门、改角色授权时，`ver` 加一并清缓存，旧令牌下次请求即按新权限生效。
- Refresh token：随机 256 位，7 天，`HttpOnly; Secure; SameSite=Strict` cookie，路径 `/api/v1/auth`。Access token 只放内存。
- 密码策略（`sys_config`，默认值）：最少 8 位、含大小写字母 / 数字 / 符号中至少 3 类、不能含用户名；连续失败 5 次锁 15 分钟；有效期 0（不过期）；首次登录和管理员重置后强制改密。改密后作废该用户全部 refresh token。

### 4.2 功能权限

- 后端：Controller 方法标 `@RequiresPerm("md:material:update")`（可多个，`anyOf`）。没有该注解且不在白名单（登录、健康检查）的接口启动时报错，防止漏标【假设 F5】。不满足 → 403 `PERMISSION_DENIED`，返回所需权限点编码。
- **全局资源只允许超级管理员写**（F2）。`@RequiresPerm(global = true)` 的接口，非超级管理员一律 403，即使当前厂角色持有该权限点。已标 `global` 的写操作：工厂的创建 / 修改 / 删除；菜单的创建 / 修改 / 删除；角色定义的创建 / 修改 / 删除（含启用与停用）；`PUT /roles/{id}/menus`。厂级管理员仍可在本厂把**已有**角色授给其他用户（`POST /users`、`PUT /users/{id}/plants`）。此时每个角色的全部权限点必须是操作者在当前厂所持权限点的子集；非超级管理员改角色菜单时，除 `DIR` 外每个所选节点的 `perm_code` 也必须落在自己的权限内。超出 → 403 `PERMISSION_DENIED`。`header` 模式（仅 dev/test）仍视为完全访问，包含这些全局写操作。
- 前端：登录后取 `GET /auth/me`，拿到菜单树和权限点集合；路由按菜单生成；按钮用 `<Perm code="…">` 包裹，无权限不渲染（不是置灰）。前端只做体验，后端是唯一裁决。

### 4.3 数据权限

两层，都在 MyBatis-Plus 拦截器里统一做，业务 Mapper 和 Service 不写过滤条件。

**第一层：工厂行过滤（已有）**。`PlantLineInnerInterceptor` 按 `PlantScope` 表登记，自动加 `plant_id` 条件。不变。

**第二层：数据范围（新增）**。角色的 `data_scope`：

| 值 | 含义（在当前工厂内） | 生成条件 |
| --- | --- | --- |
| `ALL` | 全部，且允许跨厂汇总接口 | 无附加条件；标了 `@CrossPlant` 的查询同时跳过第一层，限定为本人有任职的工厂 |
| `PLANT` | 本工厂全部 | 无附加条件 |
| `DEPT_AND_CHILD` | 本部门及以下 | `dept_id IN (SELECT id FROM org_dept WHERE ancestors LIKE :myAncestors || '%')` |
| `DEPT` | 仅本部门 | `dept_id = :myDept` |
| `SELF` | 仅本人 | `created_by = :myUserId`（或表登记的负责人列） |
| `CUSTOM` | 自定义部门 | `dept_id IN (:roleDeptIds)` |

- 用户在当前工厂有多个角色时取并集：任一角色是 `ALL`/`PLANT` 即不加条件；否则各角色条件 `OR` 合并。
- 生效方式：表登记 + 注解。`DataScopeTable` 登记哪些表参与以及用哪个部门列、本人列（与 `PlantScope` 同一风格）；Mapper 方法或 Service 方法标 `@DataScope`（可选 `alias` 指定多表 join 时作用的表别名）才启用，未标的方法不加条件。实现基于 MyBatis-Plus `DataPermissionInterceptor` + `MultiDataPermissionHandler`，放在分页拦截器之前。
- 主数据（物料、BOM 等）是工厂级共享资源，**不登记**数据范围，只受第一层约束。V1 登记：`org_person`（`dept_id`、`created_by`）、`sys_user`（经 `person.dept_id`）、`sys_oper_log`（`user_id`）。工单等后续业务表建表时带 `dept_id` 并登记。
- 按 id 读一条超出数据范围的记录 → 404（与跨厂一致，不暴露存在性）；写超出范围的记录同样 404。
- `super_admin` 跳过第二层，但仍受第一层（当前工厂）约束，跨厂只走 `@CrossPlant`。

### 4.4 审计

- 写接口标 `@AuditLog(module="md", action="material.update")`；拦截器异步写 `sys_oper_log`，失败不影响业务。
- `request_summary` 只记 id、编码和变更字段名，不记字段值；密码、令牌、`mobile`、`email` 一律不记。
- 登录日志在认证过滤器里同步写（量小，且锁定判断需要可靠）。

## 5. API 清单

前缀 `/api/v1`。列表均为分页查询，参数沿用 `page`、`size`、`sort`。括号内为权限点；“登录”表示登录即可。

| 资源 | 接口 | 权限点 |
| --- | --- | --- |
| 认证 | `POST /auth/login`、`POST /auth/refresh`、`POST /auth/logout`、`POST /auth/switch-plant`、`POST /auth/change-password`、`GET /auth/me`（用户、当前工厂、可切换工厂、菜单树、权限点） | 匿名 / 登录 |
| 用户 | `GET/POST /users`、`GET/PUT/DELETE /users/{id}`、`POST /users/{id}/enable`、`/disable`、`/unlock`、`/reset-password`、`PUT /users/{id}/plants`（任职 + 每厂角色 + 默认工厂整体替换） | `sys:user:*`，`sys:user:reset-pwd`、`sys:user:grant` |
| 组织树 | `GET /org/tree`（集团 → 工厂 → 车间 → 产线） | 登录 |
| 部门 | `GET /depts/tree`、`POST /depts`、`PUT/DELETE /depts/{id}`、`POST /depts/{id}/move` | `org:dept:*` |
| 岗位 | `GET/POST /posts`、`PUT/DELETE /posts/{id}` | `org:post:*` |
| 菜单 | `GET /menus/tree`、`GET /menus/{id}`、`POST /menus`、`PUT /menus/{id}`（含改父节点）、`DELETE /menus/{id}?lockVersion=` | `sys:menu:list/view` 登录角色；`create/update/delete` 为 `global`，仅超级管理员 |
| 角色 | `GET/POST /roles`、`PUT/DELETE /roles/{id}`、`GET/PUT /roles/{id}/menus`、`PUT /roles/{id}/data-scope`（`dataScope` + `deptIds`）、`GET /roles/{id}/users` | 读 `sys:role:list/view`；角色定义写与 `PUT /menus` 为 `global`，仅超级管理员。厂级管理员可按 §4.2 把已有角色授给本厂用户 |
| 字典 | 已有 `/dict-types`、`/dict-items`；新增 `GET /dicts/{typeCode}`（前端缓存用，带 `ETag`） | 已有 / 登录 |
| 系统参数 | `GET /configs`、`PUT /configs/{key}`；`GET /configs/public`（前端可读子集） | `sys:config:*` / 匿名 |
| 编码规则 | 已有 `/code-rules`；新增 `POST /code-rules/{id}/preview`（不占号） | `sys:code-rule:*` |
| 附件 | `POST /files`（multipart，返回 id）、`GET /files/{id}/download`（302 到 MinIO 预签名 URL，5 分钟）、`PUT /files/bind`（`bizType`、`bizId`、`fileIds`）、`DELETE /files/{id}` | 按业务权限点校验 |
| 登录日志 | `GET /login-logs` | `sys:login-log:list` |
| 操作日志 | `GET /oper-logs`、`GET /oper-logs/{id}` | `sys:oper-log:list` |

新错误码：401 `UNAUTHENTICATED`、`TOKEN_EXPIRED`、`TOKEN_INVALID`；403 `PERMISSION_DENIED`、`ACCOUNT_LOCKED`、`ACCOUNT_DISABLED`、`PASSWORD_CHANGE_REQUIRED`（只放行改密和登出）、`NO_PLANT_ASSIGNMENT`；400 `PLANT_MISMATCH`；422 `PASSWORD_POLICY`、`DEPT_NOT_EMPTY`、`BUILTIN_PROTECTED`、`MENU_PARENT_INVALID`；409 `DUPLICATE_PERM_CODE`、`MENU_HAS_CHILDREN`、`MENU_IN_USE`。登录失败统一返回 401 `BAD_CREDENTIALS`，不区分“用户不存在”和“密码错”。

## 6. 前端

沿用 PR #9 管理后台外框和列表模板：不放页面标题、不放 KPI 卡片；删除和停用用 Popconfirm；`mobile`、`email`、身份证类字段列表中脱敏（`138****5678`、`z***@x.com`），详情需 `sys:user:view-sensitive` 才明文【假设 F6】。

- 新增页面：登录、强制改密、用户、部门（左树右表单）、岗位、角色（列表 + 抽屉里分“功能权限”树勾选与“数据范围”两页）、菜单、字典、系统参数、编码规则、登录日志、操作日志。
- 顶栏工厂切换器改为调用 `switch-plant`，成功后清全部查询缓存。
- Access token 401 `TOKEN_EXPIRED` 时自动调一次 `refresh` 并重放请求；`refresh` 失败跳登录页。

## 7. 待确认

| 编号 | 假设 |
| --- | --- |
| F1 | 组织树不建统一节点表；产线 = 类型为 `LINE` 的工作中心 |
| F2 | 已确认：人员与账号严格一对一，每个人员都有账号 |
| F3 | 已确认：菜单可在页面增删改，种子仅作初始数据 |
| F4 | 两张日志表按月分区，操作日志保留 180 天 |
| F5 | 写接口漏标 `@RequiresPerm` 时启动失败 |
| F6 | 敏感字段明文需要单独权限点 |
