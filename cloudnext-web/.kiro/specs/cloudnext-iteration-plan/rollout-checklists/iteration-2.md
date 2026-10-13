# 迭代二上线前 · 主链回归 Checkpoint（外链管理）

> 本清单对应 `tasks.md` Task 2.17 与 `design.md` §"Migration & Rollout" 迭代二上线前。
> 由 **用户手动逐项确认**；不自动跑 `mvn`，不自动重启 OnlyOffice 容器，不自动重启 O2（遵循 `AGENTS.md` 中"不要随意进行编译"约定）。
> 任何一项未勾选 → **不要把 `cloudnext.iteration.public-links.enabled` 切到 `true`**；让 `/api/links*` 与 `/public/links/*` 继续 404。
>
> **自动 vs 手动**
> - 已自动：后端集成测试 `cloudnext-server/src/test/java/com/x/cloudnext/server/link/CloudNextPublicLinkFlowTest.java`（管理端 + 公开端 4 接口 + revoke + 限额 + 非创建者 403）。
> - 已自动：前端单元测试 `cloudnext-web/src/__tests__/LinksPage.test.js`，驱动 `useLinksPageState` 的加载 / 空 / 错误 / 复制 / 二次确认 / 修改后刷新分支。
> - 本清单只覆盖 **自动化覆盖不到 / 必须在真实环境上观察的 P0 项**。

---

## 0. 前置：DDL / 数据准备

- [ ] **0.1** O2 启动后 `cloudnext_drive_public_link` 表已被 OpenJPA 自动建表（不要手写 SQL）
  ↳ verify：H2 控制台或 MySQL Workbench 中 `\d cloudnext_drive_public_link` / `DESC cloudnext_drive_public_link;` 能看到该表，列含 `id, item_id, creator_person_oa_id, creator_name, token, permission, password_hash, expires_at, download_limit, download_count, revoked, created_at, updated_at`。
- [ ] **0.2** 索引齐全
  ↳ verify：表上至少存在以下三个：
  - `uk_drive_public_link_token`（`token` 唯一）
  - `idx_dpl_creator_revoked_created`（`creator_person_oa_id, revoked, created_at`）
  - `idx_dpl_item`（`item_id`）
- [ ] **0.3** `cloudnext_drive_item` 已含 `scope_type` / `scope_id` / `storage_backend`（迭代一遗留校验）
  ↳ verify：随机抽 1 条 `scope_type='PERSONAL'` 行，`scope_id` 与 `owner_person_oa_id` 相等；`cloudnext_meta.scope_backfill_v1 = 'done'`。
- [ ] **0.4** 如果环境从迭代一升级而来，确认 **没有遗留的 `revoked=true` 历史外链** 影响测试集
  ↳ verify：`SELECT COUNT(*) FROM cloudnext_drive_public_link;`；如果是新环境应为 0。

---

## 1. 配置前置（必须显式配置，不依赖运行时随机）

- [ ] **1.1** `cloudnext.public-link.session-secret` 在 `application-*.yml`（或环境变量覆盖）中显式配置
  ↳ verify：值非空字符串；不要让 `DrivePublicLinkSessionService` 走"启动时随机生成 + INFO 日志"分支，否则 O2 重启后所有 accessTicket 失效，已发外链的密码态会话全部断。
- [ ] **1.2** `cloudnext.public-link.base-url` 配置为 **对外可访问** 的主机（前端拼接外链 URL 用）
  ↳ verify：从外网浏览器拼接 `${base-url}/public/links/{token}/meta` 能拿到 200，不应是 `localhost` 或 `127.0.0.1`。
- [ ] **1.3** `cloudnext.public-link.session-ttl-seconds` 是预期值（默认 1800）
  ↳ verify：在 yml 中确认；过短会让用户输完密码刚要预览就过期。
- [ ] **1.4** `cloudnext.iteration.workspaces.enabled=true` 已保留（迭代一不回退）
  ↳ verify：`/api/workspaces` 返回 200。
- [ ] **1.5** `cloudnext.iteration.public-links.enabled` **当前仍为 `false`**，**保持** false 直到本清单全部勾选
  ↳ verify：`GET /api/links` 应返回 404，`GET /public/links/anything/meta` 应返回 404；这个状态是回归基线。
- [ ] **1.6** OnlyOffice 配置链不变
  ↳ verify：`target/o2server/config/o2oa_onlyoffice.json` 存在且 `callbackBaseUrl` / `jwtSecret` 仍是迭代一上线时的值；本次发布不重启 OnlyOffice 容器。

---

## 2. 迭代二闸门翻转（`public-links.enabled=true` 之后）

> 把 `cloudnext.iteration.public-links.enabled` 改成 `true` 后再做 §2 与 §3。

- [ ] **2.1** `/api/links` 列表接口可访问
  ↳ verify：登录态调用 `GET /api/links` 返回 200，初始空数组。
- [ ] **2.2** `/api/status.pendingCapabilities` 中 **不再** 包含 `public-links`
  ↳ verify：`GET /api/status` 响应 `pendingCapabilities` 不含字符串 `public-links`。

---

## 3. 后端真实回归（手测，登录态）

> 至少用两个测试账号 A、B；A 创建外链，B 用来验证非创建者 403。

### 3.1 创建外链（permission=download，含密码，有效期 + 下载次数限制）

- [ ] **3.1.1** A 调用 `POST /api/links`，body 含 `itemId`、`permission=DOWNLOAD`、`password=Test123!`、`expiresAt`（未来 1 天）、`downloadLimit=3`
  ↳ verify：返回 200，响应含 `id`、`token`（32 位 base64url）、`requirePassword=true`、`downloadCount=0`，且 `password` 不在响应里。
- [ ] **3.1.2** 数据库中 `password_hash` 是 BCrypt 串（`$2a$...` 开头），**不是** 明文
  ↳ verify：`SELECT password_hash FROM cloudnext_drive_public_link WHERE token=?;`。

### 3.2 公开端 meta / verify / preview / download

- [ ] **3.2.1** `GET /public/links/{token}/meta` 在 **无登录 cookie / token** 的环境下返回 200
  ↳ verify：从外部机器或开了无痕窗口（已清掉 `X-CloudNext-Token`）执行
  ```
  curl -i http://<host>/public/links/<token>/meta
  ```
  HTTP 200，body 含 `fileName / fileType / requirePassword=true / permission=DOWNLOAD / isOpen=true`，且 **不含** `creatorName / creatorPersonOaId / itemId`。
- [ ] **3.2.2** 401 不应作为公开端的拒绝码（避免暴露登录语义）
  ↳ verify：以未登录身份调 `/public/links/<token>/preview`（未带 ticket）应返回 4xx 但 **不是 401**（设计上 403 / 410，看上下文）。
- [ ] **3.2.3** `POST /public/links/{token}/verify` 密码正确 → 拿到 `accessTicket`
  ↳ verify：body `{ "password": "Test123!" }`，返回 200 含 `accessTicket`；密码错误返回 4xx 且不签发 ticket。
- [ ] **3.2.4** `GET /public/links/{token}/preview?accessTicket=<t>` 返回 200（或预览跳转）
  ↳ verify：office 文件走 `o2_lib/onlyoffice/index.html`，不会借用桌面应用 OnlyOfficeEditor 入口。
- [ ] **3.2.5** `GET /public/links/{token}/download?accessTicket=<t>` 连续调用 3 次都成功
  ↳ verify：每次 200 + 文件流；`downloadCount` 在数据库中从 0 单调升到 3。
- [ ] **3.2.6** 第 4 次 download 返回 429 `link download limit reached`，**`downloadCount` 仍为 3**（未递增）
  ↳ verify：再 `SELECT download_count` 仍是 3，不是 4。

### 3.3 修改外链（PATCH）

- [ ] **3.3.1** A 调 `PATCH /api/links/{id}` 修改 `expiresAt`（推后 1 天）→ 200，响应字段以服务端值为准
- [ ] **3.3.2** 修改 `password`（含 → 空 → 含）→ `requirePassword` 字段切换；公开端 `meta` 同步反映
- [ ] **3.3.3** 修改 `downloadLimit`（3 → 5）→ 后续 download 可再用 2 次
- [ ] **3.3.4** 修改 `permission`（DOWNLOAD → PREVIEW）→ 之后 `download` 返回 403 `link download not permitted`，`preview` 仍可用

### 3.4 失效（revoke）

- [ ] **3.4.1** A 调 `POST /api/links/{id}/revoke` → 200，`revoked=true`
- [ ] **3.4.2** revoke 后公开端全部返回 410 `link revoked`
  ↳ verify：`meta` / `verify` / `preview` / `download` 四条均 410；不再返回 200。
- [ ] **3.4.3** revoke 是终态：再次 PATCH 修改 / 再次 revoke 行为定义清晰（设计上不允许"复活"）
  ↳ verify：建议手测一次"PATCH 已 revoke 的链"，确认行为是 4xx 而不是悄悄复活。

### 3.5 失效顺序（设计文档的硬约束：revoked → expired → permission/limit）

- [ ] **3.5.1** 同时满足 `revoked=true ∧ expired ∧ exhausted` 时，`meta` 返回的拒因是 `link revoked`（最严重的先返）
- [ ] **3.5.2** `expired` 但未 revoke 时，`verify/preview/download` 返回 410 `link expired`
- [ ] **3.5.3** `permission=PREVIEW` 但未过期未吊销时，`download` 返回 403 `link download not permitted`，`preview` 仍 200

### 3.6 非创建者权限（403）

- [ ] **3.6.1** B（非 creator）调 `PATCH /api/links/{A 创建的 id}` → 403
- [ ] **3.6.2** B 调 `POST /api/links/{A 创建的 id}/revoke` → 403
- [ ] **3.6.3** B 调 `GET /api/links` → 仅返回 B 自己创建的链（不含 A 的）

### 3.7 公开端密码暴力破解保护（rate limit）

- [ ] **3.7.1** 同一 token + 同一 IP，1 分钟内连续 11 次 `POST /public/links/{token}/verify`
  ↳ verify：第 11 次返回 429 `link verify rate limited`，**前 10 次** 行为正常（密码错就 4xx，密码对就签 ticket）。
- [ ] **3.7.2** 等待窗口过去后能恢复，桶不污染其他 token
  ↳ verify：换 token2 / 换 IP 不受影响。

---

## 4. 前端 LinksPage 真实回归（Chrome / Edge 都过一遍）

> 自动化已覆盖 `useLinksPageState` 的状态机；以下手测确认 SFC 渲染、剪贴板、弹窗交互。

- [ ] **4.1** 进入 LinksPage 时显示加载态 → 切到列表态；空数据时显示空态文案，**不显示** mock 占位数据
- [ ] **4.2** 接口返回错误时（断网 / 后端 500）显示错误提示，**列表保留旧数据不动**，不显示假成功
- [ ] **4.3** 顶部"创建外链（从文件选择）"入口可用；可选择文件、设置 permission / 密码 / 有效期 / 下载次数；提交成功后列表前置出现新行
- [ ] **4.4** 行内复制 URL：剪贴板成功 → toast 提示成功；剪贴板被禁用时回退 `<input>` + `execCommand` → 仍出现明确成功 / 失败 toast
- [ ] **4.5** 行内修改弹窗：提交后用响应值刷新该行，不出现"前端先乐观写、后端报错前端不回退"的反例
- [ ] **4.6** 行内失效：弹二次确认对话框 → 确认后调 `POST /api/links/{id}/revoke` → 该行立即变灰、显示"已失效"chip、复制和修改按钮被隐藏 / 禁用
- [ ] **4.7** 已失效条目：复制和修改入口对它都不再显示
- [ ] **4.8** `downloadCount/downloadLimit` 列在每次列表刷新后与后端一致；`-1`（不限）显示为合理文案（如 `n / 不限`）
- [ ] **4.9** 顶部导航 / 侧边栏到 LinksPage 的入口在 `cloudnext.iteration.public-links.enabled=true` 时可见，**关闭** 时建议同步隐藏（见 §6 回滚条款）

---

## 5. 迭代一不回退（主链回归基线）

> 与迭代一 checkpoint 同一份清单。任意一条挂掉就别上线迭代二。

- [ ] **5.1** 个人文件 CRUD：浏览 / 上传 / 新建文件夹 / 重命名 / 移动 / 下载 / 删除入回收站
- [ ] **5.2** 共享文件：能收到分享、能浏览 / 预览 / 下载，不可写入
- [ ] **5.3** 回收站：还原 / 彻底删除（个人）；个人回收站绝不出现 `scope_type='WORKSPACE'` 行
- [ ] **5.4** 最近打开列表
- [ ] **5.5** OnlyOffice 预览：doc/docx/xls/xlsx/ppt/pptx 走 `o2_lib/onlyoffice/index.html`；pdf 走 PDF.js；图片浏览器内联打开；标题为真实文件名
- [ ] **5.6** Workspace 全链路：创建 / 列表 / 详情 / 上传 / 新建文件夹 / 删除入 WorkspaceTrash / 还原；WorkspaceTrash 绝不混入个人 trash
- [ ] **5.7** Workspace 成员管理：admin 不能改 owner；最后一个 owner 不可降级 / 不可移除（400 `workspace must keep at least one owner`）
- [ ] **5.8** 组织同步保持可用（OA → CloudNext 镜像不中断）
- [ ] **5.9** 首页最近访问保持可用

---

## 6. 回滚（Rollback）计划

如果迭代二上线后 4 小时内出现严重缺陷（公开端越权 / 限额失效 / 鉴权绕过 / 主链回退），按以下顺序回滚：

- [ ] **6.1** 把 `cloudnext.iteration.public-links.enabled` 改回 `false` 并重启 O2
  ↳ effect：`/api/links*` 与 `/public/links/*` 全部返回 404；`/api/status.pendingCapabilities` 重新出现 `public-links`。
- [ ] **6.2** **不需要** 数据回滚
  ↳ `cloudnext_drive_public_link` 表与已有外链记录可保留原状；下次重新打开 flag 时 token 仍然有效（只要 `session-secret` 没变）。
- [ ] **6.3** 前端导航处理
  ↳ LinksPage 路由本身仍存在并会渲染；回滚后该页会向 `/api/links` 发请求并拿到 404 / fetch 失败 → 显示错误态。建议同步把侧边栏 / 顶部导航中 LinksPage 的入口按 `pendingCapabilities` 包含 `public-links` 时隐藏；如不便发版隐藏，请明确告知现场用户"外链管理已暂停"。
- [ ] **6.4** OnlyOffice 容器不动
  ↳ 本次回滚不涉及 OnlyOffice 端，`/srv/onlyoffice/.env` 不改；`callbackBaseUrl` / `jwtSecret` 不动。
- [ ] **6.5** 验证回滚
  ↳ 重新跑 §1.5 与 §5 的全部基线项确认主链不受影响。

---

## 7. 闸门翻转记录（Sign-off）

- 把 `cloudnext.iteration.public-links.enabled=true` 写入 `application-*.yml` 并重启 O2 之后，再确认一次 §2 / §3 / §4。
- 上述所有 `[ ]` 全部勾上，再正式公告"迭代二外链管理对外开放"。

---

Signed off by: ____________  Date: ____________

Backout owner: ____________  Backout phone / IM: ____________
