# Form 免登补同步与失败页

## 2026-09-14 部署核验更新

已按用户授权编译重启 CloudNext 服务与 Web，并将用户环境变量 `CLOUDNEXT_FORM_SSO_START_URL` 改为 `http://qr.tsingshan.cc:5173/`。公网状态接口已返回新地址。

运行日志发现 `unsupported form snapshot schemaVersion: 2`：当前 Form v2 是保留身份字段、增加主管关系的兼容扩展。目录校验现明确支持 1/2，其他版本仍拒绝，10 项隔离数据库测试通过。09:15 定时同步已成功处理 1,213 人、106 组织和 1,361 任职；原反馈员工绑定启用且映射为 NEW。未手工改写绑定、文件归属或共享权限。

本次最终 JAR 从独立构建目录替换，替换前版本保存在 `cloudnext-runtime-logs/backend-backups/`。最新后端运行日志为 `backend-20260914-091049.out.log`，Web 为 `frontend-20260914-090222.out.log`。外部 OnlyOffice 原有连接超时告警仍存在，与此次目录同步故障分开处理。

以下为开发阶段说明，部署状态以上述更新为准。

2026-09-14：绑定缺失时通过 `FormLoginDirectorySync` 合并目录补同步，复用 `FormDirectorySyncService`、现有事务应用服务与身份映射规则。一次免登票据只交换一次；补同步重试不会再次消费它。

登录补同步使用无 ETag 的完整快照；校验快照租户与已认证身份租户。相同目录源的持久化状态锁及一分钟冷却防止重复请求。等待定时或其他实例任务时读取最新状态；登录等待最多 15 秒，超时返回 `DIRECTORY_SYNC_PENDING`，后台继续完成。维护模式不会被自动补同步绕过。

创建会话与 bootstrap 移入 `SsoSessionIssuer` 的独立事务；网络调用不占用这个事务。补同步失败回滚绑定修改，另行保存失败状态。已有可用绑定直接登录；待确认、归档、停用或过期认证版本不自动放行。

浏览器 GET 回调返回 `/login?sso=error&code=...&returnPath=...`，清理旧 bootstrap Cookie，禁止缓存并禁止该响应继续传递 Referer。POST 保留原 HTTP 状态并补充 `code`。部署服务端与 Web 错误页时应一并更新，避免旧页面缺少恢复操作。

主要错误码：`DIRECTORY_SYNC_PENDING`、`DIRECTORY_SYNC_FAILED`、`DIRECTORY_BINDING_MISSING`、`IDENTITY_MAPPING_PENDING`、`IDENTITY_UNAVAILABLE`、`ACCOUNT_DISABLED`、`TICKET_INVALID`、`PASSWORD_CHANGE_REQUIRED`、`SSO_REQUEST_INVALID`、`SSO_UNAVAILABLE`。

## 运行配置

用户确认的外网 OA 地址对应以下服务端变量：

```dotenv
CLOUDNEXT_FORM_SSO_START_URL=http://qr.tsingshan.cc:5173/
```

它供浏览器返回 OA 获取新票据使用，不等同于服务到服务请求的 `CLOUDNEXT_FORM_BASE_URL`。本轮线上只读状态接口仍返回旧内网地址 `http://192.168.0.230:7099`，尚未更改部署配置或重启。

部署前读取现有管理员目录同步状态与日志，确认实际 Form 地址、快照租户、最近任务时间和失败原因。无部署主机连接信息时，不把本地历史日志当成当前线上证据；不直接修改绑定表、文件归属或共享权限。

## 验证

相关测试：`FormLoginDirectorySyncTest`、`FormAuthenticationProviderTest`、`FormLoginSyncIntegrationTest`、`FormDirectorySyncTransactionServiceTest`、`CloudNextSsoRecoveryTest`，并回归 `AuthSessionServiceTest`、`MigrationManagementServiceTest`。数据库验证使用独立 H2 内存库；故意注入的字段长度超限验证完整回滚。

Java 测试必须在隔离副本执行，副本的 `application.yml` 使用内存数据库并禁用定时任务；不要在实际运行目录直接执行 `clean`、覆盖 `target` 或连接生产数据库。执行 `mvn test` 仅产生测试所需 class，不执行 `package` 或 Spring Boot 重启。

Web 的 `e2e/sso-recovery.vite.config.js` 拦截所有 API 请求，可独立验证中文恢复页；生产配置不能引用这个测试文件。实际员工外网登录另行联调。
