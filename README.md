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

当前保留的数据目录为 `cloudnext-server/cloudnext-server/.data/`，包含 H2 元数据库和本地存储；另一个可能存在的 `cloudnext-server/.data/` 也保留。启动前核对 `CLOUDNEXT_LOCAL_DB_URL`、`CLOUDNEXT_STORAGE_LOCAL_ROOT` 和实际目录，避免连接到空数据库。此次隔离没有移动数据或执行数据库迁移。

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

检查使用模拟接口及静态分析，不操作真实文件。Java 测试仅在隔离环境和独立数据库中执行；不要在实际运行目录执行 `clean` 或使用生产数据库测试。

Android 环境说明见 [SETUP](cloudnext-android/docs/SETUP.md)，Form 迁移记录见 [运行手册](cloudnext-server/FORM_MIGRATION_RUNBOOK.md)。
