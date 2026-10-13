# Implementation Plan: CloudNext 后续迭代主线

## Overview

本任务清单按照 `requirements.md` 的三轮迭代主线（协作空间 → 外链管理 → 搜索/治理/存储平台化）以及 `design.md` 的组件分解，把后续迭代拆解为可在单个 PR 内完成的最小执行单元。每个阶段内部按照"后端实体 + 仓库 → Service → Controller → 前端 API 客户端 → 前端页面 → 测试"的顺序排列；阶段之间严格遵守 Requirement 1.2/1.3 的迭代门禁。

注意：

- 任务描述只写明改动点与验收钩子，**不自动触发 maven 编译、不自动重启 OnlyOffice、不自动跑全量回归**；构建/部署步骤需由用户显式发起（遵循 `AGENTS.md` 中"不要随意进行编译"约定）。
- 所有迭代二、三的接口默认通过 `cloudnext.iteration.*` flag 关闭；只有上一轮 `回归与验收` 全部勾选后才开启对应 flag。
- 每个叶子任务通过 `_Requirements:_` 反查需求条目，通过 `_Design:_` 反查设计章节。

## Tasks

### 阶段 0：前置基础（Scope 模型扩展 + 迭代门禁 flag）

- [x] 0.1 application.yml 扩配置项与迭代门禁 flag
  - 在 `cloudnext-server/config/application.yml` 增加 `cloudnext.iteration.*`（workspaces / public-links / search / governance / preview-health / storage-gateway.objectStorageEnabled）默认值
  - 增加 `cloudnext.public-link.*`（base-url / session-secret / session-ttl-seconds）
  - 增加 `cloudnext.storage.backend` 与 `cloudnext.storage.local.root`、`cloudnext.storage.s3.*` 占位
  - 增加 `cloudnext.drive.personal-trash-retention-days` / `workspace-trash-retention-days`，保留旧 `cloudnext.drive.trash-retention-days` 作为兜底
  - 不修改 `cloudnext.drive.onlyoffice.*` 现有值
  - _Requirements: 1.1, 1.9, 11.5, 12.5_
  - _Design: §"Configuration Inventory", §"Iteration Gate Strategy"_

- [x] 0.2 DriveItem 新增 scope/storage 字段与索引
  - 给 `DriveItem` 增加 `scopeType`（枚举 `PERSONAL|WORKSPACE`，默认 `PERSONAL`）、`scopeId`（默认 `""`）、`storageBackend`（默认 `local`）
  - 添加索引 `idx_drive_item_scope_deleted_parent_updated(scope_type, scope_id, deleted, parent_id, updated_at)`
  - 依赖 OpenJPA 自动建列（不写手写 SQL）
  - 不在本任务中触发编译/重启
  - _Requirements: 2.5, 2.6, 2.7, 2.8_
  - _Design: §"Scope 模型扩展（迭代一前置）", §"Data Models"_

- [x] 0.3 一次性回填 + 版本标记表
  - 新增 `cloudnext_meta` 表（单行 KV，主键 `key`，值 `value`、`updated_at`）
  - 实现 `OneTimeBackfillRunner`（`ApplicationRunner`）：当 `cloudnext_meta` 中无 `scope_backfill_v1` 标记时，执行 `UPDATE cloudnext_drive_item SET scope_id = owner_person_oa_id WHERE scope_type = 'PERSONAL' AND scope_id = ''`
  - 写入版本标记 `scope_backfill_v1=done` 防止重复执行
  - _Requirements: 2.6, 2.8_
  - _Design: §"Scope 模型扩展（迭代一前置）" 的迁移策略, §"Migration & Rollout"_

- [x] 0.4 抽取 DriveScopeAccessService（访问网关）
  - 新增 `DriveScopeAction` 枚举：`READ / WRITE / DELETE / MANAGE_SHARE / MANAGE_MEMBER`
  - 新增 `DriveScopeAccessService`，提供 `requireItem(session, itemId, action)` / `canActOn(session, item, action)` / `resolveWorkspaceRole(session, workspaceId)`
  - 实现设计文档中的 4 角色 × 5 动作权限矩阵；非成员一律拒绝；对 PERSONAL scope 复用 owner 判定与既有 `DriveShareService` 共享判定
  - _Requirements: 2.5, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7_
  - _Design: §"DriveScopeAccessService（新增，访问网关）"_

- [x] 0.5 既有调用切换到 DriveScopeAccessService
  - 把 `DriveService` / `DriveShareService` 现有 `requireOwnedItem(...)` 调用改为 `driveScopeAccessService.requireItem(...)`
  - 保持 PERSONAL 链路语义不变（既有个人文件用例必须仍然通过）
  - 不在本任务中执行 maven 验证；交给用户在阶段 1 集成测试时确认
  - _Requirements: 1.5, 1.6, 14.1_
  - _Design: §"DriveScopeAccessService（新增，访问网关）" 兼容策略_

- [x] 0.6 /api/status.pendingCapabilities 改为按 flag 派生
  - 修改 `/api/status` 实现：`pendingCapabilities` 列表根据 `cloudnext.iteration.*` flag 动态计算（未启用的能力名才进入列表）
  - 默认 flag 状态下保持 `public-links / search / governance / preview-health` 全部为 pending
  - _Requirements: 1.2, 1.3, 1.4_
  - _Design: §"Iteration Gate Strategy"_

- [x] 0.7 单元测试：DriveScopeAccessService 权限矩阵
  - 在 `cloudnext-server/src/test/java` 增加 `DriveScopeAccessServiceTest`，覆盖 4 角色 × 5 动作，以及非成员、scope 不一致两类反例
  - _Requirements: 4.2, 4.3, 4.4, 4.5, 4.6_
  - _Design: §"Testing Strategy" 单元测试矩阵_

- [ ]* 0.8 PBT 任务：Property 1 — Scope 完整性
  - 使用 fast-check 或 jqwik 等价物对 DriveItem 生命周期生成随机操作序列（create personal / create workspace item）
  - 验证：`scopeType ∈ {PERSONAL, WORKSPACE}` 且 `scopeId != ""`；`PERSONAL ⇒ scopeId == ownerPersonOaId`；`WORKSPACE ⇒ ∃ Workspace.id == scopeId`
  - **Property 1: Scope 完整性**
  - **Validates: Requirements 2.6, 2.7, 2.8**
  - _Design: §"Correctness Properties / Property 1"_

### 阶段 1：协作空间真实闭环（迭代一）

- [x] 1.1 Workspace / WorkspaceMember 实体与 Repository
  - 新增 `Workspace`（`cloudnext_workspace`，含 `idx_workspace_status_updated`）与 `WorkspaceMember`（`cloudnext_workspace_member`，含 `uk_ws_member`、`idx_ws_member_person`、`idx_ws_member_workspace_role`）
  - 新增 `WorkspaceRepository` / `WorkspaceMemberRepository`；后者提供 `findByWorkspaceId(...)`、`findByPersonOaId(...)`、`countByWorkspaceIdAndRole(...)`
  - _Requirements: 2.1, 2.2, 2.3_
  - _Design: §"迭代一：Workspace 闭环 / 实体"_

- [x] 1.2 DriveItemRepository 扩展 scope 查询
  - 增加 `findByScopeTypeAndScopeIdAndDeletedFalseAndParentId(...)` 用于工作区目录浏览
  - 增加 `findByScopeTypeAndScopeIdAndDeletedTrue(...)` 用于工作区回收站隔离
  - 保留既有 personal 查询方法不动
  - _Requirements: 3.5, 4.7, 4.8_
  - _Design: §"WorkspaceService" 目录浏览 / WorkspaceTrash 隔离_

- [x] 1.3 WorkspaceService — 创建空间（事务内 4 步）
  - 实现 `createWorkspace`：在同一 `@Transactional` 内 (a) 写 `Workspace` (b) 写 `WorkspaceMember(role=OWNER)` (c) 写 RootItem（`scopeType=WORKSPACE`、`parentId=""`、`itemType=FOLDER`、`name=""`、`storageKey=""`） (d) 回填 `Workspace.rootItemId`
  - 名称非法（空 / 超长）抛 `BAD_REQUEST`
  - _Requirements: 2.4, 2.7, 3.3_
  - _Design: §"WorkspaceService" 关键设计点_

- [x] 1.4 WorkspaceService — 列表 / 详情 / 目录浏览
  - 实现 `listMyWorkspaces`：通过 `WorkspaceMemberRepository` 取出当前 session 所有成员关系并组装 `WorkspaceSummaryResponse`（含 `myRole`、`memberCount`）
  - 实现 `getWorkspace`：返回 `WorkspaceDetailResponse`（含 `myRole`、`rootItemId`），非成员返回 403
  - 实现 `listItems(workspaceId, parentId)`：经 `DriveScopeAccessService.requireItem` 校验后调用扩展仓库方法
  - 调用方在任意 Workspace 中无成员关系时 `listMyWorkspaces` 返回空列表，而非 403
  - _Requirements: 3.1, 3.2, 3.4, 3.5, 3.12_
  - _Design: §"WorkspaceService" / §"Controller: CloudNextWorkspaceController"_

- [x] 1.5 DriveService 抽 scope-aware 内部方法 + WorkspaceService 文件 / 目录写入
  - 把 `DriveService.createFolder` / `uploadFile` 内部抽为 `createFolderInScope(session, scope, parentId, name)` / `uploadFileInScope(session, scope, parentId, file, conflictStrategy)`；现有 personal 入口改为薄包装（`scope = personal(session)`）
  - `WorkspaceService.createFolder` / `uploadFile` 委托到上述内部方法
  - 删除入 WorkspaceTrash：物理上仍是 `deleted=true` + `deletedAt`，通过 `scopeType=WORKSPACE` 过滤实现隔离
  - viewer 调用写入接口由 `DriveScopeAccessService` 拦截
  - _Requirements: 3.6, 3.7, 4.1, 4.2, 4.3, 4.7, 4.8_
  - _Design: §"WorkspaceService" 写入操作_

- [x] 1.6 WorkspaceService — 成员管理 + owner 守恒
  - 实现 `listMembers / addMember / updateMemberRole / removeMember`
  - admin 不能把成员设为 owner / 不能把 owner 删除（返回 400）
  - 任何会让 `count(role=OWNER) == 0` 的请求一律拒绝（返回 `workspace must keep at least one owner`）
  - 非 admin/owner 的成员管理调用返回 403
  - _Requirements: 3.8, 3.9, 3.10, 3.11, 4.4, 4.5, 4.6_
  - _Design: §"WorkspaceService" owner 守恒_

- [x] 1.7 CloudNextWorkspaceController（@ConditionalOnProperty）
  - 路径与方法对齐设计：`GET / POST /api/workspaces`、`GET /api/workspaces/{id}`、`GET /api/workspaces/{id}/items`、`POST /api/workspaces/{id}/folders`、`POST /api/workspaces/{id}/files`（multipart）、`GET / POST /api/workspaces/{id}/members`、`PATCH / DELETE /api/workspaces/{id}/members/{memberId}`
  - 用 `@ConditionalOnProperty(prefix="cloudnext.iteration.workspaces", name="enabled", havingValue="true", matchIfMissing=true)` 收口
  - 错误码与文案对齐 §"Error Handling"
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10, 3.11, 3.12, 3.13_
  - _Design: §"Controller: CloudNextWorkspaceController"_

- [x] 1.8 前端 cloudnext.js — Workspace API 客户端
  - 在 `cloudnext-web/src/api/cloudnext.js` 新增 `getWorkspaces / createWorkspace / getWorkspace / getWorkspaceItems / createWorkspaceFolder / uploadWorkspaceFile / listWorkspaceMembers / addWorkspaceMember / updateWorkspaceMember / removeWorkspaceMember`
  - 复用既有 `fetchJson` / `buildQuery` / multipart 上传辅助，不引入新的网络层
  - _Requirements: 5.1, 5.2_
  - _Design: §"前端落地" cloudnext.js 片段_

- [x] 1.9 前端 WorkspacesPage.vue 重构
  - 移除 `mocks/drive.js` 引用，改为 `getWorkspaces()` 真实数据源
  - 列表项点击跳转路由 `/workspaces/:id`
  - 加载、空态、错误态文案与 LinksPage 风格保持一致
  - _Requirements: 5.1_
  - _Design: §"前端落地" WorkspacesPage_

- [x] 1.10 前端 useDriveDirectoryState scope 参数
  - 修改 `composables/drive/useDriveDirectoryState.js` 接收 `scope` 参数；当 `scope.type === 'workspace'` 时调用 `getWorkspaceItems(scope.id, parentId)`，否则维持 `getMyDriveItems(parentId)`
  - 既有 MyFilesPage 调用保持不变（默认 personal scope）
  - _Requirements: 5.2, 5.3, 14.2_
  - _Design: §"前端落地" useDriveDirectoryState_

- [x] 1.11 前端 WorkspaceDetailPage.vue 新增
  - 新增页面 `cloudnext-web/src/pages/WorkspaceDetailPage.vue`（路由 `/workspaces/:id`，懒加载）
  - 顶部面包屑 `协作空间 / {workspace.name}`
  - 文件区域复用 `<DriveFileTable />`；新增 `scope`、`readonly`、`enable-share` 三个可选 prop
  - viewer 角色隐藏/禁用上传 / 新建文件夹 / 删除等写入入口
  - _Requirements: 5.2, 5.3, 5.5_
  - _Design: §"前端落地" WorkspaceDetailPage / DriveFileTable 扩展_

- [x] 1.12 前端 WorkspaceMembersDialog.vue 新增
  - 新弹窗组件，沿用现有 `dialog-shell` 样式
  - 内容：成员列表、角色变更（admin/owner 可见）、添加成员、移除成员
  - viewer/member 看不到写入控件
  - 错误处理走 `useToast()`，失败保留旧数据
  - _Requirements: 5.4, 5.6_
  - _Design: §"前端落地" 成员管理入口_

- [x] 1.13 路由与导航接入
  - 在 `cloudnext-web/src/router/index.js` 注册 `/workspaces/:id` 懒加载路由
  - WorkspacesPage 列表行 click → `router.push({ name: 'WorkspaceDetail', params: { id } })`
  - _Requirements: 5.1, 5.2_
  - _Design: §"前端落地" 路由变更_

- [x] 1.14 集成测试：Workspace 全链路
  - 在 `cloudnext-server/src/test/java` 增加 `@SpringBootTest`：HTTP 创建空间 → 上传 → 列表 → 删除 → 验证 trash 出现在 workspace 而非个人 trash
  - 增加非成员访问被拒绝、admin 不能改 owner、最后一个 owner 不可删/不可降级 三个场景
  - _Requirements: 3.1, 3.6, 3.7, 4.5, 4.7, 4.8_
  - _Design: §"Testing Strategy" Integration 测试_

- [ ]* 1.15 PBT 任务：Property 2 — Workspace owner 守恒
  - 随机生成成员管理事件序列（add/update/remove），并发执行
  - 验证：每次事务结束后 `count(WorkspaceMember{w, role=OWNER}) >= 1`；任何会让该计数变为 0 的请求被拒绝且不修改数据
  - **Property 2: Workspace owner 守恒**
  - **Validates: Requirements 4.5**
  - _Design: §"Correctness Properties / Property 2"_

- [ ]* 1.16 PBT 任务：Property 3 — WorkspaceTrash 隔离
  - 随机生成跨 PERSONAL / WORKSPACE 的 delete 操作
  - 验证：个人回收站查询绝不返回 `scopeType=WORKSPACE`；WorkspaceTrash 查询绝不返回 `scopeType=PERSONAL`
  - **Property 3: WorkspaceTrash 隔离**
  - **Validates: Requirements 4.7, 4.8**
  - _Design: §"Correctness Properties / Property 3"_

- [x] 1.17 前端单元测试 — WorkspaceDetailPage / 成员弹窗
  - 在 `cloudnext-web/src/__tests__` 增加 `WorkspaceDetailPage` 渲染（viewer 隐藏写入入口）、`WorkspaceMembersDialog` 角色控制、`useDriveDirectoryState scope=workspace` 三个用例
  - _Requirements: 5.5, 5.6_
  - _Design: §"Testing Strategy" 端到端回归_

- [x] 1.18 主链回归 checkpoint（迭代一）
  - 显式列出需要手测的 P0 项：个人文件 CRUD、共享文件、回收站、最近打开、OnlyOffice 预览
  - 不自动跑 maven / 不自动重启 OnlyOffice；由用户依据 `回归与验收 / 迭代一` 检查清单逐项确认
  - _Requirements: 1.5, 1.6, 14.1, 14.2_
  - _Design: §"Migration & Rollout" 迭代一上线前_

### 阶段 2：外链管理真实闭环（迭代二）

> 准入条件：阶段 1 检查清单全部勾选；`cloudnext.iteration.workspaces.enabled=true`；本阶段所有新接口默认通过 `cloudnext.iteration.public-links.enabled=false` 关闭。

- [x] 2.1 DrivePublicLink 实体与 Repository
  - 新增 `DrivePublicLink`（`cloudnext_drive_public_link`，含 `uk_drive_public_link_token`、`idx_dpl_creator_revoked_created`、`idx_dpl_item`）
  - `DrivePublicLinkRepository` 提供 `findByToken / findByCreatorPersonOaIdAndRevokedFalseOrderByCreatedAtDesc`
  - 新增 JPQL 原子限额方法 `incrementDownloadCountIfWithinLimit(id, now)`：`WHERE l.revoked=false AND (l.downloadLimit < 0 OR l.downloadCount < l.downloadLimit)`
  - _Requirements: 6.1, 6.2_
  - _Design: §"迭代二：Public Link 闭环 / 实体"_

- [x] 2.2 BCrypt 密码 + token 生成器
  - 引入 `spring-security-crypto`（仅为 BCryptPasswordEncoder），如已存在则复用
  - 新增 `PublicLinkTokenGenerator`：`SecureRandom` 24 字节 + base64url；冲突重试 1 次
  - "无密码"语义为 `passwordHash == ""`；不存明文
  - _Requirements: 6.4, 6.5_
  - _Design: §"DrivePublicLink" 实体说明_

- [x] 2.3 accessTicket 签发与校验
  - 新增 `PublicLinkSessionService`：HMAC-SHA256 签发 `{ token, exp, iat }`，密钥取 `cloudnext.public-link.session-secret`（留空启动时随机生成并打 INFO 日志）
  - TTL 由 `cloudnext.public-link.session-ttl-seconds` 控制，默认 1800
  - 提供 `issue(token)` / `verify(token, accessTicket)`
  - _Requirements: 8.3_
  - _Design: §"DrivePublicLinkService" accessTicket 设计点_

- [x] 2.4 DrivePublicLinkService — 管理端
  - 实现 `create / listMine / update / revoke`
  - `create` 通过 `DriveScopeAccessService.canActOn(session, item, MANAGE_SHARE)` 校验：PERSONAL 需 owner；WORKSPACE 需 member/admin/owner（viewer 不行）
  - `update` / `revoke` 限创建者本人，其他人返回 403
  - _Requirements: 6.1, 7.1, 7.2, 7.3, 7.4, 7.5, 7.6_
  - _Design: §"DrivePublicLinkService" 管理端方法_

- [x] 2.5 DrivePublicLinkService — 公开端 + assertOpen 失效顺序
  - 实现 `getMeta / verify / preview / download`
  - 抽取 `assertOpen(token)`：按 `revoked → expired → permission/limit` 严格顺序判定，先返回最严重错误
  - `download` 走 `incrementDownloadCountIfWithinLimit(...)`：返回 0 行直接拒绝，**不开启文件流**
  - 元信息脱敏：`getMeta` 仅返回 `fileName, fileType, requirePassword, permission, isOpen`
  - _Requirements: 8.1, 8.2, 8.5, 8.6, 8.7, 8.8, 8.9, 8.10_
  - _Design: §"DrivePublicLinkService" 公开端 / §"Error Handling"_

- [x] 2.6 DriveService — buildPreviewForItem 提取 + AccessContext
  - 把 `DriveService.getPreview` 中按 item 出 `PreviewResponse` 的逻辑提取为 `buildPreviewForItem(DriveItem, AccessContext)`
  - `AccessContext` 表达"匿名 + 仅预览/仅下载 + token 标识"，给审计层提供主体
  - 现有 personal 预览路径仍走 `AccessContext.user(session)` 包装，行为不变
  - 不重新做 office 渲染流程；OnlyOffice 配置入口不变
  - _Requirements: 1.9, 8.5_
  - _Design: §"DrivePublicLinkService" 预览复用_

- [x] 2.7 公开端限速桶
  - 新增 `PublicLinkRateLimiter`：内存级 `LoadingCache<String, RateBucket>`，单 token + IP 维度，10 次/分钟
  - 仅作用于 `POST /public/links/{token}/verify`
  - 超出返回 429 `link verify rate limited`
  - _Requirements: 8.4_
  - _Design: §"Error Handling" 外链密码暴力破解保护_

- [x] 2.8 CloudNextLinkController（管理端）
  - 路径：`POST /api/links`、`GET /api/links`、`PATCH /api/links/{id}`、`POST /api/links/{id}/revoke`
  - `@ConditionalOnProperty(prefix="cloudnext.iteration.public-links", name="enabled")`，默认关闭
  - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6_
  - _Design: §"Controllers / CloudNextLinkController"_

- [x] 2.9 CloudNextPublicLinkController（公开端 + 拦截器排除）
  - 路径：`GET /public/links/{token}/meta`、`POST /public/links/{token}/verify`、`GET /public/links/{token}/preview`、`GET /public/links/{token}/download`
  - 在 `WebMvcConfig` 中把 `/public/links/**` 从 `AuthSessionInterceptor` 中显式排除（不走登录）
  - `@ConditionalOnProperty(prefix="cloudnext.iteration.public-links", name="enabled")`，默认关闭
  - 公开端不返回 401（避免暴露登录语义）；按 §"Error Handling" 表返回 410 / 403 / 429
  - _Requirements: 1.7, 8.1, 8.2, 8.3, 8.5, 8.6, 8.7, 8.8, 8.9, 8.10_
  - _Design: §"Controllers / CloudNextPublicLinkController"_

- [x] 2.10 前端 cloudnext.js — Link API 客户端
  - 新增 `listMyLinks / createLink / updateLink / revokeLink`
  - 公开端 `getPublicLinkMeta / verifyPublicLink / publicLinkPreviewUrl / publicLinkDownloadUrl`（通过 `?accessTicket=...` 或 `X-CloudNext-Public-Ticket` 头携带）
  - _Requirements: 9.1, 9.4_
  - _Design: §"前端 LinksPage.vue" / §"DrivePublicLinkService" accessTicket_

- [x] 2.11 前端 LinksPage.vue 重构（列表 + 创建 + 操作）
  - 移除占位内容；进入页或主动刷新时调用 `listMyLinks`
  - 列表展示：外链地址、关联文件名、创建时间、有效期、是否设密码、`downloadCount/downloadLimit`、当前 `permission`、失效状态
  - 加载状态、空状态、错误态分别处理；错误时保留旧数据不动并通过 `useToast()` 显示原因
  - 顶部"创建外链（从文件选择）"入口
  - _Requirements: 9.1, 9.2, 9.7_
  - _Design: §"前端 LinksPage.vue"_

- [x] 2.12 前端 LinksPage — 行内操作（复制 / 修改 / 失效）
  - 复制 URL：`navigator.clipboard.writeText`，失败回退 `<input>` + `execCommand`，并显式提示成功/失败
  - 修改有效期 / 密码 / 下载次数 / 权限：弹窗提交 `updateLink`，成功后用响应值刷新该行
  - 失效：弹二次确认，确认后调用 `revokeLink`；成功后该行所有操作按钮变灰并显示"已失效"chip
  - 已失效条目隐藏复制 / 修改入口
  - _Requirements: 9.3, 9.4, 9.5, 9.6, 9.7_
  - _Design: §"前端 LinksPage.vue" 操作单元_

- [x] 2.13 集成测试：外链全链路
  - `@SpringBootTest`：管理端 create → 公开端 meta → verify → preview → download；revoke 后所有公开端返回 410
  - 超限拒绝（`incrementDownloadCountIfWithinLimit` 返回 0）；preview-only 链接 download 返回 403
  - 非创建者修改/吊销返回 403
  - _Requirements: 7.6, 8.6, 8.7, 8.9, 8.10_
  - _Design: §"Testing Strategy" Integration 测试_

- [ ]* 2.14 PBT 任务：Property 4 — 外链下载限额单调与原子
  - 多线程模拟 N 个并发 `download`，验证只有 `min(N, downloadLimit - downloadCount)` 个请求得到下载流
  - 验证 `downloadCount` 单调非递减；任何"未签发下载流"的拒绝路径都不增加 `downloadCount`
  - 在真实 H2 上跑 `incrementDownloadCountIfWithinLimit`
  - **Property 4: 外链下载限额单调与原子**
  - **Validates: Requirements 8.6, 8.9**
  - _Design: §"Correctness Properties / Property 4"_

- [ ]* 2.15 PBT 任务：Property 5 — 外链失效顺序
  - 随机构造 `(revoked, expired, permission, downloadLimit, downloadCount)` 状态组合，对每种状态发起 meta/verify/preview/download
  - 验证拒绝判定按 `revoked → expired → permission → limit` 严格顺序，文案与设计一致
  - **Property 5: 外链失效顺序**
  - **Validates: Requirements 8.7, 8.8, 8.10**
  - _Design: §"Correctness Properties / Property 5"_

- [x] 2.16 前端单元测试 — LinksPage 交互
  - `LinksPage` 加载/空态/错误态分支；复制成功/失败提示；二次确认行为；修改后字段以服务端返回值刷新；错误时不回显成功
  - _Requirements: 9.2, 9.4, 9.5, 9.6, 9.7_
  - _Design: §"Testing Strategy" 端到端回归_

- [x] 2.17 主链回归 checkpoint（迭代二）
  - 由用户按 `回归与验收 / 迭代二` 清单逐项确认
  - 不自动跑 maven、不自动重启 OnlyOffice
  - _Requirements: 1.5, 1.6, 14.1, 14.2_
  - _Design: §"Migration & Rollout" 迭代二上线前_

### 阶段 3：搜索 / 治理 / 存储平台化（迭代三）

> 准入条件：阶段 2 检查清单全部勾选；本阶段所有新接口默认通过 `cloudnext.iteration.search.enabled / governance.enabled / preview-health.enabled / storage-gateway.objectStorageEnabled` 关闭。

- [x] 3.1 DriveSearchService — 跨 scope 搜索
  - 新增 `DriveSearchService` 与 `DriveSearchRequest`：`scope, keyword, itemType, owner, updatedFrom, updatedTo, page, size`
  - personal：`scope_type='PERSONAL' AND scope_id=:session.personOaId AND deleted=false AND name LIKE :kw`
  - workspace：先经 `WorkspaceMemberRepository.findByPersonOaId` 拿到 workspaceId 列表，再 `scope_type='WORKSPACE' AND scope_id IN (:ids) AND deleted=false AND name LIKE :kw`
  - shared：经现有 `DriveShareRepository` 拿到分享给我的 itemId 集合，再 `id IN (:ids) AND deleted=false AND name LIKE :kw`
  - 命中行经 `DriveScopeAccessService.canActOn(session, item, READ)` 兜底过滤
  - keyword 长度 ≤100、去前后空白；为空返回 400
  - 分页：`page` 从 1 起，默认 1；`size` 默认 20、上限 100；返回 `{ items, page, size, total }`
  - 已完成：后端单测覆盖 personal / workspace / shared 三类 scope，关键参数校验与权限兜底已通过回归
  - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_
  - _Design: §"4.1 DriveSearchService"_

- [x] 3.2 CloudNextSearchController
  - 路径：`GET /api/search/files`
  - `@ConditionalOnProperty(prefix="cloudnext.iteration.search", name="enabled")`，默认关闭
  - 已完成：开关关闭时返回 404，开关开启后接口可达；前端 `searchFiles()` 契约已补齐并通过测试
  - _Requirements: 10.1, 10.3, 10.5_
  - _Design: §"Architecture / Controllers"_

- [~] 3.3 QuotaSubject 实体 + Repository
  - 新增 `QuotaSubject`（`cloudnext_quota_subject`，唯一 `(subject_type, subject_id)`），字段 `subjectType, subjectId, activeBytes, trashBytes, updatedAt`
  - 新增 `QuotaSubjectRepository.findBySubjectTypeAndSubjectId(...)`
  - _Requirements: 11.1, 11.2_
  - _Design: §"4.2 Quota / Audit / Retention" Quota_

- [~] 3.4 QuotaService — 4 类事件回调
  - 新增 `QuotaService.applyDelta(scope, deltaBytes, area)`，`area ∈ {ACTIVE, TRASH}`
  - 仅在以下事件成功后调用：`upload`（active +=）、`deleteToTrash`（active -= / trash +=）、`restore`（active += / trash -=）、`purge`（trash -=）
  - rename / move 同 scope / preview / download / share / link 等不调用
  - 失败回滚不更新（依赖 `@Transactional`）
  - _Requirements: 11.3, 11.4_
  - _Design: §"Quota" 触发集合_

- [~] 3.5 接入 Quota 到 DriveService / WorkspaceService
  - 在 `DriveService.uploadFile / deleteToTrash / restoreFromTrash / purgeFromTrash` 持久化成功后调用 `quotaService.applyDelta(...)`
  - 工作区路径同上（通过 1.5 抽出的 scope-aware 内部方法天然继承）
  - _Requirements: 11.3, 11.4_
  - _Design: §"Quota" 触发集合_

- [~] 3.6 CloudNextQuotaController
  - 路径：`GET /api/quotas/me`、`GET /api/quotas/workspaces/{id}`
  - workspace 查询经 `DriveScopeAccessService` 校验调用方为成员
  - `@ConditionalOnProperty(prefix="cloudnext.iteration.governance", name="enabled")`
  - _Requirements: 11.1, 11.2_
  - _Design: §"Architecture / Controllers"_

- [~] 3.7 回收站保留策略拆桶
  - 修改 `DriveTrashCleanupService`：cron 仍由 `cloudnext.drive.trash-cleanup-cron` 控制（默认 03:00）
  - 个人 trash 用 `personal-trash-retention-days`、workspace trash 用 `workspace-trash-retention-days`；缺失时回退 `cloudnext.drive.trash-retention-days`
  - 超期条目走既有 purge 流程；purge 触发 `QuotaService.applyDelta(... TRASH, -)`
  - _Requirements: 11.5_
  - _Design: §"Retention"_

- [~] 3.8 AuditLog 实体 + Repository + 异步线程池
  - 新增 `AuditLog`（`cloudnext_audit_log`，含 `idx_audit_subject_time / idx_audit_action_time / idx_audit_target_time`）
  - 新增 `auditExecutor` Bean：核心 2、最大 4、队列 256，溢出降级为同步写并打 WARN
  - `AuditLogService.record(...)` 使用 `@Async("auditExecutor")`
  - _Requirements: 11.6, 11.7, 11.8_
  - _Design: §"Audit"_

- [~] 3.9 6 类动作埋点
  - 在以下位置埋点：`DriveService.downloadItem`（FILE_DOWNLOAD）、`DriveService.getPreview`（FILE_PREVIEW）、`DriveShareService.createShares`（SHARE_GRANT）、`DriveShareService.revokeShare`（SHARE_REVOKE）、`DrivePublicLinkService.create`（LINK_CREATE）、`DrivePublicLinkService.revoke`（LINK_REVOKE）、`DrivePublicLinkService.download`（LINK_PUBLIC_DOWNLOAD）
  - 公开端动作主体写为 `subjectType=PUBLIC_LINK, subjectId=token`，其他动作写为 `USER, userId`
  - _Requirements: 11.6, 11.7, 11.8_
  - _Design: §"Audit" 埋点位置_

- [~] 3.10 CloudNextAuditController
  - 路径：`GET /api/admin/audit-logs?action=&subjectId=&from=&to=&page=&size=`
  - 限管理员可见（沿用现有管理员鉴权）
  - `@ConditionalOnProperty(prefix="cloudnext.iteration.governance", name="enabled")`
  - _Requirements: 11.6, 11.7_
  - _Design: §"Architecture / Controllers"_

- [~] 3.11 StorageGateway 抽象 + StorageObject + LocalStorageBackend
  - 把现有 `DriveStorageService` 接口改名/抽为 `StorageGateway`，方法 `write(storageKey, MultipartFile) / write(storageKey, InputStream, sizeBytes, contentType) / read(StorageObject) / delete(StorageObject) / exists(StorageObject)`
  - `StorageObject{ backend, storageKey, sizeBytes, contentType }`
  - `LocalStorageBackend` 保留现 `LocalDriveStorageService` 行为；root 目录读 `cloudnext.storage.local.root`，缺省值与现状一致（`cloudnext-server/.data/drive-storage`）
  - DriveItem 新增列 `storage_backend`（已在 0.2 完成），其他元信息复用既有 `storage_key / size_bytes / content_type`
  - _Requirements: 12.1, 12.2, 12.6, 12.8_
  - _Design: §"4.3 StorageGateway"_

- [~] 3.12 S3CompatibleStorageBackend
  - 基于 AWS SDK v2 `S3Client`（兼容 S3 / MinIO / OSS）
  - 配置：`cloudnext.storage.s3.{endpoint, region, bucket, accessKey, secretKey, pathStyleAccess}`
  - `storageKey` 即 S3 Object Key
  - `StorageGatewayConfiguration` 通过 `cloudnext.storage.backend` 装配（`local` 默认 / `s3` 由 flag 开启）；本地实现恒被保留为可选
  - 未启用 S3 时不引入 S3Client Bean，避免空配置启动报错
  - _Requirements: 12.3, 12.4, 12.5_
  - _Design: §"4.3 StorageGateway" 实现 / §"Iteration Gate Strategy" storage-gateway.objectStorageEnabled_

- [~] 3.13 写入回滚事务同步
  - 修改 `DriveService.uploadFile`：先 `gateway.write(...)` 得到 StorageObject → 同事务内 `driveItemRepository.save(item)`
  - 注册 `TransactionSynchronization.afterCompletion`：若 `STATUS_ROLLED_BACK`，调用 `gateway.delete(storageObject)` 清理
  - replace 策略下旧 storageObject 删除放进 `afterCommit` 钩子
  - 错误码：存储回滚后返回 500 `storage rolled back`
  - _Requirements: 12.7_
  - _Design: §"4.3 StorageGateway" 写入回滚_

- [~] 3.14 PreviewHealthService — 双指标
  - 新增 `PreviewHealthRecorder`：在 `DriveService.getPreview` 入/出口埋点（成功 / 失败计数器，滑动窗口 5 分钟、1 分钟）
  - 新增 `OnlyofficeProbeJob`：`@Scheduled(fixedDelay=30000)` `HEAD/GET cloudnext.drive.onlyoffice.documentServerUrl + healthcheck`，连续 3 次失败置 `documentServerUp=false`
  - 两侧指标独立维护：`previewServiceUp` 不因 `documentServerUp=false` 被强制置 false
  - 数据存内存（`AtomicReference`），不持久化
  - _Requirements: 13.1, 13.2, 13.3, 13.4_
  - _Design: §"4.4 PreviewHealthService"_

- [~] 3.15 CloudNextPreviewHealthController
  - 路径：`GET /api/admin/preview-health`，返回 `previewServiceUp / documentServerUp / previewRequestsLastMinute / previewFailuresLastMinute / lastDocumentServerError`
  - `@ConditionalOnProperty(prefix="cloudnext.iteration.preview-health", name="enabled")`
  - _Requirements: 13.2, 13.4_
  - _Design: §"4.4 PreviewHealthService" 输出_

- [~] 3.16 前端 cloudnext.js — Search / Quota / Audit / Health API
  - 新增 `searchFiles(req)`、`getMyQuota()`、`getWorkspaceQuota(id)`、`listAuditLogs(query)`、`getPreviewHealth()`
  - _Requirements: 10.1, 11.1, 11.2, 11.6, 13.2_
  - _Design: §"Architecture / Controllers"_

- [~] 3.17 前端 GlobalSearchBar
  - 新增组件 `cloudnext-web/src/components/drive/GlobalSearchBar.vue`，挂载到顶部栏
  - 提供 `scope`（personal/shared/workspace）切换、`keyword` 输入、`itemType / updatedFrom / updatedTo` 高级筛选
  - 提交后调用 `searchFiles`，结果落到搜索结果页（可放在新路由或抽屉，最低实现复用 `DriveFileTable`）
  - _Requirements: 10.1, 10.2, 10.3, 10.5_
  - _Design: §"High-level Component Map" GlobalSearchBar_

- [~] 3.18 前端 AdminPage 接入 Quota / Audit / PreviewHealth 面板
  - 在 `AdminPage.vue` 新增三块只读面板：我的容量 + 我能管理的 Workspace 容量、最近审计日志列表（带筛选）、预览健康指标
  - 仅在对应 flag 启用时展示对应面板（页面初始化时根据 `/api/status.pendingCapabilities` 决定是否渲染）
  - _Requirements: 11.1, 11.2, 11.6, 13.2, 13.4_
  - _Design: §"4.2 Quota / Audit / Retention" / §"4.4 PreviewHealthService"_

- [~] 3.19 集成测试：搜索 / 配额 / 审计 / 存储 / 预览健康
  - 搜索：personal / workspace / shared 三 scope 下命中关键字；非成员搜索 workspace 返回 0 条
  - 配额：上传 1MB → 删除 → 还原 → purge，活动桶与回收桶迁移正确；rename/move 不影响
  - 审计：6 类动作各落一条；公开下载主体为 token
  - StorageGateway：在 `local` 与 `s3` 之间通过单一配置切换；切到 s3 后回归上传/下载/预览/删除链路（仅在测试 profile 中开启 S3，使用 LocalStack 或 MinIO testcontainers，由用户在本地启动）
  - PreviewHealth：模拟 OnlyOffice 不可达 → `documentServerUp=false`；预览成功率 95% 临界
  - _Requirements: 10.4, 11.3, 11.4, 11.6, 12.5, 12.7, 13.3_
  - _Design: §"Testing Strategy" Integration 测试_

- [ ]* 3.20 PBT 任务：Property 6 — 容量统计触发集合
  - 随机生成 DriveItem 操作序列（含 rename / move / preview / share / link 等不应触发 quota 的事件）
  - 验证：仅 4 类成功事件触发 `applyDelta`；其余事件不调用
  - **Property 6: 容量统计触发集合**
  - **Validates: Requirements 11.3, 11.4**
  - _Design: §"Correctness Properties / Property 6"_

- [ ]* 3.21 PBT 任务：Property 7 — 写入与元信息原子
  - 随机注入 `driveItemRepository.save` 抛异常 / gateway.write 抛异常
  - 验证：事务结束时三种状态二选一（成功 = item ∧ exists 都为真；失败 = 都为假），不存在悬挂状态
  - **Property 7: 写入与元信息原子**
  - **Validates: Requirements 12.7**
  - _Design: §"Correctness Properties / Property 7"_

- [ ]* 3.22 PBT 任务：Property 8 — 审计主体不冒名
  - 随机生成 `LINK_PUBLIC_DOWNLOAD` 与 `FILE_DOWNLOAD` 事件
  - 验证：公开端审计 `subjectType=PUBLIC_LINK ∧ subjectId=token`；任何把外部访问者写为内部 user id 的尝试在 `AuditLogService` 内被拒绝
  - **Property 8: 审计主体不冒名**
  - **Validates: Requirements 11.7, 11.8**
  - _Design: §"Correctness Properties / Property 8"_

- [ ]* 3.23 PBT 任务：Property 9 — 迭代门禁
  - 在两套 profile 下分别校验：`public-links.enabled=false` 时 `/api/links*` 与 `/public/links/*` 全部 404；`search.enabled=false` 时 `/api/search/files` 返回 404
  - 不存在"flag 关闭但接口仍可访问"的状态；通过 `@SpringBootTest` 启动两次容器对比
  - **Property 9: 迭代门禁**
  - **Validates: Requirements 1.2, 1.3, 1.4_**
  - _Design: §"Correctness Properties / Property 9"_

- [~] 3.24 主链回归 checkpoint（迭代三）
  - 由用户按 `回归与验收 / 迭代三` 清单逐项确认
  - 不自动跑 maven、不自动重启 OnlyOffice
  - _Requirements: 1.5, 1.6, 14.1, 14.2_
  - _Design: §"Migration & Rollout" 迭代三上线前_

### 回归与验收

> 跨切关卡：每一轮迭代上线前由用户逐项勾选；任何一项未勾选不开启对应 flag、不暴露下一轮接口。

- [~] 4.1 迭代一上线前检查清单
  - 确认 `scope_type` / `scope_id` 一次性回填脚本跑成功，`cloudnext_meta.scope_backfill_v1=done`
  - 既有个人文件用例 100% 通过（个人文件 CRUD、共享文件、回收站、最近打开、OnlyOffice 预览）
  - Workspace 创建 / 成员 / 上传 / 删除 / 还原全部跑通
  - WorkspacesPage / WorkspaceDetailPage / 成员弹窗在 Chrome / Edge 手测通过
  - `cloudnext.iteration.public-links.enabled=false`、`search.enabled=false`，确认 `/api/links*`、`/public/links/*`、`/api/search/*` 全部 404
  - _Requirements: 1.2, 1.5, 1.6, 14.1, 14.2_
  - _Design: §"Migration & Rollout" 迭代一上线前_

- [~] 4.2 迭代二上线前检查清单
  - `cloudnext_drive_public_link` 表自动建表成功，token 唯一索引存在
  - `cloudnext.public-link.session-secret` 显式配置（不依赖启动时随机）
  - LinksPage 全部交互（复制 / 修改 / 失效 / 二次确认 / 错误回显）通过
  - 公开端 4 个接口在未登录状态下可访问；登录态接口仍受 token 校验
  - 把 `iteration.public-links.enabled=true`，`/api/status.pendingCapabilities` 中移除 `public-links`
  - 主链回归（与 4.1 同清单）仍然通过
  - _Requirements: 1.3, 1.4, 1.5, 1.6, 14.1, 14.2_
  - _Design: §"Migration & Rollout" 迭代二上线前_

- [~] 4.3 迭代三上线前检查清单
  - `cloudnext_quota_subject`、`cloudnext_audit_log` 表与索引就绪
  - 容量统计在 4 类事件下都能更新；其他事件不变
  - 审计日志覆盖 6 类动作；外链动作主体记录为 token
  - `StorageGateway` 在 `local` 与 `s3` 之间通过单一配置切换；切到 s3 后回归上传 / 下载 / 预览 / 删除链路
  - `cloudnext.iteration.search.enabled=true`、`governance.enabled=true`、`preview-health.enabled=true`，并视情况开 `storage-gateway.objectStorageEnabled`
  - 主链回归（与 4.1 同清单）仍然通过
  - _Requirements: 1.4, 1.5, 1.6, 14.1, 14.2, 14.3, 14.4, 14.5_
  - _Design: §"Migration & Rollout" 迭代三上线前_

## Notes

- 标记 `*` 的任务为可选项（property-based test），可在主任务通过后增量补齐；其余任务为发布前必做项。
- 所有需要 maven 构建、OnlyOffice 重启、对象存储部署的步骤由用户显式发起，本任务清单不自动触发（遵循 `AGENTS.md`）。
- 迭代二、三的接口默认通过 `@ConditionalOnProperty` 关闭；只有上一轮 `回归与验收` 全部勾选后才开对应 flag。
- 跨切任务（4.1-4.3）必须排在对应迭代所有依赖任务之后；orchestrator 应等待依赖完成后再触发。
- 所有迁移依赖 OpenJPA 自动建表 + `OneTimeBackfillRunner`，与现有"重启触发自动建表"策略一致，不引入手写 SQL。

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["0.1", "0.2"] },
    { "id": 1, "tasks": ["0.3", "0.4", "0.6"] },
    { "id": 2, "tasks": ["0.5", "0.7", "0.8"] },
    { "id": 3, "tasks": ["1.1", "1.2"] },
    { "id": 4, "tasks": ["1.3", "1.4", "1.5", "1.6"] },
    { "id": 5, "tasks": ["1.7", "1.8"] },
    { "id": 6, "tasks": ["1.9", "1.10", "1.11", "1.12", "1.13"] },
    { "id": 7, "tasks": ["1.14", "1.15", "1.16", "1.17"] },
    { "id": 8, "tasks": ["1.18"] },
    { "id": 9, "tasks": ["2.1", "2.2", "2.3"] },
    { "id": 10, "tasks": ["2.4", "2.5", "2.6", "2.7"] },
    { "id": 11, "tasks": ["2.8", "2.9", "2.10"] },
    { "id": 12, "tasks": ["2.11", "2.12"] },
    { "id": 13, "tasks": ["2.13", "2.14", "2.15", "2.16"] },
    { "id": 14, "tasks": ["2.17"] },
    { "id": 15, "tasks": ["3.1", "3.3", "3.8", "3.11"] },
    { "id": 16, "tasks": ["3.2", "3.4", "3.9", "3.12", "3.13", "3.14"] },
    { "id": 17, "tasks": ["3.5", "3.6", "3.7", "3.10", "3.15", "3.16"] },
    { "id": 18, "tasks": ["3.17", "3.18"] },
    { "id": 19, "tasks": ["3.19", "3.20", "3.21", "3.22", "3.23"] },
    { "id": 20, "tasks": ["3.24"] },
    { "id": 21, "tasks": ["4.1"] },
    { "id": 22, "tasks": ["4.2"] },
    { "id": 23, "tasks": ["4.3"] }
  ]
}
```
