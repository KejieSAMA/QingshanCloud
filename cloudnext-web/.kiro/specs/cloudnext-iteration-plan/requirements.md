# Requirements Document

## Introduction

本文档定义 CloudNext 在当前可用主链（个人文件 / 回收站 / 最近打开 / 在线预览 / 共享文件 / 系统台同步）之上的后续迭代路线，目标是把仓库中已识别的两块空白位（协作空间、外链管理）以及 `/api/status` 中挂着的 identity-broker / storage-gateway / preview-search 待补能力补齐为可上线的真实闭环。

本规约把后续迭代视为同一条主线特性（roadmap-level feature），分为三个**强顺序**子目标：

1. **迭代一：协作空间真实闭环**（最高优先级）
2. **迭代二：外链管理真实闭环**
3. **迭代三：搜索、治理与存储平台化**

需求按照以上分组组织。除明确标注为「Out of Scope（第一阶段不做）」的内容外，每条 Acceptance Criteria 在对应迭代落地时都必须满足。

## Glossary

- **CloudNext_Server**：CloudNext 的后端服务（`cloudnext-server` Spring 工程），负责 DriveItem、Workspace、Public Link 等业务模型与 API。
- **CloudNext_Web**：CloudNext 的前端应用（`cloudnext-web` Vue 工程），承载页面（HomePage / MyFilesPage / WorkspacesPage / SharedFilesPage / TrashPage / LinksPage / OnlyofficePreviewPage 等）。
- **OA_Platform**：现有 O2 OA 平台，仅承担登录与组织同步两类能力，不参与文件业务。
- **DriveItem**：CloudNext 已有的统一文件/目录实体，承载文件元数据、内容引用与权限。
- **Scope**：DriveItem 所归属的命名空间，由 `scopeType` + `scopeId` 描述，至少包含 `personal`（个人文件）和 `workspace`（协作空间文件）两种取值。
- **Workspace**：协作空间实体，表示一个独立于个人文件的团队级文件容器，拥有自己的成员列表与角色。
- **WorkspaceMember**：Workspace 与用户之间的成员关系实体，必须携带固定四档之一的 `WorkspaceRole`。
- **WorkspaceRole**：协作空间角色，固定取值为 `owner`、`admin`、`member`、`viewer` 四档。
- **WorkspaceRootItem**：协作空间下作为顶层父节点的特殊 DriveItem 或等价 scope 字段标识，用于把空间内文件树挂在该 Workspace 之下。
- **WorkspaceTrash**：协作空间回收站，与个人文件回收站隔离，按 Workspace 单独保存被删除的 DriveItem。
- **DrivePublicLink**：外链实体，代表一份 DriveItem 对外公开访问的凭证，至少包含 `itemId`、`creator`、`token`、`passwordHash`、`expiresAt`、`downloadLimit`、`downloadCount`、`revoked`、`createdAt`。
- **PublicLinkPermission**：外链权限，第一阶段固定两档：`preview`、`download`。
- **Public_Endpoint**：外链对应的对外 HTTP 入口，路径前缀为 `/public/links/{token}/...`，不要求登录。
- **Drive_Search_Service**：CloudNext 后端搜索服务，第一阶段范围为文件名与元数据搜索，覆盖个人 / 共享 / 工作区三类 Scope。
- **Storage_Gateway**：CloudNext 存储网关抽象层，对上提供统一读写接口，对下可对接本地存储或对象存储（S3 / MinIO / OSS 三选一）。
- **Quota_Service**：用户与 Workspace 容量统计服务。
- **Audit_Log_Service**：CloudNext 审计日志服务，记录下载 / 预览 / 共享 / 外链等关键动作。
- **Preview_Health_Service**：预览健康监测服务，暴露文档服务回源健康与预览失败统计。

## Requirements

### Requirement 1: 后续迭代主线优先级与不变量

**User Story:** 作为 CloudNext 产品负责人，我希望后续迭代严格按"协作空间 > 外链管理 > 搜索/治理/存储"的顺序推进，避免同时开太多战线导致主链回归，从而让每一阶段都有清晰的上线目标。

#### Acceptance Criteria

1. THE CloudNext_Server SHALL 将后续迭代主线优先级固定为：迭代一（协作空间）> 迭代二（外链管理）> 迭代三（搜索、治理与存储平台化）。
2. WHILE 迭代一未通过 Test Plan 中"协作空间"全部验收项，THE CloudNext_Server SHALL NOT 对外暴露迭代二或迭代三引入的新接口。
3. WHILE 迭代二未通过 Test Plan 中"外链管理"全部验收项，THE CloudNext_Server SHALL NOT 对外暴露迭代三引入的新接口。
4. WHEN 迭代二通过 Test Plan 中"外链管理"全部验收项，THE CloudNext_Server SHALL 允许对外暴露迭代三引入的新接口。
5. THE CloudNext_Server SHALL 在每一次迭代上线前保留以下既有能力可用：个人文件、共享文件、回收站、最近打开、OnlyOffice 预览、组织同步、首页最近访问。
6. THE CloudNext_Web SHALL 在每一次迭代上线前保留 HomePage 最近访问入口、MyFilesPage、SharedFilesPage、TrashPage、OnlyofficePreviewPage 的现有交互行为不回退。
7. THE CloudNext_Server SHALL 把"登录"与"组织同步"两类能力继续委托给 OA_Platform，不在本规约任意迭代中接管这两类能力。
8. THE CloudNext_Server SHALL 以 CloudNext 本地组织镜像作为身份与组织判断的真源，不在任意迭代中引入对 OA_Platform 的在线实时组织查询。
9. THE CloudNext_Server SHALL 通过自身配置链控制 OnlyOffice 的所有调用与配置，不在任意迭代中绕过 CloudNext 的配置入口直接读取 OA_Platform 的 OnlyOffice 配置。

---

## 迭代一：协作空间真实闭环

### Requirement 2: 协作空间领域模型

**User Story:** 作为 CloudNext 后端开发者，我希望以最小集合扩展领域模型来承载团队文件场景，从而让"团队文件"形成一条独立于个人文件的真实工作流，而不是把权限语义塞回共享文件。

#### Acceptance Criteria

1. THE CloudNext_Server SHALL 新增 Workspace 实体，至少包含：唯一标识、名称、创建者、创建时间、状态。
2. THE CloudNext_Server SHALL 新增 WorkspaceMember 实体，描述用户与 Workspace 的成员关系，并携带 WorkspaceRole 字段。
3. THE CloudNext_Server SHALL 把 WorkspaceRole 取值固定为 `owner`、`admin`、`member`、`viewer` 四档之一。
4. THE CloudNext_Server SHALL 新增 WorkspaceRootItem 概念（独立实体或 DriveItem 的等价 scope 字段），用于将一个 Workspace 内的文件树挂载到该 Workspace 之下。
5. THE DriveItem SHALL 新增 `scopeType` 和 `scopeId` 两个语义字段，分别标识所属 Scope 类型与 Scope 标识。
6. WHEN 一个 DriveItem 属于某个用户的个人文件，THE CloudNext_Server SHALL 将其 `scopeType` 置为 `personal`，`scopeId` 置为该用户标识。
7. WHEN 一个 DriveItem 属于某个 Workspace，THE CloudNext_Server SHALL 将其 `scopeType` 置为 `workspace`，`scopeId` 置为该 Workspace 标识。
8. THE CloudNext_Server SHALL 把 DriveItem 的 `scopeType` 与 `scopeId` 始终与该 DriveItem 的归属关系保持一致：归属于某用户个人文件的 DriveItem 的 `scopeType` 为 `personal`，归属于某 Workspace 的 DriveItem 的 `scopeType` 为 `workspace`，并按物理归属关系派生，而不是按访问者意图派生。
9. THE CloudNext_Server SHALL 把 Workspace 文件流复用现有 DriveItem 的预览、下载、回收站链路，不为协作空间新建并行的预览或下载实现。
10. WHILE 迭代一处于第一阶段，THE CloudNext_Server SHALL NOT 实现以下能力：多人实时编辑锁、评论或批注、历史版本、空间模板。

### Requirement 3: 协作空间最小接口集

**User Story:** 作为 CloudNext 前端开发者，我需要一组最小但完整的协作空间 HTTP 接口，从而让 WorkspacesPage 从 mock 列表切换到真实后端。

#### Acceptance Criteria

1. WHEN 一个已登录用户调用 `GET /api/workspaces`，THE CloudNext_Server SHALL 返回该用户作为成员的所有 Workspace 列表。
2. IF 一个已登录用户调用 `GET /api/workspaces` 时该用户在任何 Workspace 中都没有成员关系，THEN THE CloudNext_Server SHALL 返回空列表，而不返回与"无权访问"语义一致的错误响应。
3. WHEN 一个已登录用户调用 `POST /api/workspaces` 并提交合法的空间名称，THE CloudNext_Server SHALL 创建一个新的 Workspace，将调用方记为 `owner`，并返回新 Workspace 的标识与基本信息。
4. WHEN 一个已登录用户调用 `GET /api/workspaces/{id}`，THE CloudNext_Server SHALL 返回该 Workspace 的基本信息、当前调用者在该空间的 WorkspaceRole、以及 WorkspaceRootItem 标识。
5. WHEN 一个已登录用户调用 `GET /api/workspaces/{id}/items`，THE CloudNext_Server SHALL 返回该 Workspace 指定父目录下的 DriveItem 列表，并支持按目录浏览。
6. WHEN 一个 WorkspaceRole 为 `member`、`admin` 或 `owner` 的用户调用 `POST /api/workspaces/{id}/folders` 提交合法的目录信息，THE CloudNext_Server SHALL 在该 Workspace 下创建对应的目录型 DriveItem。
7. WHEN 一个 WorkspaceRole 为 `member`、`admin` 或 `owner` 的用户调用 `POST /api/workspaces/{id}/files` 提交合法的文件内容，THE CloudNext_Server SHALL 在该 Workspace 下创建对应的文件型 DriveItem。
8. WHEN 一个 WorkspaceRole 为 `admin` 或 `owner` 的用户调用 `GET /api/workspaces/{id}/members`，THE CloudNext_Server SHALL 返回该 Workspace 的成员列表与每名成员对应的 WorkspaceRole。
9. WHEN 一个 WorkspaceRole 为 `admin` 或 `owner` 的用户调用 `POST /api/workspaces/{id}/members` 并提交目标用户与目标 WorkspaceRole，THE CloudNext_Server SHALL 将该用户加入该 Workspace 并写入对应 WorkspaceRole。
10. WHEN 一个 WorkspaceRole 为 `admin` 或 `owner` 的用户调用 `PATCH /api/workspaces/{id}/members/{memberId}` 并提交合法的目标 WorkspaceRole，THE CloudNext_Server SHALL 更新该 WorkspaceMember 的角色为目标值。
11. WHEN 一个 WorkspaceRole 为 `admin` 或 `owner` 的用户调用 `DELETE /api/workspaces/{id}/members/{memberId}`，THE CloudNext_Server SHALL 将对应 WorkspaceMember 从该 Workspace 中移除。
12. IF 调用方对目标 Workspace 不具备成员关系，THEN THE CloudNext_Server SHALL 对该 Workspace 的所有读写接口返回与"无权访问"语义一致的错误响应。
13. IF 调用方在目标 Workspace 拥有成员关系但未携带显式 WorkspaceRole 字段，THEN THE CloudNext_Server SHALL 在执行权限判断时把该成员视作 WorkspaceRole 为 `member`。

### Requirement 4: 协作空间内权限与第一阶段操作集合

**User Story:** 作为协作空间使用者，我希望按 WorkspaceRole 严格区分浏览与写入能力，从而保证 viewer 不会误改文件、member/admin/owner 的权限按阶梯生效。

#### Acceptance Criteria

1. WHILE 迭代一处于第一阶段，THE CloudNext_Server SHALL 在协作空间内开放以下文件操作集合：浏览目录、上传文件、新建文件夹、预览文件、下载文件、删除到 WorkspaceTrash。
2. WHEN 一个 WorkspaceRole 为 `viewer` 的用户对该 Workspace 内的 DriveItem 发起任何写入类操作（上传、新建文件夹、修改、删除到 WorkspaceTrash 等），THE CloudNext_Server SHALL 拒绝该请求并返回与"无权写入"语义一致的错误响应。
3. WHEN 一个 WorkspaceRole 为 `member` 的用户在该 Workspace 内发起浏览、预览、下载、上传、新建文件夹、删除到 WorkspaceTrash 操作，THE CloudNext_Server SHALL 接受该请求。
4. WHEN 一个 WorkspaceRole 为 `admin` 的用户在该 Workspace 内发起 `member` 允许的所有操作以及成员管理类操作（增删改成员、变更非 owner 成员的 WorkspaceRole），THE CloudNext_Server SHALL 接受该请求。
5. IF 一个 WorkspaceRole 为 `admin` 的用户尝试将某成员设置为 `owner` 或将当前 owner 移除，THEN THE CloudNext_Server SHALL 拒绝该请求。
6. WHEN 一个 WorkspaceRole 为 `owner` 的用户在该 Workspace 内发起任意已开放的操作，THE CloudNext_Server SHALL 接受该请求。
7. WHEN 任意 WorkspaceRole 为 `member`、`admin` 或 `owner` 的用户对 Workspace 内的 DriveItem 执行删除，THE CloudNext_Server SHALL 把目标 DriveItem 移入对应 Workspace 的 WorkspaceTrash，而不是个人文件的回收站。
8. THE CloudNext_Server SHALL 把 WorkspaceTrash 与个人回收站在数据归属和列表查询上保持隔离。

### Requirement 5: 协作空间前端页面与成员管理交互

**User Story:** 作为 CloudNext 前端使用者，我希望 WorkspacesPage 是真实的列表与空间详情，并且空间内的文件表格、成员管理弹窗与现有页面风格一致，以减少二次学习成本。

#### Acceptance Criteria

1. THE CloudNext_Web SHALL 改造 WorkspacesPage 调用 `GET /api/workspaces` 渲染真实的 Workspace 列表，并去除原 mock 数据来源。
2. WHEN 用户在 WorkspacesPage 选择一个 Workspace 进入，THE CloudNext_Web SHALL 加载该 Workspace 的目录视图并展示 WorkspaceRoot 下的 DriveItem。
3. THE CloudNext_Web SHALL 在 Workspace 文件视图中复用 DriveFileTable 组件渲染 DriveItem，不为协作空间新建并行的列表组件。
4. THE CloudNext_Web SHALL 在 Workspace 视图中提供"成员管理"入口，并以与现有共享弹窗一致的轻弹窗样式呈现成员列表与角色变更控件。
5. WHEN 当前用户在该 Workspace 的 WorkspaceRole 为 `viewer`，THE CloudNext_Web SHALL 在该 Workspace 视图中隐藏或禁用上传、新建文件夹、删除等写入类入口。
6. WHEN 当前用户在该 Workspace 的 WorkspaceRole 不是 `admin` 或 `owner`，THE CloudNext_Web SHALL 在该 Workspace 视图中隐藏成员管理入口的写入类操作（增删改成员、修改 WorkspaceRole）。

---

## 迭代二：外链管理真实闭环

### Requirement 6: 外链领域模型

**User Story:** 作为 CloudNext 后端开发者，我希望以最小但完整的字段集合表达一份外链，从而支持有效期、密码、下载次数限制与失效等产品行为。

#### Acceptance Criteria

1. THE CloudNext_Server SHALL 新增 DrivePublicLink 实体，并至少包含字段：`itemId`、`creator`、`token`、`passwordHash`、`expiresAt`、`downloadLimit`、`downloadCount`、`revoked`、`createdAt`。
2. THE CloudNext_Server SHALL 把 DrivePublicLink 的 `token` 字段保持全局唯一。
3. THE CloudNext_Server SHALL 把 PublicLinkPermission 取值在第一阶段固定为 `preview` 或 `download` 之一。
4. WHEN 创建 DrivePublicLink 时未提供访问密码，THE CloudNext_Server SHALL 把 `passwordHash` 置为表示"无密码"的明确值，并不存储原始明文密码。
5. WHEN 创建 DrivePublicLink 时提供了访问密码，THE CloudNext_Server SHALL 仅保存该密码的哈希值至 `passwordHash`，不保留明文。
6. WHILE 迭代二处于第一阶段，THE CloudNext_Server SHALL NOT 实现以下能力：二维码、匿名上传、水印、外链协作者。

### Requirement 7: 外链管理后台接口

**User Story:** 作为外链创建者，我希望可以创建、查看、修改和失效自己的外链，从而控制外部访问的有效期、密码与下载次数上限。

#### Acceptance Criteria

1. WHEN 一个已登录用户调用 `POST /api/links` 并提交合法的 `itemId`、PublicLinkPermission、可选的有效期、可选的访问密码、可选的下载次数上限，THE CloudNext_Server SHALL 创建一份 DrivePublicLink，把 `creator` 置为调用方，生成唯一 `token`，并返回新外链的标识与可分享 URL。
2. IF 调用方对目标 `itemId` 不具备分享权限（既不是个人文件所有者，也不是该文件所在 Workspace 中具备分享权限的 WorkspaceRole），THEN THE CloudNext_Server SHALL 拒绝 `POST /api/links` 请求。
3. WHEN 一个已登录用户调用 `GET /api/links`，THE CloudNext_Server SHALL 返回当前调用方作为 `creator` 的 DrivePublicLink 列表，并在每条记录中包含 `revoked`、`expiresAt`、`downloadLimit`、`downloadCount`、PublicLinkPermission、是否设置了密码等状态字段。
4. WHEN 一个已登录用户调用 `PATCH /api/links/{id}` 修改有效期、访问密码、下载次数上限或 PublicLinkPermission 之一或多项，并且调用方是该 DrivePublicLink 的 `creator`，THE CloudNext_Server SHALL 应用变更并返回更新后的状态。
5. WHEN 一个已登录用户调用 `POST /api/links/{id}/revoke` 且调用方是该 DrivePublicLink 的 `creator`，THE CloudNext_Server SHALL 把该外链的 `revoked` 置为真，使后续公开访问立即失效。
6. IF 调用方不是某 DrivePublicLink 的 `creator`，THEN THE CloudNext_Server SHALL 拒绝对该外链的 `PATCH /api/links/{id}` 与 `POST /api/links/{id}/revoke` 请求。

### Requirement 8: 外链公开访问与权限/限额执行

**User Story:** 作为外部访问者，我希望通过外链链接安全地按设定权限访问目标文件，从而在密码、有效期、下载次数限制下完成预览或下载。

#### Acceptance Criteria

1. WHEN 任意访问者调用 `GET /public/links/{token}/meta`，THE Public_Endpoint SHALL 返回该外链的非敏感元信息，至少包括：目标文件名、文件类型、是否需要密码、PublicLinkPermission、外链是否仍然有效。
2. THE Public_Endpoint SHALL 在 `GET /public/links/{token}/meta` 的响应中不包含 `passwordHash`、`creator` 个人敏感信息或与该外链无关的其他用户信息。
3. WHEN 任意访问者调用 `POST /public/links/{token}/verify` 并提交访问密码，且该密码经哈希校验与 `passwordHash` 匹配，THE Public_Endpoint SHALL 返回一个用于后续 `preview` / `download` 调用的会话凭证，并在该外链需要密码的语义下作为放行依据。
4. IF `POST /public/links/{token}/verify` 提交的密码经哈希校验与 `passwordHash` 不匹配，THEN THE Public_Endpoint SHALL 拒绝该请求并不签发会话凭证。
5. WHEN 任意访问者调用 `GET /public/links/{token}/preview` 且通过了密码校验（如该外链需要密码）、未失效、未过期、PublicLinkPermission 至少为 `preview`，THE Public_Endpoint SHALL 返回目标 DriveItem 的预览内容或预览跳转。
6. WHEN 任意访问者调用 `GET /public/links/{token}/download` 且通过了密码校验（如该外链需要密码）、未失效、未过期、PublicLinkPermission 为 `download`、且未达到 `downloadLimit`（若设置），THE Public_Endpoint SHALL 返回目标 DriveItem 的下载流，并将该外链的 `downloadCount` 增加 1。
7. IF DrivePublicLink 的 `revoked` 为真，THEN THE Public_Endpoint SHALL 拒绝该 `token` 下的 `meta`、`verify`、`preview`、`download` 全部请求。
8. IF DrivePublicLink 的 `expiresAt` 早于当前时间，THEN THE Public_Endpoint SHALL 拒绝该 `token` 下的 `verify`、`preview`、`download` 请求。
9. IF 一次 `GET /public/links/{token}/download` 调用会使 `downloadCount` 超过 `downloadLimit`（若 `downloadLimit` 已设置），THEN THE Public_Endpoint SHALL 拒绝该下载请求并不增加 `downloadCount`。
10. IF DrivePublicLink 的 PublicLinkPermission 为 `preview`，THEN THE Public_Endpoint SHALL 拒绝该 `token` 下的 `download` 请求。

### Requirement 9: 外链管理前端页面

**User Story:** 作为 CloudNext 前端使用者，我希望在 LinksPage 上一站式完成外链的创建、复制、修改与失效，从而避免在多个页面之间来回跳转。

#### Acceptance Criteria

1. WHEN 当前用户进入 LinksPage 或在页面内主动刷新列表，THE CloudNext_Web SHALL 调用 `GET /api/links` 加载当前用户名下的外链列表替换原占位页内容，并在每条列表项上至少展示外链地址、关联文件或文件夹名称、创建时间、有效期、是否设置了访问密码、下载次数上限、当前 PublicLinkPermission 与失效状态。
2. WHILE LinksPage 正在请求 `GET /api/links` 期间，THE CloudNext_Web SHALL 在列表区域显示加载中状态，并在请求完成且返回的外链数量为 0 时显示空状态提示，不显示任何旧的占位数据。
3. THE CloudNext_Web SHALL 在 LinksPage 同一页内为每条外链就地提供以下操作入口：复制外链、修改有效期、设置或修改访问密码、修改下载次数上限、修改 PublicLinkPermission、失效外链，且全部交互无需跳转至其他页面。
4. WHEN 当前用户在 LinksPage 点击"复制外链"，THE CloudNext_Web SHALL 将该条外链的完整 URL 写入系统剪贴板，并在页面上显示明确的复制成功或复制失败提示。
5. WHEN 当前用户在 LinksPage 点击"失效"，THE CloudNext_Web SHALL 先弹出二次确认提示，在用户确认后调用 `POST /api/links/{id}/revoke`，并在收到成功响应后将该条外链在列表中标记为已失效，同时禁用该条目的复制与修改入口。
6. WHEN 当前用户在 LinksPage 修改有效期、访问密码、下载次数上限或 PublicLinkPermission 中的任意一项并提交，THE CloudNext_Web SHALL 调用 `PATCH /api/links/{id}`，并在收到成功响应后用后端返回的最新值更新列表中该条外链对应字段的展示。
7. IF `GET /api/links`、`POST /api/links/{id}/revoke` 或 `PATCH /api/links/{id}` 返回非成功响应或发生网络错误，THEN THE CloudNext_Web SHALL 在 LinksPage 显示包含失败原因的错误提示，保留列表中原有数据不变，并不得将本次修改或失效操作回显为已生效状态。

---

## 迭代三：搜索、治理与存储平台化

### Requirement 10: 文件名与元数据搜索

**User Story:** 作为 CloudNext 用户，我希望按关键字在个人 / 共享 / 工作区三类文件中搜索文件，从而快速定位目标文件。

#### Acceptance Criteria

1. WHEN 一个已登录用户调用 `GET /api/search/files` 并提供 `scope` 与 `keyword` 参数，THE Drive_Search_Service SHALL 在该 `scope` 限定的范围内按文件名与元数据匹配 `keyword`，并返回命中 DriveItem 列表。
2. THE Drive_Search_Service SHALL 把 `scope` 取值至少支持以下三类：`personal`（个人文件）、`shared`（共享文件）、`workspace`（工作区文件）。
3. THE Drive_Search_Service SHALL 把 `GET /api/search/files` 接受的查询参数集合至少包括：`scope`、`keyword`、`itemType`、`owner`、`updatedFrom`、`updatedTo`、`page`、`size`。
4. WHEN 调用方对某条命中 DriveItem 不具备读取权限（个人文件非所有者、共享文件未被分享、工作区文件不在对应 Workspace 成员中），THE Drive_Search_Service SHALL 不在返回结果中包含该条 DriveItem。
5. THE Drive_Search_Service SHALL 在 `GET /api/search/files` 的响应中至少返回：命中 DriveItem 列表、当前页、总数或下一页指针，以支持分页。
6. WHILE 迭代三处于第一阶段，THE Drive_Search_Service SHALL NOT 实现以下能力：OCR、文档内容全文索引、智能标签、自动分类。

### Requirement 11: 容量统计、回收站策略与审计日志

**User Story:** 作为 CloudNext 管理员，我希望系统能持续给出用户容量、空间容量、回收站保留策略与关键动作的审计记录，从而支持日常治理与审计追溯。

#### Acceptance Criteria

1. THE Quota_Service SHALL 维护每个用户的容量统计，至少包括：个人文件总占用、个人回收站占用。
2. THE Quota_Service SHALL 维护每个 Workspace 的容量统计，至少包括：Workspace 文件总占用、WorkspaceTrash 占用。
3. WHEN DriveItem 在个人文件或某 Workspace 内发生上传、删除（移入回收站）、还原、彻底删除，THE Quota_Service SHALL 在该操作完成后更新对应主体（用户或 Workspace）的容量统计值。
4. THE Quota_Service SHALL 仅在第 3 项列出的操作（上传、删除入回收站、还原、彻底删除）完成时更新容量统计值，不为其他 DriveItem 操作触发额外的容量更新。
5. THE CloudNext_Server SHALL 提供回收站保留策略，至少支持按"保留天数上限"对个人回收站与 WorkspaceTrash 中超期的 DriveItem 进入彻底删除流程。
6. THE Audit_Log_Service SHALL 记录以下关键动作的审计日志：文件下载、文件预览、共享授权变更、外链创建、外链失效、外链公开下载。
7. THE Audit_Log_Service SHALL 在每条审计日志中记录：动作类型、动作时间、动作主体（已登录用户标识或外链 token 标识）、目标 DriveItem 标识、所属 Scope。
8. IF 一次动作的发起方为外链公开访问，THEN THE Audit_Log_Service SHALL 把动作主体记录为对应 DrivePublicLink 的 `token` 标识，而不是把外部访问者强制写为某个内部用户。

### Requirement 12: 存储网关与对象存储接入

**User Story:** 作为 CloudNext 平台运维者，我希望存储层有一个真正的网关抽象，并能在保留本地存储的同时接入一种对象存储实现，从而支撑后续平台化部署。

#### Acceptance Criteria

1. THE Storage_Gateway SHALL 对上提供统一的 DriveItem 内容读写接口，使 DriveItem 上层业务无需感知底层存储类型。
2. THE Storage_Gateway SHALL 保留本地存储实现，作为默认部署形态。
3. THE Storage_Gateway SHALL 新增一个对象存储实现，并在 S3、MinIO、OSS 三者中至少接入一个。
4. THE Storage_Gateway SHALL 在引入对象存储实现时同时保留本地存储实现，不允许通过移除本地存储来仅保留对象存储。
5. THE CloudNext_Server SHALL 通过配置选择 Storage_Gateway 当前激活的实现（本地存储或某种对象存储），不在代码中硬编码切换。
6. WHEN 一次 DriveItem 写入流程完成，THE Storage_Gateway SHALL 把存储位置元信息保存到 DriveItem 关联的存储记录中，使后续读取无需依赖外部参数。
7. IF 一次 DriveItem 写入的字节流已成功落入存储后端，但存储位置元信息保存失败，THEN THE Storage_Gateway SHALL 回滚本次写入并清理已写入的字节流，使该 DriveItem 不进入"有内容但无元信息"的状态。
8. WHEN Storage_Gateway 的当前激活实现切换为对象存储，THE CloudNext_Server SHALL 仍然让既有上传、下载、预览、删除链路保持可用，不强制变更上层调用语义。

### Requirement 13: 预览健康暴露

**User Story:** 作为 CloudNext 管理员，我希望预览链路的可用性与健康状态可被观察，从而在 OnlyOffice 文档服务异常时能快速定位。

#### Acceptance Criteria

1. THE Preview_Health_Service SHALL 继续沿用 OnlyOffice + 浏览器内嵌预览作为预览实现，不在迭代三引入新的桌面预览器。
2. THE Preview_Health_Service SHALL 暴露当前预览能力的可用性状态，至少标识：预览服务是否可用、文档服务回源是否健康。
3. THE Preview_Health_Service SHALL 把"预览服务是否可用"与"文档服务回源是否健康"作为两个独立指标维护，不在文档服务不健康时强制把预览服务可用性标记为不可用。
4. THE Preview_Health_Service SHALL 暴露预览失败统计指标，至少包括：单位时间内预览请求数、单位时间内预览失败请求数。
5. WHILE 迭代三处于第一阶段，THE Preview_Health_Service SHALL NOT 引入 OCR、内容全文索引、智能标签、自动分类等高级预览/索引能力。

---

## 回归与边界（贯穿三轮迭代）

### Requirement 14: 主链回归与 OA 边界

**User Story:** 作为 CloudNext 用户，我希望每一轮迭代上线后，原有主链操作不被破坏，CloudNext 与 OA 的边界保持不变。

#### Acceptance Criteria

1. THE CloudNext_Server SHALL 在每一轮迭代完成时通过以下回归项：个人文件浏览/上传/下载/删除、共享文件浏览/接收、回收站还原与彻底删除、最近打开列表、OnlyOffice 预览、组织同步、首页最近访问。
2. THE CloudNext_Web SHALL 在每一轮迭代完成时保留 HomePage 最近访问、MyFilesPage、SharedFilesPage、TrashPage、OnlyofficePreviewPage 的现有交互可用。
3. THE CloudNext_Server SHALL 在所有迭代中把"登录"与"组织同步"两类能力继续委托给 OA_Platform，不接管这两类能力。
4. THE CloudNext_Server SHALL 在所有迭代中把文件业务（个人文件、共享文件、协作空间、外链、搜索、治理、存储、预览）保留在 CloudNext 内，不把这些业务下沉到 OA_Platform。
5. THE CloudNext_Server SHALL 把"共享文件"作为单纯的文件分发能力维持现状，把多人协作写入语义放到 Workspace 体系，不在共享文件入口上叠加 WorkspaceRole 语义。

---

## Out of Scope（第一阶段不做）

以下能力被显式标注为本规约范围外，三轮迭代第一阶段均不实现：

- 迭代一第一阶段不做：多人实时编辑锁、评论或批注、历史版本、空间模板。
- 迭代二第一阶段不做：二维码、匿名上传、水印、外链协作者。
- 迭代三第一阶段不做：OCR、文档内容全文索引、智能标签、自动分类。
- 任意迭代均不做：让 CloudNext 接管 OA_Platform 的登录或组织同步；把文件业务整体迁回 OA_Platform；OnlyOffice 调用绕过 CloudNext 自身配置链。
