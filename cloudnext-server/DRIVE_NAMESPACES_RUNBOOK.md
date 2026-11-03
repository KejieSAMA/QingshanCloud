# 云盘命名空间发布与验证手册

更新：2026-10-15。用户要求执行后续部署后，已完成隔离 Java/MySQL 验证、正式 V002 迁移及 API/Web 配套发布。Form 专属盘开关已启用。以下步骤保留为发布与恢复依据，不得对已迁移的库重复执行 V002。

## 数据与权限边界

`cloudnext_drive_namespace` 使用 `(tenant_id, entry_id)` 唯一映射；ID 为 UTF-8 字符串 `cloudnext:<tenantId>:<entryId>` 的 SHA-256 小写十六进制。`default` 为默认青山盘的保留入口标识，不是一个新账号。

文件、空间、成员、共享、外链、版本、收藏/最近打开、偏好、审计及存储清理队列具有不可变的 `drive_id`。Hibernate 查询过滤器在每个 JPA 事务开始时绑定经过验证的上下文，作用域仓库的派生查询也处于只读事务中；ID 加载、更新及删除另有实体检查。批量 HQL 更新显式限制盘 ID。

浏览器请求使用盘内会话和 `X-CloudNext-Drive` 一致性校验，专属盘缺少该头时拒绝访问。此头不能选择或授予另一个盘。前端凭据、刷新事件、缓存及请求代次均按盘区分；旧全局键只兼容默认盘。

预览和编辑票据绑定盘、访问者及文件，使用时重新检查准入与当前文件权限。专属盘外链还需对应盘登录；新专属外链使用 `/share/:token` 页面。默认盘既有匿名外链继续可用。

新内容保存在 `drives/<driveId>/<objectId>.<extension>`，历史默认盘 storage key 和文件内容不移动。存储层拒绝在专属盘读取历史默认对象或其他盘前缀。目录身份引用保护使用全局聚合计数/有效期投影，防止当前盘过滤器遗漏其他盘引用；不用于读取或列出其他盘文件。盘内维护清单只列当前盘对象。

入口归档及停用后不再启动该盘过期文件清理，删除根对象前再核对状态；鉴权服务不可用时暂停。已有事务按现有边界完成。恢复沿用原入口 ID 和数据，不自动启用；永久清理不是归档动作的一部分。

## 配套协议与配置

- Form 先部署只读 `drive-access/resolve`、`drive-status/resolve` 接口，但专属盘开关保持关闭。
- 云盘 `/api/auth/status` 增加 `driveNamespaceProtocol=1`；登录可带 `entryId`，会话返回 `drive: {id, entryId, name, isDefault}`。
- 免登使用每次尝试独立的 HttpOnly Cookie，完成请求为 `POST /api/auth/sso/complete?state=<公开关联值>`。关联值不是会话令牌或 Cookie 内容；旧无 state 的在途默认盘流程保留兼容。
- 默认 Form 会话也核对最新账号有效性。新账号认证协议部署前不得单独上线新版云盘 API。
- Form 调用路径可分别用 `CLOUDNEXT_FORM_DRIVE_ACCESS_PATH`、`CLOUDNEXT_FORM_DRIVE_STATUS_PATH` 配置；沿用已有 Form base URL 和服务令牌，禁止把令牌放入 Web 或日志。
- API 和 Web 必须配套更新，之后才能启用 Form 的 `CLOUDNEXT_DRIVE_NAMESPACES_ENABLED=true`。2026-10-15 已确认并启用；Form 手机入口参数另按既有 Web OTA 流程更新，无需 APK。

## 增量迁移

1. 编译、测试、维护窗口、迁移和部署均需另行明确授权。正式目标仅为云盘独立 MySQL 的 `qingshancloud`；隔离测试只用 `qingshancloud_test` 及专用账号，不接触 Form 库或旧库。
2. 只读核对当前部署版本、数据库名、Form 租户、表结构、各表行数及文件 ID/storage key 快照。确认所有写入服务和定时任务已进入维护窗口；本手册不会自动停止服务。
3. 将 `@cloudnext_namespace_tenant` 设置为已经核对的 Form 租户 ID，再显式执行 `src/main/resources/db/mysql/V002__drive_namespaces.sql`。脚本需要在该库创建并删除自己的临时迁移存储过程的权限，使用支持 `DELIMITER` 的 MySQL 客户端。
4. 脚本遇到错误数据库、多个/不一致租户、已存在命名空间或部分 `drive_id` 结构时立即拒绝。禁止用“忽略错误”继续执行。若历史租户不止一个，需要单独确认历史归属方案，不能修改 canonical ID 来绕过。
5. 所有历史业务行归入默认青山盘，A、B 初始为空。该脚本仅增加盘归属、命名空间、索引及偏好唯一约束，不改文件 ID、人员 canonical key、storage key 或文件内容。
6. 核对各表行数、归属、原有文件与版本引用；确认 `drive_id` 无空值，空间根与文件、共享与文件、版本与文件均同盘。对比迁移前 ID/storage key 快照，再恢复配套 API/Web。

MySQL DDL 会提交，失败后不自动回滚或重跑。需要先检查完成到哪一步，再准备明确的续跑方案。应用仍使用 `ddl-auto=validate`，不会自动执行此迁移。`V001__cloudnext.sql` 是新建空库的当前基线，不能拿来升级现有库；旧 H2 转换工具不是本次命名空间迁移入口。

专属盘产生数据后，禁止回退到忽略 `drive_id` 的旧 API。回退应关闭专属入口并保留支持隔离的后端和数据。

## 验证

已使用不访问数据库、不启动服务的检查：

- `node --test cloudnext-server/tools/mysql-contract.test.mjs cloudnext-server/tools/drive-namespace-contract.test.mjs`
- 云盘 Web 的 `src/__tests__` 模拟测试，以及 `src/next/tests/navigation.test.mjs`、`isolation.test.mjs` 源码检查。
- Form 的相关 API/Web 模拟测试与不输出制品的类型检查。

Java 编译及隔离 MySQL 回归已通过，共 177 项、0 失败、0 错误。首次回归发现的旧夹具上下文和切片依赖已补齐，未放宽运行时盘校验；云盘 Web 构建发现的外链 API 导入名称已修复。`DriveNamespaceTest` 和 `CloudNextDriveNamespaceFlowTest` 验证同一用户同名文件三盘独立、跨盘 ID 拒绝、撤权和专属外链准入。

`DriveNamespaceMigrationTest` 已通过，使用改造前 28 表基线快照，检查错误租户拒绝、13 张历史业务表的原始列逐值保留、默认盘归属及重复迁移拒绝。测试只重建经过库名与账号权限核对的 `qingshancloud_test`，结束时恢复当前 29 表基线；测试账号增加的 routine 权限仅限测试库。未在运行源码目录执行 Maven clean。

## 2026-10-15 发布记录

- 正式库：远程独立 MySQL 8.0.43 / 环回 13307 / `qingshancloud`，Form 租户只读核对为 `tenant-default`。
- 发布：`D:\QingshanCloud\releases\namespaces-20261015-eebf2aee2a18`，完整源码与制品清单随包保存。JAR SHA-256：`0763f904fc22de5f71b5b20150debae710da799bec41ee9b4631aa599dd4cf10`。
- 一致性备份及校验记录：`D:\QingshanCloud\backups\namespaces-20261015-082124`。28 张历史表原始列逐行哈希和行数保持一致，13 张表默认归属正确；新版启动后再次通过原始列核对。
- 存储：2,869 个文件 / 6,338,832,781 字节；迁移前后路径、大小、修改时间清单一致，未移动或上传业务文件。
- API health=UP，公网 Web 代理协议=1，Web 首页响应字节哈希与发布文件一致，未认证 `/api/auth/me` 返回 401。
- 云盘维护已关闭，原有调度设置已恢复；其他受保护的 MySQL/Form 监听进程未变。Form 默认盘准入通过真实账号的只读接口核对，没有创建登录会话。
- 正式入口配置和盘内业务操作未代用户修改；实际 UI、多标签、手机与 OnlyOffice 交互仍由人工验收。
- Form 手机入口代码已全量发布 Web OTA r36 / `2026.10.15.1`，兼容现有 vc33～34 壳，活动 revision 29 / FULL / 100%。云盘 PC/手机 Web 来自上面的远程配套发布；本次无 APK 构建。

人工验收覆盖：A/B 左上角及标题、历史文件留在青山、并行标签免登与退出、搜索/最近打开/回收站隔离、共享成员范围、预览和编辑保存、外链登录、改名、停用及归档恢复。助手不使用 Computer Use 或浏览器自动化进行 UI 验收。
