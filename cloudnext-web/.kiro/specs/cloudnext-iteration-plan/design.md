# Design Document

## Overview

本设计承接 `requirements.md` 三轮迭代主线（协作空间 → 外链管理 → 搜索/治理/存储平台化），在不破坏现有 `cloudnext-server`（Spring Boot + JPA）和 `cloudnext-web`（Vue 3）主链能力的前提下，定义最小可上线方案。

设计原则：

1. **复用而非新建**：DriveItem 仍是唯一的文件/目录实体；新增 `scopeType` / `scopeId` 字段把它扩展到 `personal` 与 `workspace` 两个 Scope，预览、下载、回收站、审计、配额都共用同一条物理链路。
2. **Scope 是访问网关唯一收口**：所有读写鉴权走 `DriveAccessService` + 新增 `DriveScopeAccessService`，禁止任何业务在 Service 层直接读 `ownerPersonOaId == 当前人`。
3. **三轮迭代严格门禁**：迭代二/迭代三的接口默认通过 Spring `@ConditionalOnProperty` 收住；只有上一轮验收完成、配置开启对应 feature flag 后才挂载到容器。`/api/status` 中 `pendingCapabilities` 改为根据 flag 动态计算。
4. **OA 边界不破坏**：登录与组织源仍走 `AuthSessionService` + `OrgMirrorSyncService`，本设计不引入对 OA 在线接口的实时调用；OnlyOffice 配置仍仅通过 `cloudnext.drive.onlyoffice.*` 一处管理。

## Architecture

### High-level Component Map

```
┌──────────────────────────────────────────────────────────────────────────┐
│                     cloudnext-web (Vue 3 Pages)                          │
│  WorkspacesPage · WorkspaceDetailPage · LinksPage · GlobalSearchBar      │
│  PublicLinkPage (未登录)                                                  │
└────────────────┬─────────────────────────────────────────────────────────┘
                 │ X-CloudNext-Token (登录) / token query (公开)
┌────────────────▼─────────────────────────────────────────────────────────┐
│                     cloudnext-server (Spring Boot)                       │
│                                                                          │
│  Controllers (Conditional)                                               │
│  ├── 迭代一: CloudNextWorkspaceController (/api/workspaces*)             │
│  ├── 迭代二: CloudNextLinkController (/api/links*)                       │
│  ├── 迭代二: CloudNextPublicLinkController (/public/links*)              │
│  ├── 迭代三: CloudNextSearchController (/api/search/files)               │
│  ├── 迭代三: CloudNextQuotaController (/api/quotas/*)                    │
│  ├── 迭代三: CloudNextAuditController (/api/admin/audit-logs)            │
│  └── 迭代三: CloudNextPreviewHealthController (/api/admin/preview-health)│
│                                                                          │
│  Domain Services                                                         │
│  ├── DriveScopeAccessService    (Scope 鉴权统一入口)                      │
│  ├── WorkspaceService           (Workspace + WorkspaceMember)            │
│  ├── DrivePublicLinkService     (DrivePublicLink + 限额)                 │
│  ├── DriveSearchService         (跨 Scope 文件名/元数据搜索)              │
│  ├── QuotaService               (用户/Workspace 容量统计)                 │
│  └── AuditLogService            (审计日志写入 + 查询)                     │
│                                                                          │
│  Cross-cutting                                                           │
│  ├── StorageGateway             (interface, 替换 DriveStorageService)    │
│  │   ├── LocalStorageBackend                                             │
│  │   └── S3CompatibleStorageBackend (S3 / MinIO / OSS 任选其一)          │
│  └── PreviewHealthService       (OnlyOffice 探测 + 失败计数)              │
│                                                                          │
│  Existing (复用, 仅扩展)                                                  │
│  ├── DriveService / DriveReadService / DriveShareService                 │
│  ├── DriveAccessService                                                  │
│  └── AuthSessionService / OrgMirrorSyncService                           │
└──────────────────────────────────────────────────────────────────────────┘
```

### Iteration Gate Strategy

每一轮迭代的新接口都通过 Spring `@ConditionalOnProperty` 与 feature flag 控制，配置文件统一收口在 `cloudnext.iteration.*`：

| Flag | 默认 | 控制范围 |
|------|------|---------|
| `cloudnext.iteration.workspaces.enabled` | `true`（迭代一交付后） | `WorkspaceController` + 相关 Service Bean |
| `cloudnext.iteration.public-links.enabled` | `false` | `LinkController` / `PublicLinkController` / 公开端点 Filter |
| `cloudnext.iteration.search.enabled` | `false` | `SearchController` |
| `cloudnext.iteration.governance.enabled` | `false` | Quota/Audit/Retention 接口 |
| `cloudnext.iteration.storage-gateway.objectStorageEnabled` | `false` | S3 兼容 backend |
| `cloudnext.iteration.preview-health.enabled` | `false` | Preview Health 接口 |

`/api/status.pendingCapabilities` 改成根据这些 flag 计算：未开启的能力名才进列表。这套机制让 Requirement 1.2/1.3「未通过验收前不暴露下一轮接口」从代码层面落地。

## Components and Interfaces

### 1. Scope 模型扩展（迭代一前置）

#### 数据模型

`DriveItem` 新增两列：

```java
@Column(name = "scope_type", nullable = false, length = 16)
@Enumerated(EnumType.STRING)
private DriveScopeType scopeType = DriveScopeType.PERSONAL; // PERSONAL | WORKSPACE

@Column(name = "scope_id", nullable = false, length = 64)
private String scopeId = ""; // personal: ownerPersonOaId; workspace: workspaceId
```

迁移策略：

- 表已存在 `cloudnext_drive_item`。新增列在 OpenJPA 启动时自动建列，默认值 `PERSONAL` + `""`。
- 启动后跑一次 **一次性回填**：所有 `scope_type='PERSONAL'` 且 `scope_id=''` 的行，把 `scope_id` 写为 `owner_person_oa_id`。回填用 `OneTimeBackfillRunner`（`ApplicationRunner` + 写入版本标记），版本标记落 `cloudnext_meta`（新表，单行 KV）。
- 同时新增索引 `idx_drive_item_scope_deleted_parent_updated`（`scope_type, scope_id, deleted, parent_id, updated_at`），后续 Workspace 与 Search 都走这个索引。

#### `DriveScopeAccessService`（新增，访问网关）

把"当前 Session 能否在某个 DriveItem 上做某种动作"统一收到一个服务。原 `DriveAccessService.requireOwnedItem` 改为 `requireItem(session, itemId, action)`：

```java
public DriveItem requireItem(AuthLoginResponse session, String itemId, DriveScopeAction action);
public boolean canActOn(AuthLoginResponse session, DriveItem item, DriveScopeAction action);
public WorkspaceRole resolveWorkspaceRole(AuthLoginResponse session, String workspaceId); // null 表示非成员
```

`DriveScopeAction`：`READ` / `WRITE` / `DELETE` / `MANAGE_SHARE` / `MANAGE_MEMBER`。

权限派生表：

| scopeType | 调用方 | READ | WRITE | DELETE | MANAGE_SHARE | MANAGE_MEMBER |
|---|---|---|---|---|---|---|
| PERSONAL | owner | ✔ | ✔ | ✔ | ✔ | n/a |
| PERSONAL | 非 owner | 走 `DriveShareService` 现有共享判定 | n/a | n/a | n/a | n/a |
| WORKSPACE | viewer | ✔ | ✘ | ✘ | ✘ | ✘ |
| WORKSPACE | member | ✔ | ✔ | ✔（移入 WorkspaceTrash） | ✔ | ✘ |
| WORKSPACE | admin | ✔ | ✔ | ✔ | ✔ | ✔（不能改/删 owner） |
| WORKSPACE | owner | ✔ | ✔ | ✔ | ✔ | ✔ |
| WORKSPACE | 非成员 | ✘ | ✘ | ✘ | ✘ | ✘ |

兼容策略：现有 `DriveService` / `DriveShareService` 内的 `requireOwnedItem(...)` 调用改为 `driveScopeAccessService.requireItem(...)`，逻辑不变（新建的个人文件 scopeType=PERSONAL，行为完全一致）。

### 2. 迭代一：Workspace 闭环

#### 实体

新增三个 JPA 实体（保持与现有命名风格一致，主键 64 位字符串，表名 `cloudnext_workspace*`）：

```java
@Entity @Table(name = "cloudnext_workspace",
  indexes = @Index(name="idx_workspace_status_updated", columnList="status, updated_at"))
public class Workspace {
    @Id @Column(length=64) private String id;
    @Column(nullable=false, length=100) private String name;
    @Column(nullable=false, length=64)  private String creatorPersonOaId;
    @Column(nullable=false, length=255) private String creatorName;
    @Column(nullable=false, length=16)  private String status; // ACTIVE | ARCHIVED
    @Column(nullable=false, length=64)  private String rootItemId; // == DriveItem.id, scopeType=WORKSPACE
    @Column(nullable=false, length=64)  private String createdAt;
    @Column(nullable=false, length=64)  private String updatedAt;
}

@Entity @Table(name = "cloudnext_workspace_member",
  uniqueConstraints = @UniqueConstraint(name="uk_ws_member", columnNames={"workspace_id","person_oa_id"}),
  indexes = {
    @Index(name="idx_ws_member_person", columnList="person_oa_id, workspace_id"),
    @Index(name="idx_ws_member_workspace_role", columnList="workspace_id, role")
  })
public class WorkspaceMember {
    @Id @Column(length=64) private String id;
    @Column(name="workspace_id", nullable=false, length=64) private String workspaceId;
    @Column(name="person_oa_id", nullable=false, length=64) private String personOaId;
    @Column(name="person_name",  nullable=false, length=255) private String personName;
    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=16) private WorkspaceRole role; // OWNER|ADMIN|MEMBER|VIEWER
    @Column(nullable=false, length=64) private String createdAt;
    @Column(nullable=false, length=64) private String updatedAt;
}
```

`Workspace.rootItemId` 指向一个 `scopeType=WORKSPACE`、`parentId=""`、`itemType=FOLDER`、`name=""` 的 DriveItem，对应需求中的 **WorkspaceRootItem**。所有空间内文件目录都挂在这棵树上，预览/下载/回收站直接复用既有 DriveItem 链路。

#### Service: `WorkspaceService`

核心方法：

```java
List<WorkspaceSummaryResponse> listMyWorkspaces(AuthLoginResponse session);
WorkspaceDetailResponse createWorkspace(AuthLoginResponse session, WorkspaceCreateRequest req);
WorkspaceDetailResponse getWorkspace(AuthLoginResponse session, String workspaceId);
List<DriveItemResponse> listItems(AuthLoginResponse session, String workspaceId, String parentId);
DriveItemResponse createFolder(AuthLoginResponse session, String workspaceId, WorkspaceFolderCreateRequest req);
DriveItemResponse uploadFile(AuthLoginResponse session, String workspaceId, MultipartFile file, ...);
List<WorkspaceMemberResponse> listMembers(AuthLoginResponse session, String workspaceId);
WorkspaceMemberResponse addMember(AuthLoginResponse session, String workspaceId, WorkspaceMemberAddRequest req);
WorkspaceMemberResponse updateMemberRole(AuthLoginResponse session, String workspaceId, String memberId, WorkspaceMemberUpdateRequest req);
void removeMember(AuthLoginResponse session, String workspaceId, String memberId);
```

关键设计点：

- **创建空间**：在同一个事务内 (a) 写 Workspace；(b) 写一条 `WorkspaceMember(role=OWNER)`；(c) 写 RootItem（DriveItem，`scopeType=WORKSPACE`、`scopeId=workspaceId`、`parentId=""`、`itemType=FOLDER`、`name=""`、`storageKey=""`）；(d) 把 RootItem.id 回填到 `Workspace.rootItemId`。
- **目录浏览**：复用 `DriveItemRepository` 新增方法 `findByScopeTypeAndScopeIdAndDeletedFalseAndParentId(...)`，返回顺序与个人文件一致（先目录后文件、按更新时间）。
- **写入操作**：`WorkspaceService.createFolder/uploadFile` 内部委托给 `DriveService` 的内部受保护方法 `createFolderInScope(session, scope, parentId, name)`、`uploadFileInScope(session, scope, parentId, file, strategy)`，避免重写一份目录树/冲突/命名逻辑。`DriveService` 现有 `createFolder` 改为薄包装（`scope = personal(session)`）。
- **删除入 WorkspaceTrash**：物理上仍是 `deleted=true` + `deletedAt` 标记。回收站隔离通过 Scope 过滤实现：
  - 个人回收站查询：`WHERE scope_type='PERSONAL' AND scope_id=:userId AND deleted=true`。
  - WorkspaceTrash 查询：`WHERE scope_type='WORKSPACE' AND scope_id=:workspaceId AND deleted=true`。
- **owner 守恒**（需求 4.5、3 新增 16）：`updateMemberRole` 与 `removeMember` 中加保护，若该操作会让 `role=OWNER` 数量变成 0 则抛 `ResponseStatusException(BAD_REQUEST)`。

#### Controller: `CloudNextWorkspaceController`

REST 路径与请求/响应：

| Method | Path | Body / Query | 返回 |
|---|---|---|---|
| GET | `/api/workspaces` | — | `List<WorkspaceSummaryResponse>` |
| POST | `/api/workspaces` | `{ name }` | `WorkspaceDetailResponse` |
| GET | `/api/workspaces/{id}` | — | `WorkspaceDetailResponse`（含 `myRole`、`rootItemId`） |
| GET | `/api/workspaces/{id}/items` | `?parentId=` | `List<DriveItemResponse>` |
| POST | `/api/workspaces/{id}/folders` | `{ parentId, name }` | `DriveItemResponse` |
| POST | `/api/workspaces/{id}/files` | multipart `parentId, file, conflictStrategy` | `DriveItemResponse` |
| GET | `/api/workspaces/{id}/members` | — | `List<WorkspaceMemberResponse>` |
| POST | `/api/workspaces/{id}/members` | `{ personOaId, role }` | `WorkspaceMemberResponse` |
| PATCH | `/api/workspaces/{id}/members/{memberId}` | `{ role }` | `WorkspaceMemberResponse` |
| DELETE | `/api/workspaces/{id}/members/{memberId}` | — | 204 |

`WorkspaceDetailResponse.myRole` 字段供前端判断 viewer/admin 等。`WorkspaceSummaryResponse` 含 `id, name, status, myRole, memberCount, updatedAt, creatorName`，避免前端再发一轮 N+1 查询。

#### 前端落地

**`cloudnext-web/src/api/cloudnext.js`** 增加：

```js
export function getWorkspaces() { return fetchJson('/api/workspaces'); }
export function createWorkspace(payload) { return fetchJson('/api/workspaces', { method:'POST', body: JSON.stringify(payload) }); }
export function getWorkspace(id) { return fetchJson(`/api/workspaces/${encodeURIComponent(id)}`); }
export function getWorkspaceItems(id, parentId='') { return fetchJson(`/api/workspaces/${encodeURIComponent(id)}/items${buildQuery({ parentId })}`); }
export function createWorkspaceFolder(id, payload) { return fetchJson(`/api/workspaces/${encodeURIComponent(id)}/folders`, { method:'POST', body: JSON.stringify(payload) }); }
export function uploadWorkspaceFile(id, { parentId='', file, conflictStrategy='abort' }) { /* multipart */ }
export function listWorkspaceMembers(id) { /* ... */ }
export function addWorkspaceMember(id, payload) { /* ... */ }
export function updateWorkspaceMember(id, memberId, payload) { /* ... */ }
export function removeWorkspaceMember(id, memberId) { /* ... */ }
```

**`WorkspacesPage.vue`**：移除 `mocks/drive.js` 引用，改为 `getWorkspaces()`。点击行进入路由 `/workspaces/:id`。

**新增 `WorkspaceDetailPage.vue`**（路由 `/workspaces/:id`）：

- 顶部面包屑：`协作空间 / {workspace.name}`。
- 文件区域复用 `<DriveFileTable />` 与 `<DriveToolbarActions />`，通过 prop 注入 `dataSource`：
  ```
  <DriveFileTable
    :items="items"
    :scope="{ type: 'workspace', id: workspaceId }"
    :readonly="myRole === 'viewer'"
    :enable-share="myRole !== 'viewer'"
    @navigate="..."
    @upload="..."
  />
  ```
  `DriveFileTable` 内部已经接收 items 与 actions 回调，现在新增 `scope` 与 `readonly` 两个可选 prop。`composables/drive/useDriveDirectoryState.js` 增加 `scope` 参数，把 `getMyDriveItems(parentId)` 替换为 `scope.type === 'workspace' ? getWorkspaceItems(scope.id, parentId) : getMyDriveItems(parentId)`。
- 成员管理入口：右上角"成员管理"按钮（仅 admin/owner 可见），点击打开 `<WorkspaceMembersDialog />`，样式沿用现有共享弹窗（同一套 `dialog-shell` class）。viewer/member 看不到"添加 / 修改角色 / 移除"按钮。

**路由变更**：`router/index.js` 加 `/workspaces/:id`，懒加载 `WorkspaceDetailPage`。

### 3. 迭代二：Public Link 闭环

#### 实体

```java
@Entity @Table(name = "cloudnext_drive_public_link",
  uniqueConstraints = @UniqueConstraint(name="uk_drive_public_link_token", columnNames="token"),
  indexes = {
    @Index(name="idx_dpl_creator_revoked_created", columnList="creator_person_oa_id, revoked, created_at"),
    @Index(name="idx_dpl_item", columnList="item_id")
  })
public class DrivePublicLink {
    @Id @Column(length=64) private String id;
    @Column(name="item_id", nullable=false, length=64) private String itemId;
    @Column(name="creator_person_oa_id", nullable=false, length=64) private String creatorPersonOaId;
    @Column(name="creator_name", nullable=false, length=255) private String creatorName;
    @Column(nullable=false, length=64) private String token; // 32 字节 base64url, 全局唯一
    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=16) private PublicLinkPermission permission; // PREVIEW | DOWNLOAD
    @Column(name="password_hash", nullable=false, length=128) private String passwordHash; // ""=无密码; 否则 bcrypt 串
    @Column(name="expires_at", nullable=false, length=64) private String expiresAt = ""; // ""=永久
    @Column(name="download_limit", nullable=false) private Long downloadLimit = -1L; // -1=不限
    @Column(name="download_count", nullable=false) private Long downloadCount = 0L;
    @Column(nullable=false) private boolean revoked;
    @Column(name="created_at", nullable=false, length=64) private String createdAt;
    @Column(name="updated_at", nullable=false, length=64) private String updatedAt;
}
```

`token` 用 `SecureRandom` 生成 24 字节，base64url 编码 → 32 字符；冲突时重试一次。

`passwordHash` 使用 BCrypt（Spring Security 已有 `BCryptPasswordEncoder` 风格依赖；若无，引入 `spring-security-crypto`，仅为 BCrypt 编码器）。"无密码"语义为 `passwordHash == ""`。

#### Service: `DrivePublicLinkService`

```java
DrivePublicLinkResponse create(AuthLoginResponse session, DrivePublicLinkCreateRequest req);
List<DrivePublicLinkResponse> listMine(AuthLoginResponse session);
DrivePublicLinkResponse update(AuthLoginResponse session, String id, DrivePublicLinkUpdateRequest req);
DrivePublicLinkResponse revoke(AuthLoginResponse session, String id);

// 公开端 (无 session)
DrivePublicLinkMetaResponse getMeta(String token);
DrivePublicLinkVerifyResponse verify(String token, String password);
DrivePreviewResponse preview(String token, String accessTicket);
DriveService.DriveDownloadPayload download(String token, String accessTicket);
```

设计要点：

- **创建权限**：`create` 时通过 `DriveScopeAccessService.canActOn(session, item, MANAGE_SHARE)` 校验。PERSONAL 文件需 owner；WORKSPACE 文件需 member/admin/owner（viewer 不行）。
- **`accessTicket`**：`verify` 成功后签发一段 JWT-ish 短令牌（30 分钟），载荷 `{ token, exp, iat }` 用 HMAC-SHA256 签名，密钥来自 `cloudnext.public-link.session-secret`（应用启动时若未配置则随机生成并写日志）。前端在 `preview/download` 调用时通过 `?accessTicket=...` 或 `X-CloudNext-Public-Ticket` 头携带。无密码外链不需要 ticket。
- **限额并发安全**：`download` 调用使用 `DrivePublicLinkRepository.incrementDownloadCountIfWithinLimit(id, limit)` JPQL 原子更新：
  ```
  UPDATE DrivePublicLink l SET l.downloadCount = l.downloadCount + 1, l.updatedAt = :now
  WHERE l.id = :id
    AND l.revoked = false
    AND (l.downloadLimit < 0 OR l.downloadCount < l.downloadLimit)
  ```
  返回 0 行表示本次下载被拒绝（已达上限或被吊销）。这条更新在事务内先执行；若返回 0 行直接拒绝，不开启文件流；返回 1 行后再走 `StorageGateway.load(...)`。
- **失效顺序**（需求 8.7-8.10）：`revoked → expired → permission/limit`，统一在 `assertOpen(token)` 工具方法内按这个顺序检查，先返回最严重的错误。
- **元信息脱敏**（需求 8.2）：`DrivePublicLinkMetaResponse` 仅含 `fileName, fileType, requirePassword, permission, isOpen`，不含 `creatorName / creatorPersonOaId / itemId`（公开端拿不到 itemId 防被穷举）。
- **预览复用**：`preview` 不重新做 office 渲染流程，复用 `DriveService.getPreview` 的"按 item 出 PreviewResponse"内部方法（提取为 `buildPreviewForItem(DriveItem, AccessContext)`）。AccessContext 表达"匿名 + 仅预览/仅下载 + token 标识"，让审计日志可以记录主体为 token 而非内部用户。

#### Controllers

`CloudNextLinkController`（`/api/links*`，需登录）：

| Method | Path | 描述 |
|---|---|---|
| POST | `/api/links` | 创建外链 |
| GET | `/api/links` | 列表（仅本人创建） |
| PATCH | `/api/links/{id}` | 修改 expiresAt / password / downloadLimit / permission |
| POST | `/api/links/{id}/revoke` | 失效 |

`CloudNextPublicLinkController`（`/public/links/*`，**不**走 `AuthSessionInterceptor`，通过 `WebMvcConfig` 排除）：

| Method | Path | 描述 |
|---|---|---|
| GET | `/public/links/{token}/meta` | 公开元信息 |
| POST | `/public/links/{token}/verify` | `{ password }` → `{ accessTicket }` |
| GET | `/public/links/{token}/preview` | 走 ticket（如需密码） |
| GET | `/public/links/{token}/download` | 走 ticket（如需密码） |

#### 前端 `LinksPage.vue`

完整重构为真实列表页：

```
+----------------------------------------------------------+
| 外链管理                            [创建外链 (从文件选择)] |
+----------------------------------------------------------+
| 加载中… / 暂无外链                                          |
| ┌────────────────────────────────────────────────────────┐|
| │ 文件 / 文件夹 ├ 权限 ├ 有效期 ├ 密码 ├ 下载 nn/总量 ├ 状态├操作│|
| │ ...                                                    │|
| └────────────────────────────────────────────────────────┘|
+----------------------------------------------------------+
```

操作单元包括：复制 URL（`navigator.clipboard.writeText`，失败回退 `<input>` + `execCommand`）、修改有效期/密码/下载次数/权限（弹窗）、失效（二次确认）。已失效条目操作按钮变成"已失效"灰色 chip。

错误路径：所有 `fetchJson` 失败统一通过 `useToast()` composable 显示，列表保留旧数据不动。

### 4. 迭代三：搜索 / 治理 / 存储平台化

#### 4.1 `DriveSearchService` (`GET /api/search/files`)

实现策略：第一阶段不引入 ES / Lucene，直接基于 JPA 的 `LIKE` 查询：

```java
public DrivePageResponse searchFiles(AuthLoginResponse session, DriveSearchRequest req);
```

`DriveSearchRequest` 字段：`scope`（personal/shared/workspace）、`keyword`、`itemType`（file/folder/all）、`owner`、`updatedFrom`、`updatedTo`、`page`、`size`。

执行计划：

- **scope=personal**：`scope_type='PERSONAL' AND scope_id=:session.personOaId AND deleted=false AND name LIKE :kw`，再按 `updated_at` 区间过滤。
- **scope=workspace**：先用 `WorkspaceMemberRepository.findByPersonOaId(...)` 拿到我的所有 workspaceId 列表，构造 `scope_type='WORKSPACE' AND scope_id IN (:ids) AND deleted=false AND name LIKE :kw`。`owner` 参数在此 scope 下解释为 `ownerPersonOaId`。
- **scope=shared**：复用现有 `DriveShareRepository` 的"分享给我"列表，先取所有可见的 itemId 集合，再 `id IN (:ids) AND deleted=false AND name LIKE :kw`。

权限过滤在每条命中行上再走一次 `DriveScopeAccessService.canActOn(session, item, READ)` 兜底，确保不漏（即使索引已过滤，DriveItem 在过滤期间可能被 unshared）。

`keyword` 长度上限 100，去前后空白；为空返回 `BAD_REQUEST`。

分页字段：`page` 从 1 开始（默认 1），`size` 默认 20，最大 100；响应 `{ items, page, size, total }` 与现有 `DrivePageResponse` 对齐。

#### 4.2 Quota / Audit / Retention

新增三个域：

**Quota**：

```java
@Entity @Table(name = "cloudnext_quota_subject",
  uniqueConstraints = @UniqueConstraint(columnNames={"subject_type","subject_id"}))
public class QuotaSubject {
    @Id String id;
    String subjectType; // PERSONAL | WORKSPACE
    String subjectId;
    long activeBytes;   // 非回收站文件总占用
    long trashBytes;    // 回收站占用
    String updatedAt;
}
```

Quota 仅在以下事件回调时变更（需求 11.4）：upload 成功、deleteToTrash、restore、purge。`DriveService` / `WorkspaceService` 在持久化成功后调用 `quotaService.applyDelta(scope, delta, area)`，`area` 区分 active 与 trash 桶。失败操作不更新（事务回滚保证）。

`GET /api/quotas/me`、`GET /api/quotas/workspaces/{id}` 给前端读。

**Retention**：扩展现有 `DriveTrashCleanupService`。原 cron `cloudnext.drive.trash-cleanup-cron` 默认每天 3:00 跑一次，改为遍历两类 scope。把 `cloudnext.drive.trash-retention-days` 拆成两条：`personal-trash-retention-days` 与 `workspace-trash-retention-days`（默认都 30 天）；现有单字段保留，作为兼容兜底。

**Audit**：

```java
@Entity @Table(name = "cloudnext_audit_log",
  indexes = {
    @Index(name="idx_audit_subject_time", columnList="subject_id, occurred_at"),
    @Index(name="idx_audit_action_time",  columnList="action, occurred_at"),
    @Index(name="idx_audit_target_time",  columnList="target_item_id, occurred_at")
  })
public class AuditLog {
    @Id String id;
    String action;        // FILE_DOWNLOAD | FILE_PREVIEW | SHARE_GRANT | SHARE_REVOKE
                          // | LINK_CREATE | LINK_REVOKE | LINK_PUBLIC_DOWNLOAD
    String subjectType;   // USER | PUBLIC_LINK
    String subjectId;     // userId 或 token
    String targetItemId;
    String scopeType;     // PERSONAL | WORKSPACE
    String scopeId;
    String occurredAt;    // ISO-8601
    @Lob String detailJson;  // 任意附加字段（JSON），如下载 byte 数、外链 id
}
```

`AuditLogService.record(...)` 是同步写入；为防止主链卡顿，使用 `@Async("auditExecutor")` 异步线程池写。线程池配置：核心 2、最大 4、队列 256；溢出降级为同步写并打 WARN 日志。

埋点位置：`DriveService.downloadItem/getPreview`、`DriveShareService.createShares/revokeShare`、`DrivePublicLinkService.create/revoke/download`。

`GET /api/admin/audit-logs?action=&subjectId=&from=&to=&page=&size=` 仅给管理员页（前端已存 AdminPage）使用。

#### 4.3 `StorageGateway`

把现有 `DriveStorageService` 接口改名为 `StorageGateway`，并扩展：

```java
public interface StorageGateway {
    StorageWriteResult write(String storageKey, MultipartFile file);
    StorageWriteResult write(String storageKey, InputStream input, long sizeBytes, String contentType);
    Resource read(StorageObject object);
    void delete(StorageObject object);
    boolean exists(StorageObject object);
}

public class StorageObject {
    String backend;     // "local" | "s3"
    String storageKey;  // backend 内部寻址 key
    long sizeBytes;
    String contentType;
}
```

`DriveItem` 新增三列承接 `StorageObject` 元信息：`storage_backend`（`local` 默认）、`storage_size_bytes`（已有 sizeBytes 不动，新增冗余只是为了 Storage 操作语义解耦也可以省略）、`storage_content_type`（同 contentType）。**最小落地为只新增 `storage_backend`，其他复用 `storage_key`/`size_bytes`/`content_type`**。

实现：

- `LocalStorageBackend`：保留现 `LocalDriveStorageService` 行为，把 `cloudnext-server/.data/drive-storage` 改为 `cloudnext.storage.local.root`，缺省值不变。
- `S3CompatibleStorageBackend`：基于 AWS SDK v2 `S3Client`（兼容 S3 / MinIO / OSS），配置：`cloudnext.storage.s3.endpoint / region / bucket / accessKey / secretKey / pathStyleAccess`。`storageKey` 即 S3 `Object Key`。

`StorageGateway` 由 `StorageGatewayConfiguration` 装配：

```java
@Bean
@ConditionalOnProperty(prefix="cloudnext.storage", name="backend", havingValue="local", matchIfMissing=true)
StorageGateway localBackend(...) { return new LocalStorageBackend(...); }

@Bean
@ConditionalOnProperty(prefix="cloudnext.storage", name="backend", havingValue="s3")
StorageGateway s3Backend(...) { return new S3CompatibleStorageBackend(...); }
```

切换通过 `cloudnext.storage.backend=local|s3` 单点控制，本地存储恒被保留为可选。

**写入回滚（需求 12.7）**：`DriveService.uploadFile` 内事务流程改为：

1. 生成 `storageKey = itemId + ext`；`gateway.write(...)` 成功后得到 `StorageObject`。
2. 在同一个 `@Transactional` 内 `driveItemRepository.save(item)`。
3. 注册 `TransactionSynchronization` 的 `afterCompletion`：若 `STATUS_ROLLED_BACK`，调用 `gateway.delete(storageObject)` 清理。
4. `replace` 策略下，旧 storageObject 的删除也放进 `afterCommit` 钩子，避免事务尚未提交就丢内容。

#### 4.4 `PreviewHealthService`

两个独立指标：

- `previewServiceUp`：CloudNext 自身 `/api/drive/items/{id}/preview` 在过去 5 分钟成功率 >= 95%。
- `documentServerUp`：定时（每 30 秒）`HEAD/GET cloudnext.drive.onlyoffice.documentServerUrl + healthcheck` ；连续 3 次失败置为 down。

数据存内存（`AtomicReference`，进程重启即重置即可，第一阶段不持久化）。

输出：`GET /api/admin/preview-health`：

```json
{
  "previewServiceUp": true,
  "documentServerUp": false,
  "previewRequestsLastMinute": 12,
  "previewFailuresLastMinute": 3,
  "lastDocumentServerError": "connection refused"
}
```

实现：`PreviewHealthRecorder` 在 `DriveService.getPreview` 入/出口埋点；`OnlyofficeProbeJob` 用 `@Scheduled(fixedDelay=30000)` 跑探测。两侧之间无强耦合，符合需求 13.3 的"两个指标独立维护"。

## Data Models

### 表汇总

| Table | 新增/修改 | 说明 |
|---|---|---|
| `cloudnext_drive_item` | 修改 | + `scope_type`, `scope_id`, `storage_backend`；新增索引 |
| `cloudnext_workspace` | 新增 | Workspace |
| `cloudnext_workspace_member` | 新增 | WorkspaceMember + 唯一约束 |
| `cloudnext_drive_public_link` | 新增 | DrivePublicLink + token 唯一 |
| `cloudnext_quota_subject` | 新增 | 用户/Workspace 容量统计 |
| `cloudnext_audit_log` | 新增 | 审计日志（含 token 主体） |
| `cloudnext_meta` | 新增 | 一次性回填 / 升级版本标记 |

所有新表沿用现有命名风格：表名 `cloudnext_*`、主键 `id VARCHAR(64)`、时间字段 `VARCHAR(64)` 存 ISO-8601。

### 状态机

**Workspace.status**：`ACTIVE → ARCHIVED`（迭代一只用 `ACTIVE`；`ARCHIVED` 字段先预留，不在第一阶段开放归档接口）。

**WorkspaceMember.role**：`OWNER`/`ADMIN`/`MEMBER`/`VIEWER`，转换约束：
- 只能由 owner 把现有 owner 移交给另一成员（admin 改成 owner 在第一阶段被拒绝，需求 4.5）。
- 任何会让 owner 数量降到 0 的 PATCH/DELETE 都被拒绝。

**DrivePublicLink lifecycle**：`OPEN → REVOKED (终态)`；`OPEN` 可同时叠加 `expired`、`exhausted`（语义状态，由 `expiresAt` / `downloadLimit` 派生，不写库）。

## Error Handling

延续现有 `CloudNextExceptionHandler` 与 `ResponseStatusException` 风格：

| 场景 | HTTP | message 关键词 |
|---|---|---|
| 未登录 | 401 | `cloudnext session not found` |
| 无成员关系访问 Workspace 接口 | 403 | `workspace access denied` |
| viewer 写入 | 403 | `workspace write denied` |
| Workspace 不存在 | 404 | `workspace not found` |
| 创建空间名称非法 | 400 | `workspace name is required` / `workspace name too long` |
| 把 owner 置 0 | 400 | `workspace must keep at least one owner` |
| 外链 token 无效 / 已吊销 / 已过期 | 410 | `link revoked` / `link expired` |
| 外链密码错误 | 401 | `link password invalid`（带 retry-after，避免暴力破解） |
| 外链 download 超限 | 429 | `link download limit reached` |
| 公开端尝试 download 但 permission=preview | 403 | `link download not permitted` |
| 搜索参数非法 | 400 | `search parameter invalid` |
| 存储写入失败（已回滚） | 500 | `storage write failed` |
| 上传时存储成功但元数据保存失败 | 500 | `storage rolled back` |

公开端点不返回 401（避免暴露登录语义）。所有状态码与现有 `DriveService` / `DriveShareService` 习惯保持一致；前端 `cloudnext.js` 现有 `createHttpError` 自动从 `message` 字段读出错误文案。

外链密码暴力破解保护：`POST /public/links/{token}/verify` 用内存级 `LoadingCache<String, RateBucket>` 限速，单 token 单 IP 最多 10 次/分钟；超出返回 429。第一阶段不引入 Redis。

## Testing Strategy

### 单元测试（JUnit 5 + Mockito）

| Component | 关注 | 关键 case |
|---|---|---|
| `DriveScopeAccessService` | 角色权限矩阵 | 4 角色 × 5 动作 = 20 case；非成员；scope 不一致 |
| `WorkspaceService` | 创建/成员/owner 守恒 | 创建后默认 owner；admin 不能改 owner；最后一个 owner 不可降级 |
| `DrivePublicLinkService` | 限额/失效顺序/密码 | revoked > expired > limit；密码错；并发下载（CountDownLatch 模拟） |
| `StorageGateway` | 写回滚 | 模拟 JPA save 抛异常，验证 backend.delete 被调用 |
| `QuotaService` | 仅在 4 类操作触发 | rename/move 不触发；upload/delete/restore/purge 触发 |
| `AuditLogService` | 主体记录 | 公开下载主体是 token 而非用户 |

### Integration 测试（`@SpringBootTest` + H2 in-memory）

- Workspace 全链路：HTTP 创建 → 上传 → 列表 → 删除 → 回收站显示在 workspace trash 而非个人 trash。
- 外链全链路：管理端创建 → 公开端 meta → verify → preview → download；revoke 后所有公开端都返回 410；超限拒绝；preview-only 链接拒绝 download。
- 搜索：在 personal / workspace / shared 三种 scope 下都能命中关键字；非成员搜索 workspace 返回 0 条。
- 配额：上传 1 个 1MB 文件 → 删除 → 还原 → 永久删除，活动桶与回收桶逐步迁移。

### 端到端回归

- 既有主链：个人文件 CRUD、共享文件、回收站、最近打开、OnlyOffice 预览（OA 配置走 `target/o2server/config/o2oa_onlyoffice.json`，jwtSecret 不入仓库）必须通过现有 `cloudnext-web/src/__tests__` 与 `cloudnext-server/src/test/java` 内既有用例。
- 前端：`vitest` 增加 `WorkspaceDetailPage`、`LinksPage`、`useDriveDirectoryState scope=workspace` 三个用例。
- 不在自动化范围内但需在每轮发布前手测的 P0 项写入 `Test Plan` 章节，对齐需求 14.1/14.2。

## Configuration Inventory

新增到 `cloudnext-server/config/application.yml`（在 `application.yml` 中以 default 占位，敏感值通过环境变量覆盖）：

```yaml
cloudnext:
  iteration:
    workspaces:
      enabled: true
    public-links:
      enabled: false
    search:
      enabled: false
    governance:
      enabled: false
    preview-health:
      enabled: false
  public-link:
    base-url: ""              # 用于拼接对外可分享 URL
    session-secret: ""        # accessTicket HMAC secret，留空则启动时随机
    session-ttl-seconds: 1800
  storage:
    backend: local            # local | s3
    local:
      root: ./.data/drive-storage
    s3:
      endpoint: ""
      region: ""
      bucket: ""
      access-key: ""
      secret-key: ""
      path-style-access: true
  drive:
    personal-trash-retention-days: 30
    workspace-trash-retention-days: 30
```

`cloudnext.drive.onlyoffice.*` 现有配置继续有效，不变。

## Migration & Rollout

按迭代验收推进，每一轮上线前的检查清单：

**迭代一上线前**：
- [ ] `scope_type` / `scope_id` 回填脚本跑成功，`cloudnext_meta` 写入版本号。
- [ ] 既有个人文件用例 100% 通过。
- [ ] Workspace 创建/成员/上传/删除/还原全部跑通。
- [ ] WorkspacesPage / WorkspaceDetailPage / 成员弹窗在 Chrome / Edge 测试通过。
- [ ] `cloudnext.iteration.public-links.enabled=false`、`search.enabled=false`，确认 `/api/links*`、`/public/links/*`、`/api/search/*` 全部 404。

**迭代二上线前**：
- [ ] `cloudnext_drive_public_link` 表自动建表成功，token 唯一索引存在。
- [ ] `cloudnext.public-link.session-secret` 显式配置（不依赖随机）。
- [ ] LinksPage 全部交互（复制/修改/失效/二次确认/错误回显）通过。
- [ ] 公开端 4 个接口在未登录状态下可访问；登录态接口仍受 token 校验。
- [ ] 把 `iteration.public-links.enabled=true`，`/api/status.pendingCapabilities` 中移除 `public-links`。

**迭代三上线前**：
- [ ] `cloudnext_quota_subject`、`cloudnext_audit_log` 表与索引就绪。
- [ ] 容量统计在 4 类事件下都能更新；其他事件不变。
- [ ] 审计日志覆盖列出的 6 类动作，外链动作主体记录为 token。
- [ ] `StorageGateway` 在 `local` 与 `s3` 之间通过单一配置切换；切到 s3 后回归上传/下载/预览/删除链路。
- [ ] `cloudnext.iteration.search.enabled=true`、`governance.enabled=true`、`preview-health.enabled=true`，并视情况开 `storage-gateway.objectStorageEnabled`。

迁移过程不依赖手写 SQL，全部依赖 OpenJPA 自动建表 + 应用启动后的 `OneTimeBackfillRunner`，与现有"重启触发自动建表"策略一致。

## Correctness Properties

下列属性在 PBT / 集成测试中应作为不变量被持续验证。每条都对齐到具体需求条目，方便迭代验收时打勾。

### Property 1: Scope 完整性

**Validates: Requirements 2.6, 2.7, 2.8**

- 任意 DriveItem 在任意时刻满足：`scopeType ∈ {PERSONAL, WORKSPACE}` 且 `scopeId != ""`。
- `scopeType=PERSONAL ⇒ scopeId == ownerPersonOaId`。
- `scopeType=WORKSPACE ⇒ ∃ Workspace w 使得 w.id == scopeId`。
- `scopeType / scopeId` 不能在生命周期内被另一个 Scope 覆写（move 跨 Scope 不允许；不属于第一阶段操作）。

### Property 2: Workspace owner 守恒

**Validates: Requirements 4.5**

- 对任意 Workspace `w`，在每次 `addMember / updateMemberRole / removeMember` 事务结束后：
  `count(WorkspaceMember{w, role=OWNER}) >= 1`。
- 任何会让该计数变为 0 的请求被拒绝且不修改数据。

### Property 3: WorkspaceTrash 隔离

**Validates: Requirements 4.7, 4.8**

- 个人回收站列表查询绝不返回 `scopeType=WORKSPACE` 的 DriveItem。
- WorkspaceTrash 查询绝不返回 `scopeType=PERSONAL` 的 DriveItem。
- 删除一条 `scopeType=WORKSPACE` 的 DriveItem 之后，它在该 Workspace 的 trash 视图可见，且在该用户的个人 trash 视图不可见。

### Property 4: 外链下载限额单调与原子

**Validates: Requirements 8.6, 8.9**

- `downloadCount` 单调非递减；任何"未签发下载流"的拒绝路径都不会增加 `downloadCount`。
- 在 N 个并发 `download` 请求下，只有 `min(N, downloadLimit - downloadCount)` 个请求得到下载流；其余被拒绝。
- 由 PBT 在 `incrementDownloadCountIfWithinLimit` 上验证（多线程 + 真实 H2 数据库）。

### Property 5: 外链失效顺序

**Validates: Requirements 8.7, 8.8, 8.10**

任意 Public_Endpoint 请求的拒绝判定按以下严格顺序：

1. `revoked == true` → `link revoked`
2. 否则若 `expiresAt` 已过 → `link expired`
3. 否则若 download 调用且 `permission == PREVIEW` → `link download not permitted`
4. 否则若 download 调用且 `downloadLimit >= 0` 且 `downloadCount >= downloadLimit` → `link download limit reached`

不得跨级跳过；不得对 `revoked` 的链接给出 `expired` 提示，也不得在 `expired` 的链接上提示限额。

### Property 6: 容量统计触发集合

**Validates: Requirements 11.3, 11.4**

- 仅在以下 4 种事件成功完成时 Quota 被更新：
  - `upload`：active += sizeBytes
  - `delete-to-trash`：active -= sizeBytes，trash += sizeBytes
  - `restore`：active += sizeBytes，trash -= sizeBytes
  - `purge`：trash -= sizeBytes
- 其他 DriveItem 操作（rename / move 同 scope / 预览 / 下载 / 共享 / 外链）不调用 `QuotaService.applyDelta`。

### Property 7: 写入与元信息原子

**Validates: Requirements 12.7**

- 对任意 `uploadFile` 请求 R，事务结束时下列三种状态之一成立：
  - 成功：`DriveItem(R)` 存在 ∧ `StorageGateway.exists(StorageObject(R)) == true`。
  - 失败：`DriveItem(R)` 不存在 ∧ `StorageGateway.exists(StorageObject(R)) == false`。
- 不存在"DriveItem 存在但底层无字节流"或"底层有字节流但 DriveItem 不存在"的悬挂状态。

### Property 8: 审计主体不冒名

**Validates: Requirements 11.7, 11.8**

- 由公开端发起的 `LINK_PUBLIC_DOWNLOAD` 审计记录中：`subjectType == PUBLIC_LINK ∧ subjectId == DrivePublicLink.token`。
- 不允许把外部访问者写为某个内部 user id；任何这种映射尝试在 `AuditLogService` 内被显式拒绝。

### Property 9: 迭代门禁

**Validates: Requirements 1.2, 1.3, 1.4**

- `cloudnext.iteration.public-links.enabled=false` 时：`/api/links*` 与 `/public/links/*` 的所有路径返回 404，对应 Bean 不进入容器。
- `cloudnext.iteration.search.enabled=false` 时：`/api/search/files` 返回 404。
- 不存在"flag 关闭但接口仍可访问"的状态；通过 `@SpringBootTest` 在两套 profile 下分别校验。
