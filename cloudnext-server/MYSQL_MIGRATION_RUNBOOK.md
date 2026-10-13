# MySQL 切换运行手册

## 状态与边界

本轮交付源码、版本化建表 SQL、独立迁移工具及测试改造。**提交/拉取代码不代表已切换数据库。** 编译、后端测试、真实迁移及重启需明确发版授权后执行。不要直接用新 JAR 启动旧 H2 配置。

已确认目标：远程现有 MySQL 8.0.43 的 3307 实例；正式库 `qingshancloud`，测试库 `qingshancloud_test`。不使用审批平台独立的 13306 实例，不改其他业务库、不重启 MySQL、不改其全局变量。远程项目文件、备份、工具、临时目录和日志全部在 `D:\QingshanCloud`；当前文件内容仍在 `D:\QingshanCloud\data\drive-storage`。Form、OnlyOffice、端口及公网入口不变。

## 1. 用户准备数据库和账号

通过现有数据库管理工具创建两个**空库**，均使用 `utf8mb4` / `utf8mb4_0900_bin`。正式与测试使用不同的专用账号，只允许服务器本机连接，不授予角色、全局权限或 `GRANT OPTION`。建库语句：

```sql
CREATE DATABASE qingshancloud CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE DATABASE qingshancloud_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
```

为两个账号分别授予自己库上的 `SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, DROP`。`DROP` 用于初始化失败清理和测试夹具，代码只针对已确认的本次对象或测试库；不得授权到 `*.*`。账号 host 需能接受经 `127.0.0.1` TCP 建立的本机连接，实施时核对 `CURRENT_USER()` 和 `SHOW GRANTS`，不能为图方便改成公网可访问。

根目录已忽略的 `.env` 中补充四项（不在聊天、脚本或 Git 中填写真实密码）：

```dotenv
CLOUDNEXT_DB_USERNAME=正式专用账号
CLOUDNEXT_DB_PASSWORD=正式密码
CLOUDNEXT_TEST_DB_USERNAME=测试专用账号
CLOUDNEXT_TEST_DB_PASSWORD=测试密码
```

正式 URL 在远程私有运行配置中设置 `jdbc:mysql://127.0.0.1:3307/qingshancloud`。驱动固定为 MySQL，删除旧 H2 URL/驱动环境项，设置 `ddl-auto=validate`。账号配置仅通过私有启动环境注入；Spring Boot 不自动读取 `.env`。

## 2. 构建和测试（仅在获明确授权后）

使用隔离源码副本，禁止在实际运行目录 `clean`。正式 API 和迁移工具独立构建；后者不是 API 的子模块或依赖。两个命令分别在后端目录和 `tools/h2-to-mysql/` 执行：

```text
mvn test package
```

API 产物 `target/cloudnext-server-0.1.0-SNAPSHOT.jar`，迁移工具产物 `target/h2-to-mysql-1.0.0.jar`。检查正式 JAR 的 `BOOT-INF/lib` 不含 `h2-*.jar`；工具为单独可执行 JAR，不部署进 API 类路径。

测试通过已有 SSH 连接建立仅绑定本地环回的转发，例如本地 13307 转发远程 `127.0.0.1:3307`。不修改服务器防火墙、不开放新数据库端口。测试进程私有环境为：

```dotenv
CLOUDNEXT_TEST_ENVIRONMENT=isolated-mysql
CLOUDNEXT_TEST_DB_URL=jdbc:mysql://127.0.0.1:13307/qingshancloud_test
CLOUDNEXT_TEST_DB_USERNAME=测试专用账号
CLOUDNEXT_TEST_DB_PASSWORD=测试密码
```

测试先校验库名、MySQL 8.0、账号授权和独立环境标识，再初始化/清理。缺少配置时失败，不跳过并声称通过。测试账号只能有 `qingshancloud_test.*` 权限。API 测试禁用全部调度；Form 和存储等外部服务使用模拟接口。

**API 测试与迁移工具测试串行运行，不得与其他测试进程共用测试库并发执行。** API 测试清空业务表；迁移工具测试会删除测试库中的已知业务表。迁移工具内允许制造 H2 旧格式输入，API 测试不使用 H2。

非编译静态检查可单独执行：

```text
node tools/mysql-schema.mjs
node --test tools/mysql-contract.test.mjs
```

## 3. 停写、备份及只读预检

1. 先只读核对远程实际配置、云盘进程归属、MySQL 变量及其他服务健康基线。若 MySQL 的数据、日志、临时目录位于 C 盘或通过链接落到 D 盘以外，阻断切换；不擅自调整已有数据库服务。
2. 云盘入口返回维护提示，拦截全部新业务及 OnlyOffice 回调，暂停新编辑。等待在途上传/保存完成，停用同步、清理调度，再停止**仅属于云盘**的 API 与自动重启监督进程。不能只杀 API 后让监督进程拉起旧库。
3. 确认 API 已停、H2 文件已解锁。备份当前 `D:\QingshanCloud\data\cloudnext.mv.db`、私有环境配置、监督脚本和发布版本记录至 D 盘独立备份目录。保持存储目录不变，备份文件/历史版本内容到 D 盘并生成完整大小/SHA-256 清单。校验备份完成后才能继续。
4. 使用远程当前数据作为源，不使用先前本机快照。确认冷备可只读打开、没有文件锁或待修复状态，记录冷备 SHA-256。
5. 确认旧 JVM 的实际时区和旧审计 API 显示时间，设置源时区；旧 H2 的无时区 TIMESTAMP 不可直接当 UTC 读取。若旧部署为中国时区，参数为 `Asia/Shanghai`；不确认则禁止导入。字符串时间原样复制，`Instant` 按源时区解析到 UTC 并显式截断至微秒，报告记录涉及数量。

迁移进程除正式连接配置外，还需以下环境项：

```dotenv
CLOUDNEXT_MIGRATION_SOURCE=D:/QingshanCloud/backups/本次目录/cloudnext.mv.db
CLOUDNEXT_MIGRATION_SOURCE_SHA256=本次冷备的SHA256
CLOUDNEXT_MIGRATION_SOURCE_TIME_ZONE=经核实的旧JVM时区
CLOUDNEXT_STORAGE_LOCAL_ROOT=D:/QingshanCloud/data/drive-storage
CLOUDNEXT_MIGRATION_REPORT=D:/QingshanCloud/backups/本次目录/preview.migration-report.json
```

H2 源账号缺省 `sa`，源密码缺省为空；若不同，使用 `CLOUDNEXT_MIGRATION_SOURCE_USERNAME/PASSWORD` 私有环境项。所有路径必须在 D 盘且不经过符号链接或目录联接；预先建立继承严格 ACL 的目录。JVM 参数使用 `-Djava.io.tmpdir=D:/QingshanCloud/tmp -Duser.home=D:/QingshanCloud/home`，进程 `TEMP/TMP` 同样指向 D 盘。

```text
java [上述D盘JVM参数] -jar h2-to-mysql-1.0.0.jar --mode preview
```

预检校验源 SHA、全部 28 张表/字段、目标空库、MySQL 版本/账号授权和服务器文件路径，读取全量元数据摘要与存储清单。报告会标注历史缺失/隔离文件及原有引用问题；不自动恢复文件、不关闭防病毒、不删除孤儿文件。

## 4. 导入、验证和切换

每次运行必须使用新的报告文件名，工具拒绝覆盖既有报告。依次运行：

```text
java [D盘JVM参数] -jar h2-to-mysql-1.0.0.jar --mode import
java [D盘JVM参数] -jar h2-to-mysql-1.0.0.jar --mode verify
java [D盘JVM参数] -jar h2-to-mysql-1.0.0.jar --mode clear-sessions
```

- `import` 持有云盘专用迁移锁，只接受完全空的目标库，显式执行 V001 SQL，然后在一个事务内按 200 行分批参数化写入。提交前比较行数、主键集合、规范化内容 SHA-256、业务引用和完整文件清单。
- 空间成员、文件所有者、PERSON/UNIT/IDENTITY 与 canonical alias 解析结果会与源库核对；每个 ACTIVE 空间必须仍有可登录 OWNER。已有历史问题记录到报告，新引用差异阻断提交。所有业务 ID、版本号、storage key、映射状态、偏好和审计原样保留。
- 失败回滚本次数据，清理仅本次创建且为空的表。若进程被强制终止，可能留下 DDL（MySQL DDL 不参与数据事务）；后续导入会拒绝继续。管理员应根据报告中的 `createdTables` / `creatingTable` 审核并处理，不能对目标库盲目清空。
- `verify` 只读复核；`clear-sessions` 在复核完全一致后，事务清空旧登录/SSO 状态并更新编辑票据失效时间。它不删除文件、空间或共享。清会话后元数据摘要的上述差异为预期；不能再用未经清会话的源摘要声称全表相同。
- 只有 `VERIFIED` 与 `CUTOVER_READY` 报告齐全后，才更新远程私有环境至 MySQL、新版 JAR 及相应监督脚本发布路径。先在维护状态启动，确认 schema 校验和健康，再恢复调度、解除维护并要求重新登录。不得先切配置再尝试导入。
- 启动后检查 API、登录/目录和数据库实际名称，人工验收文件、空间与共享；核对其他原有服务状态。前端不需要重建，本轮 API 接口不变。

本次仅使用服务器既有数据库进程；所有连接均为本机环回或 SSH 转发，MySQL 正式池最多 5 连接、迁移单线程。不添加 Windows 服务或任务，不修改既有业务服务启动方式。

## 5. 备份、失败恢复和验收记录

切换前保存冷备 H2、存储备份、配置与发布记录。导入完成后、开放业务前生成 MySQL 一致性备份到 D 盘。使用专用账号和受保护的客户端配置文件提供密码，不在 `mysqldump -p明文`、命令历史或日志中暴露凭据；备份可使用 `--single-transaction --no-tablespaces --skip-triggers`（本业务没有触发器）。

切换失败保持维护并保留报告，不自动回退 H2。切换后已有写入时，恢复必须使用 MySQL 备份并协调同一时点的存储，不能用旧 H2 覆盖新增数据。保留旧 H2 作为离线证据，未经另行确认不删除。

验收记录包含：代码 commit、构建包 SHA、源备份 SHA、源时区、每表行数/主键摘要/内容摘要、文件总数/大小/哈希、已有缺失文件、空间 OWNER 与引用检查、会话清理数量、实际数据源、健康结果和其他业务服务基线。权限、排序、搜索、复制、回收站、外链、历史版本沿用已有自动化回归；UI 由人工验收。
