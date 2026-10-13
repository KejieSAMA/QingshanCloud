# 青山云盘 / CloudNext

独立云盘项目，包含 Java API、Vue PC/手机 Web 和原生 Android 客户端。账号认证、目录同步和 SSO 由外部 Form 服务提供；Office 预览/编辑连接独立 OnlyOffice 服务。

## 目录

| 目录 | 用途 |
| --- | --- |
| `cloudnext-server/` | Spring Boot API、权限、存储、目录同步及迁移工具 |
| `cloudnext-web/` | Vue 应用，统一 PC 与手机 Web 入口 |
| `cloudnext-android/` | Kotlin / Compose 原生客户端 |
| `cloudnext-runtime-logs/` | 本机运行日志、已有发布备份及隔离检查清单，不入库 |

原 O2 单体项目及 Git 元数据已移除。独立云盘仓库为 https://github.com/KejieSAMA/QingshanCloud.git ，默认分支为 `main`，使用全新的提交历史。保留现有 LICENSE。

## 配置与数据

后端基础配置位于 `cloudnext-server/src/main/resources/application.yml`，本机覆盖配置位于 `cloudnext-server/config/application.yml`，真实配置不入库。独立配置模板为 `cloudnext-server/config/application.example.yml`。参考 [配置说明](cloudnext-server/CONFIGURATION.md)。

运行时优先级遵循 Spring Boot：命令行和环境变量可覆盖配置文件。后端不会自动读取 `.env`；需由启动环境注入对应变量。前端 Vite 按标准规则读取 `.env` 文件，浏览器可见变量不得包含服务密钥。

当前源码仅支持 MySQL 8.0：必须提供 `CLOUDNEXT_DB_URL`、`CLOUDNEXT_DB_USERNAME`、`CLOUDNEXT_DB_PASSWORD`，启动只校验表结构，不自动建表，也不回退 H2。正式部署目标为云盘专用 MySQL 实例中的 `qingshancloud` 库（本次环回 13307），不混用原有 3307 或 Form 的 13306。源码更新不代表旧部署已经完成切库，完整步骤见 [MySQL 迁移手册](cloudnext-server/MYSQL_MIGRATION_RUNBOOK.md)。

远程文件内容继续保存在 `D:\QingshanCloud\data\drive-storage`。本机历史 `cloudnext-server/cloudnext-server/.data/` 和另一个可能存在的 `.data/` 均保留；H2 文件仅用于旧库迁移或离线备份，不能直接启动新版应用。数据库迁移不改写文件 ID、Form canonical key 或 storage key。

所有 Form、OnlyOffice、回调和公网入口地址通过配置提供。Form 和 OnlyOffice 是独立外部服务，本项目不需要本地 O2 源码或运行目录。历史 OA canonical ID、归档快照及迁移说明保留用于数据连续性。

## 本地运行

后端使用 JDK 17，前端使用 Node.js 20.19+（或满足依赖要求的新 LTS）。Android 使用 JDK 17、Android SDK 和项目自带 Gradle Wrapper。

使用现有后端 JAR，从 `cloudnext-server/` 目录启动：

```powershell
java -jar target/cloudnext-server-0.1.0-SNAPSHOT.jar --server.port=8088
```

前端从 `cloudnext-web/` 目录启动：

```powershell
npm run dev -- --host 0.0.0.0 --port 5174 --strictPort
```

前端 `/api` 默认代理本机 8088，其他目标使用 `CLOUDNEXT_API_TARGET` 配置。首次安装前端依赖执行 `npm ci`。编译 API/Web/Android 需开发者明确发起，不在启动或清理时自动编译。

后端健康入口为 `/actuator/health`，认证配置状态入口为 `/api/auth/status`。UI 效果及真实点击交互由人工验收。

## 检查

在 `cloudnext-web/` 目录执行：

```powershell
npm run check:mobile-scope
npm test -- --exclude "src/next/tests/**"
node --test src/next/tests/navigation.test.mjs src/next/tests/isolation.test.mjs
```

检查使用模拟接口及静态分析，不操作真实文件。后端源码/建表 SQL 的非编译检查：

```powershell
node cloudnext-server/tools/mysql-schema.mjs
node --test cloudnext-server/tools/mysql-contract.test.mjs
```

Java 测试需要显式授权，在隔离副本中通过 SSH 隧道使用 `qingshancloud_test` 及专用账号。缺少测试配置、连接正式库或账号有跨库权限时拒绝执行，不再使用 H2 内存库。不要在实际运行目录执行 `clean`。

Android 环境说明见 [SETUP](cloudnext-android/docs/SETUP.md)，Form 迁移记录见 [运行手册](cloudnext-server/FORM_MIGRATION_RUNBOOK.md)。
