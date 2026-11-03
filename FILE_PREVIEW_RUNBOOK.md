# 云盘统一文件预览

2026-11-03：源码及隔离验证。本轮没有构建发布制品、部署、迁移、修改线上环境或停止 OnlyOffice。

## 功能与权限

- 个人文件、共享、空间、搜索、历史版本、外链共用 Vue 阅读器。Office 使用 LibreOffice 转 PDF；PDF 使用与 OA 相同的 PDF.js 5.4.296；图片支持拖动、缩放和旋转；文本以纯文本显示，支持 UTF-8、带 BOM 的 UTF-16 及 GB18030。
- 当前支持 DOC/DOCX、XLS/XLSX、PPT/PPTX、PDF、PNG/JPEG/GIF/BMP/WebP、TXT/MD/CSV/JSON/LOG。SVG、HTML 不作为可执行页面加载。
- Excel 依照工作簿打印布局，PPT 是静态幻灯片。不会改写原文件、公式或版本；不执行宏、不更新外部链接。没有在线编辑、批注编辑或保存回云盘的入口。
- 下载原件、系统打开使用原下载接口和原权限；仅预览不能借原生桥接导出原件。版本查看继续执行原版本权限门槛，不扩大为所有预览人员都能查看历史。
- OA 嵌入模式通过 `preview-state {bootId,revision,open}` 隐藏、恢复 OA 底栏及侧边切换。父页面核对 origin、source、连接标识、文档 bootId、账号与递增修订。独立云盘使用相同阅读器，不依赖 OA 审批服务或 Android 新接口。

## API 与缓存

- 原文件 `/api/drive/items/{id}/preview`、历史版本 `/api/drive/items/{id}/versions/{versionId}/preview`、外链 `/public/links/{token}/preview` 返回版本绑定的预览票据与文件信息。
- 新 `POST /api/previews` 幂等生成，显式失败重试使用 `?retry=true`；`GET /api/previews` 查询，`GET /api/previews/content` 分段读取。三者使用 `X-CloudNext-Preview`；普通会话同时沿用原账号和云盘请求头。
- 响应保留 `kind/supported/allowDownload/previewTicket/previewUrl/previewPageUrl`，增加 `status/phase/sourceIdentity/expiresAt/retryable/pageCount`。状态为 MISSING、PENDING、PROCESSING、READY、FAILED 或 UNSUPPORTED。首次请求进入真实排队/转换状态，不显示虚构百分比。
- 所有查询和读取都重新核对账号资格、认证版本、云盘、共享/空间权限、父目录、外链状态与提取码会话。历史版本使用实际版本的文件名、类型与内容。当前文件变化时旧票据返回冲突，不能悄悄换成新文件。
- 票据过期与 OA/云盘登录过期分开处理。正常入口会在有效期内续取预览票据；旧地址的短期票据过期后需要重新打开文件。
- Office 缓存键含云盘、文件、版本、存储身份、内容 SHA-256、转换器及字体版本。结果与清单原子落盘；单槽、有限队列；重启后已完成制品可复用，未完成任务由下一次授权请求重新建立。
- 缓存目录必须独立于原文件目录，带 `.cloudnext-preview-cache` 标记。仅清理该目录的派生 PDF、清单和过期工作目录，保留用户原文件。默认按最近使用时间保留 7 天，持久 PDF 与清单合计不超过 10 GiB，按小时及生成时清理。
- 生成失败和只读访问不会创建数据库任务、文件版本或迁移记录。预览入口沿用既有最近访问行为，验证不调用真实业务入口。

## 配置

通过现有私有环境设置以下变量，不把真实路径凭据写入 Git；路径在未来部署前只读核对。

| 变量 | 默认值／用途 |
| --- | --- |
| `CLOUDNEXT_PREVIEW_CACHE_ROOT` | `.data/preview-cache`，独立派生目录 |
| `CLOUDNEXT_PREVIEW_SOFFICE_PATH` | `soffice`；Windows 应填写已核对的 `soffice.com` 绝对路径 |
| `CLOUDNEXT_PREVIEW_PYTHON_PATH` | `python3`；Windows 应填写 LibreOffice 配套、可导入 UNO 的 `python.exe` 路径 |
| `CLOUDNEXT_PREVIEW_SIGNING_SECRET` | 独立预览签名密钥；未设置时仅兼容读取既有签名密钥，二者均未设置则使用进程内随机密钥 |
| `CLOUDNEXT_PREVIEW_CONVERTER_VERSION` | `libreoffice-pdf-v1`；转换规则变更时提高版本 |
| `CLOUDNEXT_PREVIEW_FONT_PACK_VERSION` | `cjk-v1`；字体包变化时提高版本 |
| `CLOUDNEXT_PREVIEW_MAX_SOURCE_BYTES` | 83886080（80 MiB） |
| `CLOUDNEXT_PREVIEW_MAX_PDF_BYTES` | 104857600（100 MiB） |
| `CLOUDNEXT_PREVIEW_MAX_PAGES` | 500，读取实际 PDF 校验，超限不截断 |
| `CLOUDNEXT_PREVIEW_TIMEOUT_MS` | 120000 |
| `CLOUDNEXT_PREVIEW_STARTUP_TIMEOUT_MS` | 60000，包含旧服务器的冷启动预算 |
| `CLOUDNEXT_PREVIEW_QUEUE_CAPACITY` | 100，转换并发固定为 1 |
| `CLOUDNEXT_PREVIEW_ISOLATE_PRESENTATIONS` | 默认 false；旧 Windows 的 UNO 演示文稿转换超时而 CLI 验证正常时设 true。PPT/PPTX 仍在同一串行槽内，停止本模块常驻进程后独立导出，不先等待一次常驻超时 |
| `CLOUDNEXT_PREVIEW_RETENTION_DAYS` | 7 |
| `CLOUDNEXT_PREVIEW_MAX_CACHE_BYTES` | 10737418240（10 GiB） |

常驻进程使用专属配置目录和命名管道，处理 50 个任务或运行 30 分钟后重建；基础设施故障允许一次独立冷转换，业务失败不自动重试。所有进程操作只针对本模块启动的进程。部署前应复核中文字体、UNO Python、私有目录权限和临时空间。

## 旧编辑能力与后续发布

- 编辑菜单、独立 OnlyOffice 页面、编辑器脚本、保存轮询、远程健康探测及回调写回代码已删除。旧 `/api/drive/items/{id}/edit` 和 `/api/onlyoffice/*` 返回 410 的停用提示，不再获取或写回文档。
- `/preview/office` 保留为新阅读器的旧路由；`/edit/office` 仅提示停用。旧签名票据仍必须通过当前授权核对。配置/状态响应中的旧字段保留为停用值，历史审计名称及版本、既有切换检查点不改写。
- **未来部署先收尾旧编辑会话。** 先禁止旧服务签发新的编辑票据，保留原进程处理已经受理的内容读取、保存请求与回调；由维护人员确认现有编辑窗口已关闭且保存完成后，再切换新 API。无法确认时不切断旧回调，不以新 API 的 410 替代保存成功。
- 在新的隔离目录构建 API 和 Web，不能从包含旧 `.class` 文件的目标目录打包。保持 MySQL、原文件存储、人员映射与共享配置，保留前版 API/Web 及私有配置以便回退；不执行历史安装/迁移脚本。
- 发布前在目标服务器用同一批合成夹具复核全部六种格式。此前目标服务器通过的 Word/Excel 记录不替代本次 PPT、新队列和读取接口验收。
- OA 父页面需要配套 Web 更新，App 内置 Web 需要后续授权 OTA 或手动包才会隐藏父底栏；无需新增原生桥接。此轮没有发布 OTA、APK、上传或推送代码。
- 独立 OnlyOffice 服务本轮没有停止、卸载或改配置；应用解除依赖不等于允许停止其他服务。

## 隔离验证与人工验收

Java `preview` 测试使用模拟授权与临时目录，不启动应用、不加载生产 env、不连接 MySQL。`LibreOfficeFormatsTest` 仅在显式设置 `preview.integration=true` 时运行，输入由 `cloudnext-server/tools/generate-preview-fixtures.py` 生成，不读业务文件。

运行前端 `npm test`、OA 前端无产物类型检查、移植兼容模块无产物类型检查，以及阅读器 SFC 脚本/模板静态检查。既有数据库集成测试已适配新协议，但本轮不运行其数据库初始化或迁移流程。

人工验收清单：

- 手机、桌面分别从个人、共享、空间、搜索、版本、外链打开，均为同一阅读器；长文件名不挤压关闭按钮。
- PDF 翻页、输入页码、缩放、旋转、适应页面；图片双指缩放、拖动、EXIF 方向；文本中文与长行。
- 内嵌 OA 时底栏和右侧切换暂时隐藏，关闭/系统返回后恢复原目录、页码、筛选与滚动；横竖屏和安全区正常。
- 仅预览不出现原件下载与系统打开；共享撤销、外链过期/提取码失效、账号切换后不能继续请求内容。
- 加载中快速切换文件、关闭、进入后台及恢复，不能显示上一份内容或重复弹错；失败重试不会清空文件列表状态。
- DOC/DOCX、XLS/XLSX、PPT/PPTX 的中文、长表格、跨页、多工作表及横向内容；加密、损坏、超限文件应给出一次明确错误。
- 旧预览地址进入新阅读器，旧编辑入口明确停用。覆盖旧 APK 的兼容返回和新版 OA Web 的全屏预览。

UI 与真机只由人工验收，不使用 Computer Use 或浏览器 UI 自动化。

## 2026-11-03 服务器部署

经用户另行授权，云盘 API／Web 已部署到 `cloud-preview-20261103-919d592bca3c`；没有执行数据库迁移、修改真实文件或停止独立 OnlyOffice。专用 LibreOffice 25.8.7 目录逐文件复制核对后，目标机六种合成 Office 文件及进程重建验证通过。

该旧 Windows 服务器启用 `CLOUDNEXT_PREVIEW_ISOLATE_PRESENTATIONS=true`：PPT／PPTX 使用同一串行槽内的独立导出，避免常驻 UNO 的超时等待，实测各约 19 秒。Word、Excel 保留常驻转换。转换模式纳入缓存键，不能复用另一模式的派生 PDF。最终 Java 隔离测试共 41 项通过。

服务健康 `UP`，内网／公网入口及阅读器、Worker、WASM 资源摘要核对通过；六个 MySQL、十个其他 Java 服务保持原进程。前版制品与私有配置回退副本保留。OA／APK／OTA 发布记录保存在 OA 项目的 `docs/releases/2026-11-03-cloud-preview-apk-ota.md`。
