# 独立运行配置

CloudNext 不再需要从本地 O2 目录生成配置。原 `scripts/sync-onlyoffice-from-oa.ps1` 已删除。现有 `config/application.yml` 原样保留，旧配置中的 OA 字段仅属历史兼容项；Form 模式不使用其登录或组织数据库接口，不要执行旧 OA 同步流程。

## 配置来源

从本项目目录启动 Spring Boot，自动读取资源默认配置及 `config/application.yml`；环境变量和启动参数可以覆盖。`config/application.example.yml` 是可入库模板，不由 Spring Boot 自动加载。首次部署可复制为 `application.yml`；已有部署不要覆盖真实配置。

| 配置变量 | 用途 |
| --- | --- |
| `CLOUDNEXT_DB_URL`、`CLOUDNEXT_DB_USERNAME`、`CLOUDNEXT_DB_PASSWORD` | 必填 MySQL 元数据库配置，不提供 H2 缺省值 |
| `CLOUDNEXT_LOCAL_DB_URL/USERNAME/PASSWORD` | 旧变量兼容项，只接受 MySQL，新变量优先，即使新值为空也不回退 |
| `CLOUDNEXT_TEST_DB_URL/USERNAME/PASSWORD`、`CLOUDNEXT_TEST_ENVIRONMENT` | 隔离 MySQL 测试配置，不供正式服务使用 |
| `CLOUDNEXT_SCHEDULING_ENABLED` | 对应 `cloudnext.scheduling.enabled`；测试及数据库维护时关闭所有定时任务 |
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

必须先核对实际 MySQL 库与文件存储目录。初次建表显式执行版本化 SQL；正式启动固定 `ddl-auto=validate`，不执行 Hibernate `update/create-drop`。连接池最多 5 个连接，驱动固定为 `com.mysql.cj.jdbc.Driver`，JDBC/Hibernate 时区为 UTC。MySQL 连接失败时启动失败，不创建本地 H2。

新变量优先于旧变量及外部配置中的数据源值。已有 H2 的 `config/application.yml` 仍作为旧部署配置保留，不能原样用于新版；切库时更新数据库项与驱动，其他 Form、OnlyOffice 配置保持不变。JDBC URL 必须明确库名，不允许把用户名或密码嵌入 URL。

PowerShell 从后端目录启动现有 JAR：

```powershell
java -jar target/cloudnext-server-0.1.0-SNAPSHOT.jar --server.port=8088
```

Spring Boot 不自动加载 `.env`。使用操作系统环境变量或外部启动环境注入，不把真实凭据写进启动脚本。健康检查使用 `GET /actuator/health`，认证配置状态使用 `GET /api/auth/status`；状态接口不能代替真实账号登录验收。

## 存储与备份

MySQL 备份、历史 H2、当前上传内容、历史版本和真实配置均不入库。远程元数据目标为独立 `qingshancloud` 库，存储保持 `D:\QingshanCloud\data\drive-storage`；不能只克隆源码后启动空库。MySQL 切换步骤、测试隔离和恢复边界见 [迁移手册](MYSQL_MIGRATION_RUNBOOK.md)。
