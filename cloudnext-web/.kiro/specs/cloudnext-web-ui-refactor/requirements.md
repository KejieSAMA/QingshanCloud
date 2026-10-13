# Requirements Document

## Introduction

CloudNext Web 是企业云盘的前端工作台，基于 Vue 3 + Vite 构建。当前页面中存在大量开发阶段的设计思想说明文字（如"当前阶段优先打通独立认证…"、"先用静态骨架承接…"等），这些文案面向开发者而非最终用户，需要全部清理并替换为正式产品文案或直接移除。同时，页面布局存在多层嵌套边框容器（box 套 box）和内容紧贴边框缺少合理 padding 的问题，需要统一重构布局结构和间距规范。

## Glossary

- **Page**: cloudnext-web 中 src/pages/ 目录下的 Vue 单文件组件，包括 HomePage.vue、AdminPage.vue、LoginPage.vue、WorkspacesPage.vue
- **Shell**: App.vue 中定义的应用外壳，包含侧边栏（sidebar）和顶栏（topbar）
- **Design_Copy**: 页面中面向开发者的设计思想说明文字，描述开发阶段、后续计划或架构意图，不适合展示给最终用户
- **Product_Copy**: 面向最终用户的正式产品文案，简洁描述功能用途或操作引导
- **Surface_Panel**: 页面中带有边框和圆角的内容容器组件（class 为 surface-panel、overview-side-card、workspace-card 等）
- **Spacing_Token**: style.css 中定义的 CSS 自定义属性间距变量（--spacing-1 到 --spacing-5）
- **Nested_Border**: 一个带边框的容器内部直接包含另一个带边框的子容器，形成视觉上的双重边框嵌套

## Requirements

### Requirement 1: 清理首页设计思想文案

**User Story:** As a 企业用户, I want 首页展示简洁专业的产品文案, so that 我能快速理解功能入口而不被开发阶段的内部说明干扰。

#### Acceptance Criteria

1. WHEN HomePage.vue 被渲染, THE Page SHALL 不包含以下任何 Design_Copy 文本："当前阶段优先打通独立认证、组织同步和企业文件入口，先把工作台骨架搭稳。"、"按企业文件、共享协作和管理任务组织首屏入口。"、"先用静态骨架承接未来最近访问与变更记录。"、"先聚焦认证、同步、入口页与管理台。"、"登录、首页、工作区、管理台已完成独立壳层。"、"后续接入真实访问记录与常用文件收藏。"、"后续补齐预览中心、全文检索与文件类型图标。"
2. WHEN HomePage.vue 被渲染, THE Page SHALL 不包含任何含有"当前阶段"、"后续接入"、"MVP"、"独立壳层"、"骨架"等内部开发术语的文本节点
3. IF "当前阶段"侧边卡片仍保留, THEN THE Page SHALL 将其标题和内容替换为存储用量统计信息（如已用空间、文件总数等可量化指标），且不包含任何开发阶段描述文本
4. IF "后续接入方向"侧边卡片仍保留, THEN THE Page SHALL 将其标题和内容替换为系统公告或功能快捷入口，且不包含任何以"后续"开头的规划性描述文本
5. WHEN HomePage.vue 被渲染, THE Page SHALL 保留欢迎语"你好，欢迎进入企业云盘。"或替换为包含用户姓名或组织名称的问候语，且问候语长度不超过30个字符

### Requirement 2: 清理管理台设计思想文案

**User Story:** As a 管理员, I want 管理台页面展示清晰的功能说明, so that 我能专注于系统状态监控而非阅读开发计划。

#### Acceptance Criteria

1. WHEN AdminPage.vue 被渲染, THE Page SHALL 不包含以下 Design_Copy 文本中的任何一条：（a）"继续沿用当前接口能力，用更紧凑的后台方式展示同步状态、配置情况与镜像统计。"（b）"用于快速感知独立云盘侧的组织镜像落地规模。"（c）"重点观察最近结果、计划周期和下一步动作。"（d）"把独立服务最关键的运行字段收敛到一个后台状态区。"
2. WHEN AdminPage.vue 的 page-head 区域被渲染, THE Page SHALL 在 `<h1>` 下方的 `<p>` 元素中展示一条不超过 30 个汉字的功能描述，该描述仅说明页面用途而不包含设计意图或实现计划
3. WHEN AdminPage.vue 的各 section-head 被渲染, THE Page SHALL 对每个 section-head 内的 `<p>` 元素执行以下二选一处理：替换为一条不超过 20 个汉字的面向用户的功能说明，或移除该 `<p>` 元素仅保留标题
4. IF AdminPage.vue 的任意 section-head 内 `<p>` 元素被保留, THEN THE Page SHALL 确保该文本不包含"沿用""收敛""感知""落地"等面向开发者的设计术语

### Requirement 3: 清理工作区页面设计思想文案

**User Story:** As a 企业用户, I want 工作区页面展示正式的产品引导文案, so that 我能清楚了解各空间的用途。

#### Acceptance Criteria

1. WHEN WorkspacesPage.vue 被渲染, THE Page SHALL 不包含以下 Design_Copy 文本中的任何一条：（a）"统一承接企业文件、共享工作区和部门空间，让用户更快找到协作入口。"（b）"先用与企业云盘相近的信息架构，把未来空间入口组织出来。"（c）"后续会接入空间角色与资源 ACL，让企业文件、工作区和部门空间有一致的权限体系。"（d）"后续可继续补齐最近、共享给我、我分享的与回收站等常用入口。"
2. WHEN WorkspacesPage.vue 底部卡片区被渲染, THE Page SHALL 移除包含"后续会接入"和"后续可继续补齐"文案的卡片区域整体
3. WHEN WorkspacesPage.vue 的页面头部被渲染, THE Page SHALL 在 `<h1>` 下方的 `<p>` 元素中展示一条不超过 20 个汉字的面向用户的功能描述，该描述不包含"承接""架构""入口"等开发术语

### Requirement 4: 清理应用外壳设计思想文案

**User Story:** As a 企业用户, I want 侧边栏展示简洁的品牌信息, so that 界面保持专业整洁。

#### Acceptance Criteria

1. WHEN Shell 侧边栏被渲染, THE Shell SHALL 不显示原 brand-subtitle 文本"独立会话、独立存储、复用 O2OA 组织身份"，改为显示长度不超过 12 个汉字的产品定位文案（如"企业级文件协作平台"），且该文案不得包含技术实现描述词汇（如"会话""存储""复用""身份"）
2. IF brand-subtitle 区域无合适的产品定位文案可填充, THEN THE Shell SHALL 移除该副标题元素，侧边栏品牌区域仅保留产品名称
3. WHEN Shell 侧边栏被渲染, THE Shell SHALL 不显示包含"即将""开放""开发""测试""Beta"等暗示未完成状态词汇的区域标题，原"即将开放" section-title 替换为仅描述功能分类的标题（如"更多功能"），长度不超过 6 个汉字
4. IF "更多功能"区域内无任何可用功能入口, THEN THE Shell SHALL 隐藏该区域整体，侧边栏不显示空的功能分区

### Requirement 5: 消除嵌套边框布局问题

**User Story:** As a 企业用户, I want 页面内容区域层次清晰无冗余边框, so that 视觉上不会感到拥挤和混乱。

#### Acceptance Criteria

1. THE Page SHALL 确保任何具有可见边框（border: 1px solid）的容器元素内部，不直接包含另一个同样具有可见边框的容器元素，即在 DOM 父子或祖孙关系中不出现两层可见边框相邻嵌套
2. IF Surface_Panel 内部需要对内容进行分组展示, THEN THE Page SHALL 使用无边框的分隔方式（间距不小于 12px、分割线、或背景色区分）替代嵌套边框容器，分组子项不得声明 border 属性或 border 值为 none/0
3. WHEN AdminPage.vue 的 admin-summary 区域被渲染, THE Page SHALL 确保 admin-metric 子项不具有可见边框线，改用背景色（如 var(--bg-panel-soft)）与外层 surface-panel 区分层级，admin-metric 与外层 surface-panel 之间仅存在一层可见边框
4. WHEN HomePage.vue 的 overview-table 被渲染, THE Page SHALL 确保 overview-table 容器不具有独立的外边框线，表格直接继承外层 surface-panel 的边框作为视觉边界，overview-table 自身的 border 值为 none 或 0
5. THE Page SHALL 确保在任意页面中，从最外层 surface-panel 到最内层可交互内容之间，可见边框层数不超过 1 层（即用户视觉上同一区域内最多感知到 1 条边框轮廓线）

### Requirement 6: 统一容器内边距规范

**User Story:** As a 企业用户, I want 页面内容与容器边框之间有舒适的间距, so that 文字不会紧贴边框导致阅读困难。

#### Acceptance Criteria

1. THE Surface_Panel SHALL 具有不小于 var(--spacing-3)（16px）的内边距（padding），确保内容与容器边框在上下左右四个方向均保持至少 16px 的距离
2. THE Page SHALL 确保所有带边框的容器（包括 surface-panel、page-card、overview-side-card、workspace-card、workspace-summary-card、overview-item、status-item、admin-metric、admin-summary、admin-status-panel）均具有不小于 var(--spacing-3)（16px）的内边距
3. WHILE 视口宽度小于 720px, THE Page SHALL 保持所有带边框容器的内边距不小于 var(--spacing-2)（12px），确保移动端内容不紧贴边框
4. THE Page SHALL 使用 style.css 中已定义的 Spacing_Token（--spacing-1 到 --spacing-5）作为间距值，不引入硬编码的像素值

### Requirement 7: 统一段落与标题间距

**User Story:** As a 企业用户, I want 页面中标题、描述文字和内容区之间有合理的视觉节奏, so that 信息层次分明易于浏览。

#### Acceptance Criteria

1. THE Page SHALL 确保 section-head（含标题和描述）与其后续内容区域之间有不小于 var(--spacing-3)（16px）的间距（通过 margin-bottom 或父容器 gap 实现均可）
2. THE Page SHALL 确保 page-head 与页面主体内容之间有不小于 var(--spacing-4)（20px）的间距（通过 margin-bottom 或父容器 gap 实现均可）
3. WHEN Surface_Panel 内同时包含 section-head 和内容列表时, THE Page SHALL 确保 section-head 与内容列表之间的间距不小于 var(--spacing-3)（16px），与 Surface_Panel 外部的 section-head 间距规则保持一致
4. THE Page SHALL 使用 style.css 中已定义的 Spacing_Token（--spacing-1 到 --spacing-5）设置标题与内容间距，不引入硬编码的像素值
