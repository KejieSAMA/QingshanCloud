# CloudNext 从 O2OA 切换至 Form 运行手册

本手册只描述显式人工切换。应用不会自动改写 `person_oa_id`、`owner_person_oa_id`、`target_id` 或 `scope_id`，也不会自动删除孤立存储对象。

## 1. 配置与影子同步

- 影子期可继续使用 O2 登录并设置 `CLOUDNEXT_FORM_SHADOW_SYNC_ENABLED=true`；完成映射并切换时再设置 `CLOUDNEXT_AUTH_PROVIDER=FORM`、`CLOUDNEXT_AUTH_MODE=form`，确认后可关闭 shadow 开关。
- 设置 Form HTTPS 地址、独立服务令牌和 SSO 入口：`CLOUDNEXT_FORM_BASE_URL`、`CLOUDNEXT_FORM_SERVICE_TOKEN`、`CLOUDNEXT_FORM_SSO_START_URL`、`CLOUDNEXT_FORM_SSO_AUDIENCE`。
- 设置 CloudNext 回调与 Web 地址；生产环境保持 `CLOUDNEXT_FORM_ALLOW_INSECURE_HTTP=false`。
- Form 必须运行于 `FOUNDATION_SOT=sql`、`FOUNDATION_WRITE_IMPL=sql`。旧目录基线导入或 crosswalk 决策后调用 `POST /api/admin/form-sync/trigger?force=true`，确保即使 ETag 未变也重新执行 canonical reconcile；随后检查 `GET /api/admin/form-sync/status` 的 ETag 和成功时间。
- O2 镜像同步在 Form 模式被禁用；旧镜像只作为归档/映射候选保留。

## 2. 最终 O2 归档与映射

1. 在首次 Form 人员定案前，先从 O2 导出 Person（必须包含真实 `xemployee`）、Unit、Identity、Role、Group、Membership，提交至 `POST /api/admin/form-migration/tasks/preview`，版本号不可复用。没有 `VERIFIED` 的完整旧目录基线时，Form-only 人员和身份保持 `PENDING`，不会被过早标记为 `NEW`。
2. 依次执行 `POST .../tasks/{version}/approve`、`apply`、`verify`。任务只能按 `PREVIEWED → APPROVED → APPLIED → VERIFIED` 推进；重复调用幂等，不允许同版本不同摘要。
3. 使用 `/api/admin/form-migration/principals|units|identities` 和 `/legacy/*` 完成决策；工号只允许唯一精确匹配，不使用 OA `xunique`、姓名、手机号或模糊匹配。
4. CSV 组织映射通过 `units/export.csv`、`units/import.csv` 管理。每次决策记录 `note`、操作者、前后值和时间，可在 `decisions/audit` 查看。
5. `GET /api/admin/form-migration/validation` 必须无 blocker。所有旧 PERSON/UNIT/IDENTITY 均需明确处置；Form `PENDING` principal 不允许登录。

## 3. 维护窗口与切换

1. 停止 OnlyOffice 新编辑并等待在途保存完成，备份 H2、存储目录及 Form 数据库。
2. 通过部署配置同时设置 `CLOUDNEXT_CUTOVER_MAINTENANCE_MODE=true` 和 `CLOUDNEXT_CUTOVER_MIGRATION_VERSION=<本次最终版本>` 并重启。维护模式冻结 `/api` 和 `/public` 的业务访问，但在清会话前窄放行最终 Form 同步和该归档任务的阶段推进；映射决策仍被冻结。
3. 在维护模式执行一次 `force=true` 的最终 Form 同步，确认结果为 `SUCCESS`，再创建并推进配置版本的最终 O2 归档任务。`preview` 同时锁定 Form ETag、成功 generation、成功时间和维护版本，并记录文件/目录、文件总大小、空间、成员、分享、外链、偏好、审计计数、metadata↔storage 双向差异和最多 20 个 SHA-256 抽样。`clear-sessions` 会在同一目录行锁内再次核对这些围栏、版本、checksum 和 inventory；同步失败或 preview 后发生任何新同步都会阻断切换，成功落 marker 后所有同步也会在该行锁内被拒绝。
4. 检查 `GET /api/admin/form-migration/cutover/status`，确认 `readyToClearSessions=true`。
5. 显式调用 `POST /api/admin/form-migration/cutover/clear-sessions`。它会清除 CloudNext 会话和 SSO bootstrap，并推进持久化 OnlyOffice 票据 not-before epoch，使切换前签发的编辑/预览票据永久失效。
6. 移除 O2 JDBC、认证地址和凭据配置，保留最终快照、映射和审计。设置 `CLOUDNEXT_CUTOVER_MAINTENANCE_MODE=false` 后重启恢复服务。

## 4. 回滚边界

只允许恢复“支持 Form 的上一应用版本 + 切换前数据库/存储备份”。O2 下线后不得回滚到依赖在线 O2 登录或 JDBC 的版本。发现 storage orphan 时先人工核对并恢复备份；应用不会自动清理。
