# mes-core

私有化多工厂离散 MES（精密 / 电子 / 机加）。工厂数据隔离，主数据可共享并按工厂扩展。本仓库是可运行骨架，第一刀只做 PC 端主数据，业务功能尚未实现。

## 技术栈

- API：Java 21、Spring Boot 4.1、MyBatis-Plus、Flyway、PostgreSQL、springdoc OpenAPI
- Web：React、TypeScript、Ant Design、Vite（`apps/web`）。动态表单 V1 用 Ant Design Form 与自研 Schema 渲染器，不加入 Formily。见 [架构备忘](docs/architecture.md)
- 运行：Docker Compose（API + PostgreSQL + MinIO）

## 目录

```
apps/api     Spring Boot 模块化单体
apps/web     PC 端骨架
docs         章程、架构备忘、V1 竖切规格
deploy       Docker Compose
```

## 本地运行

需要 Docker Compose。

```bash
docker compose -f deploy/docker-compose.yml up --build
```

这条 Compose 给 API 设置了 `SPRING_PROFILES_ACTIVE=dev`。`dev` profile 使用 `mes.auth.mode=header`：业务接口认请求头 `X-Plant-Id`（可选 `X-User-Id`）。只用于本机开发。

staging、生产以及其他非 dev 环境不要激活 `dev` profile，必须使用默认的 `jwt`。`jwt` 不信任客户端传来的 `X-Plant-Id`。JWT 尚未实现时，需要工厂上下文的接口会返回 400 `PLANT_REQUIRED`。

- API 健康检查：<http://localhost:8080/actuator/health>
- OpenAPI：<http://localhost:8080/swagger-ui/index.html>
- MinIO 控制台：<http://localhost:9001>（`minio` / `minioadmin`）。官方 `minio/minio` 镜像已停止匿名分发，Compose 使用兼容构建 `pgsty/silo`（同一 `server` 命令、端口与 `MINIO_ROOT_*`）。
- PostgreSQL：`localhost:5432`，库 `mes`，用户 `mes`，密码 `mes`（仅本地开发）

前端单独起：

```bash
cd apps/web
npm install
npm run dev
```

打开 <http://localhost:5173>。开发服务器把 `/api`、`/actuator`、`/v3`、`/swagger-ui` 代理到 `localhost:8080`。

## 品牌：logo 与系统名

顶栏 logo、系统名和可选副标题在浏览器运行时读取，不写死在界面里。默认系统名是「MES 制造执行系统」，副标题默认为空，logo 默认为 `/favicon.svg`。

每个字段按这个顺序取值：

1. 站点上的 `/config.json`（部署后替换这个文件即可，不必重新构建前端）
2. 构建期环境变量 `VITE_APP_NAME`、`VITE_APP_LOGO`、`VITE_APP_SUBTITLE`（只填补配置文件里没有或无效的字段）
3. 上面的内置默认值

`apps/web/public/config.json` 会原样进到构建产物。本地直接改这个文件；用 nginx 或镜像时，用同名文件覆盖 `config.json`。页面加载时以 `cache: no-store` 请求它，文件读失败或字段无效就回退，不阻断页面。

```json
{
  "name": "华东精工 MES",
  "logo": "/brand/logo.svg",
  "subtitle": ""
}
```

- `name`：纯文本。空字符串视为无效并回退。内容按文本显示，不会当成 HTML 执行。
- `subtitle`：纯文本，可以是空字符串。配置里写了空字符串就保持为空，不会再去用环境变量。
- `logo`：以 `/` 开头的站内路径（不能是 `//`），或 `http`/`https` 地址。`javascript:` 和其他写法会丢弃并改用默认 logo。把图片放到 `apps/web/public/`（例如 `public/brand/logo.svg`），`logo` 填 `/brand/logo.svg`。

环境变量示例（构建前）：

```bash
VITE_APP_NAME="华东精工 MES" VITE_APP_LOGO="/brand/logo.svg" VITE_APP_SUBTITLE="" npm run build
```


## 只编译

API 测试会连接上面的 PostgreSQL。可先 `docker compose -f deploy/docker-compose.yml up -d postgres`，或本机自建同名库。

```bash
cd apps/api && ./mvnw -B verify
cd apps/web && npm ci && npm run build
```

## 文档

- [V1 主数据章程](docs/charter-v1-master-data.md)
- [架构备忘](docs/architecture.md)
- V1 主数据竖切（草案）：[总览](docs/v1-master-data-overview.md)、[数据模型](docs/v1-data-model.md)、[接口规范](docs/v1-api.md)、[Flyway 草案](docs/sql/V2__master_data.sql)
