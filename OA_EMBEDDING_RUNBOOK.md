# OA 内嵌云盘与手机滚动

2026-10-31：配套源码位于本项目及 `C:\Users\17286\Desktop\form`。云盘 API／Web 已部署到 `D:\QingshanCloud\releases\oa-cloud-20261031-dcbb5c05295b`，原命名空间部署目录保留。

## 接口与会话

- `GET /api/auth/status` 增加 `embeddedProtocol: 1`、`embeddedOrigins`；来源依据现有 Form 免登地址及 `cloudnext.auth.embedded-origins` 配置生成，不接受客户端自行声明的可信来源。
- `/oa-embed?embed=1&channel=...&parentOrigin=...&entryId=...` 启动受控桥接。原生手机另带 `device=mobile`，横竖屏保持手机布局。
- `POST /api/auth/sso/embedded` 接收现有 `ticket、returnPath`，复用服务端单次兑换和会话校验，不设置 Bootstrap Cookie。独立云盘原有登录与 SSO 完成接口保留。
- 嵌入会话使用内存存储；父子通信校验 `oa-cloudnext / version 1`、来源、窗口和连接标识。退出 OA 销毁会话；无效或过期授权不显示密码表单，也不循环跳转。

## 文件操作

- `POST /api/drive/native-files/ticket`：现有云盘会话鉴权，接收 `itemIds、versionId?、action(OPEN/SAVE)、archive?、zipName?`。
- `GET /api/drive/native-files/metadata`：通过 `X-CloudNext-File-Ticket` 读取受限元信息。
- `GET / POST /api/drive/native-files/content`：原生端使用上述请求头；浏览器兼容页使用 POST 表单。响应为文件或 ZIP 流，不把大文件传入父页面或在 WebView 中整体编码。
- 随机票据限时五分钟，服务端内存有容量限制；绑定原会话、命名空间及内容快照。权限、账号、文件或版本变化后重新校验，服务重启后需重新发起文件操作。当前文件、历史版本和 ZIP 均复用原权限规则。
- OA Java 插件只使用构建配置的云盘来源和固定接口，拒绝重定向。原审批附件逻辑继续使用原来源和授权。

## 验证与发布

云盘 Web 执行 `npm test`：131 项 Vitest 和 14 项 Node 测试。Java 在隔离源码目录运行 `NativeFileTicketsTest、CloudNextSsoRecoveryTest`：23 项，均为模拟依赖，不连接生产库。Web 按 Chrome 83 目标构建，UI 和真机由人工验收。

本轮已完成云盘 API／Web 更新；OA 新 APK、OTA 和 Git 推送均未执行。原生能力需要后续构建 OA APK，并配置 `CLOUDNEXT_BASE_URL`；旧壳继续原浏览器路径。

部署沿用原私有环境，未执行 SQL、迁移或业务文件操作；私有配置、六个 MySQL 及十个其他 Java 进程不变。切换通过既有维护标记、监督进程停止信号及原有隐藏启动器完成。Nginx 只调整静态资源目录，保留旧资源，原版本及配置回退副本位于新版本的 `rollback/`。

操作记录、摘要及完整人工验收清单见 OA 项目的 `docs/releases/2026-10-31-oa-cloud-workspace.md`。回退同样只操作云盘：阻止新请求并等待在途结束，恢复该目录中的 Nginx／版本指针副本，再使用原启动器恢复；不调用历史初始化、迁移或清库脚本。

OnlyOffice 存在部署前已发生的连接超时，本轮未调整该独立服务；在线编辑需待其恢复后人工验收。

## 底部导航修正（2026-10-31，源码未部署）

OA 的右侧“OA／云盘”仅选择底栏分组，不改变正文、创建 iframe 或请求免登。OA 托管一条底栏：原五项与云盘的首页／文件／共享／协作四项互相切换；云盘自带底栏在嵌入态继续隐藏。正文不为右侧切换预留宽度。

在既有 `oa-cloudnext / version 1` 通信中增加可选导航能力，认证及文件接口不变：

- 子页 `ready` 携带 `navigationVersion: 1`、本次文档 `bootId`；`authenticated` 同样携带文档标识。
- 父页 `navigate-tab`：`requestId、bootId、tab`。tab 仅允许 `home、files、shared、workspaces`，映射到既有 Vue 路由，不接受任意 URL。
- 父页 `cancel-navigation`：取消指定 `requestId`；子页以 `navigation-result` 回报 `requestId、bootId、ok、cancelled?、message?`。
- 子页原有 `route` 消息增加 `bootId、revision`，修正后的父页只接受当前文档递增的路由状态。来源、窗口和连接标识仍沿用原校验。

子页串行处理导航，只保留最新请求；通过 Vue Router 原有守卫和最终取消检查，保持组织盘上下文。已处于所选栏目时不重新进入根目录；有编辑弹层时取消导航，保留原弹层与输入。

旧父页忽略新增字段仍可工作。新父页遇到旧云盘页面时显示明确错误，不静默丢弃导航；显式重试才刷新该子页。后续发布应先更新云盘 Web，再更新 OA Web／移动资源。无需更新云盘 API、迁移数据库或修改 Java 桥接，现有 vc38 足够；本次未构建、部署或发布制品。

## 2026-11-01 Web 部署

经用户后续授权，底部导航修正已部署为 `D:\QingshanCloud\releases\cloud-nav-web-20261101-ca1ab1915ff2`，主模块为 `/assets/main-BjoUvjQz.js`。210 个文件校验通过，保留 80 个旧资源；仅修改静态根目录并平滑重载 Nginx，没有重启 API。后端仍使用 `oa-cloud-20261031-dcbb5c05295b`，原版本指针不变，Web 版本另记在 `config/web-release.json`。

内网／公网 HTML 与主模块摘要、API 健康 UP 及原进程检查通过，数据库和其他 Java 服务未动。配套 OA Web 和手动安装 APK 1.1.16／vc39 已完成，未发布 OTA 或壳升级；OnlyOffice 保持原状。详细记录见 OA 项目 `docs/releases/2026-11-01-cloud-bottom-navigation-apk-vc39.md`。回退 Web 时恢复本次 `rollback/nginx.conf` 并校验、重载同一 Nginx，不切换后端或执行历史安装脚本。
