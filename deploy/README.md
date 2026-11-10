# 部署

本地用仓库根目录的 Compose：

```bash
docker compose -f deploy/docker-compose.yml up --build
```

该 Compose 设置 `SPRING_PROFILES_ACTIVE=dev`，API 以 header 模式信任 `X-Plant-Id`，只给本机开发。staging 和生产不要激活 `dev` profile，使用默认的 `jwt`。

## 初始超级管理员

迁移只写入用户名 `admin` 和一个不可登录的口令哈希。进程启动时读取环境变量 `MES_ADMIN_INITIAL_PASSWORD`：

- **dev 以外的环境必填。** 账号尚未初始化时如果缺失或为空，API 拒绝启动。
- 只在 admin 仍是那个不可登录哈希时写入一次。之后再改这个变量不会重置已有口令。
- 至少 12 个字符，并满足密码策略（大小写、数字、符号中至少 3 类，且不能包含用户名 `admin`）。
- 写入后 `must_change_pwd` 为真，**首次登录必须改密**，否则除改密以外的接口返回 403 `PASSWORD_CHANGE_REQUIRED`。

示例：

```bash
MES_ADMIN_INITIAL_PASSWORD='替换成至少12位的初始口令' java -jar mes-api.jar
```

admin 还没有工厂任职时，登录得到的是受限会话，只能建厂、管理用户、改密和读取 `GET /api/v1/auth/me`。把该账号任职到工厂后，调用 `POST /api/v1/auth/refresh` 会换发带真实工厂的 access token。
