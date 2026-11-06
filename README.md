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
