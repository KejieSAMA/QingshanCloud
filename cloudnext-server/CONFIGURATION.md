# 独立运行配置

CloudNext 不再需要从本地 O2 目录生成配置。原 `scripts/sync-onlyoffice-from-oa.ps1` 已删除。现有 `config/application.yml` 原样保留，旧配置中的 OA 字段仅属历史兼容项；Form 模式不使用其登录或组织数据库接口，不要执行旧 OA 同步流程。

## 配置来源

从本项目目录启动 Spring Boot，自动读取资源默认配置及 `config/application.yml`；环境变量和启动参数可以覆盖。`config/application.example.yml` 是可入库模板，不由 Spring Boot 自动加载。首次部署可复制为 `application.yml`；已有部署不要覆盖真实配置。

| 配置变量 | 用途 |
| --- | --- |
| `CLOUDNEXT_LOCAL_DB_URL`、`CLOUDNEXT_LOCAL_DB_USERNAME`、`CLOUDNEXT_LOCAL_DB_PASSWORD` | 元数据库 |
| `CLOUDNEXT_STORAGE_LOCAL_ROOT` | 当前文件和历史版本的存储目录 |
| `CLOUDNEXT_FORM_BASE_URL` | API 访问 Form 的服务地址 |
| `CLOUDNEXT_FORM_SERVICE_TOKEN` | 独立集成服务密钥，仅后端可见 |
| `CLOUDNEXT_FORM_SSO_START_URL` | 浏览器获取 Form SSO 票据的入口 |
| `CLOUDNEXT_FORM_SSO_ENABLED` | 是否显示 Form 免登入口 |
| `CLOUDNEXT_CALLBACK_BASE_URL`、`CLOUDNEXT_WEB_BASE_URL` | 外部回调及浏览器云盘入口 |
| `CLOUDNEXT_ONLYOFFICE_DOCUMENT_SERVER_URL`、`CLOUDNEXT_ONLYOFFICE_DOCSERVICE_API` | 独立文档服务及其 API 脚本 |
| `CLOUDNEXT_ONLYOFFICE_JWT_ENABLED`、`CLOUDNEXT_ONLYOFFICE_JWT_SECRET`、`CLOUDNEXT_ONLYOFFICE_JWT_HEADER` | 文档服务鉴权配置 |
| `CLOUDNEXT_ONLYOFFICE_EDIT_ENABLED` | Office 在线编辑开关 |
| `CLOUDNEXT_ADMIN_PERSON_KEYS` | canonical 管理员名单 |

Form 集成使用 HTTPS；本地 HTTP 联调需显式配置 `CLOUDNEXT_FORM_ALLOW_INSECURE_HTTP=true`。服务密钥不得复用 Form JWT 密钥，也不得放入前端 `VITE_*` 变量。OnlyOffice JWT 必须与文档服务的配置一致。

## 启动路径

当前数据保留在本项目内的 `cloudnext-server/.data/`（相对于后端目录）。默认资源配置以此为基准；已有本机覆盖配置和环境变量可能指向其他路径，必须核对最终值。不要因为目录名重复而重命名或合并数据库/存储目录，也不要把历史备份作为当前数据库打开。

PowerShell 从后端目录启动现有 JAR：

```powershell
java -jar target/cloudnext-server-0.1.0-SNAPSHOT.jar --server.port=8088
```

Spring Boot 不自动加载 `.env`。使用操作系统环境变量或外部启动环境注入，不把真实凭据写进启动脚本。健康检查使用 `GET /actuator/health`，认证配置状态使用 `GET /api/auth/status`；状态接口不能代替真实账号登录验收。

## 存储与备份

H2 数据库、当前上传内容、历史版本、真实配置及现有发布备份均为本机数据，被 `.gitignore` 排除。跨机器部署必须单独迁移这些数据及配置，不能只克隆源码后创建空数据库。此次目录隔离不变更数据结构，不移动或清理存储对象。
