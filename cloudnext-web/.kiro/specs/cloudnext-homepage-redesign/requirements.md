# Requirements Document

## Introduction

基于设计稿对 CloudNext 企业云盘首页进行全面 UI 重构。重构范围包括：侧边栏扩展（App.vue）、顶部栏改造（App.vue）、首页主内容区重构（HomePage.vue）以及右侧面板新增（HomePage.vue）。当前阶段使用静态占位数据，后续接入真实 API。

## Glossary

- **CloudNext_Shell**: 应用外壳布局组件（App.vue），包含侧边栏、顶部栏和主内容区的整体框架
- **Sidebar**: 左侧固定侧边栏组件，宽度约 200px，包含品牌信息、导航菜单、快捷访问和存储空间指示
- **TopBar**: 顶部栏组件，包含搜索框、通知图标和用户信息
- **HomePage**: 首页主视图组件（HomePage.vue），包含欢迎横幅、统计指标、最近访问列表
- **Right_Panel**: 首页右侧面板，包含快速入口和通知列表
- **Welcome_Banner**: 首页顶部蓝色渐变欢迎卡片，展示问候语和统计指标
- **Recent_Files_Table**: 最近访问文件列表表格组件
- **Quick_Actions**: 右侧面板快速入口区域，包含上传文件、新建文件夹、创建共享、发起协作四个操作按钮
- **Notification_List**: 右侧面板通知列表组件

## Requirements

### Requirement 1: 侧边栏品牌与主操作

**User Story:** As a 企业用户, I want 在侧边栏顶部看到 CloudNext 品牌标识和新建按钮, so that 我能快速识别产品并创建新内容。

#### Acceptance Criteria

1. THE Sidebar SHALL display the brand logo (the text badge "CloudNext"), the title "企业云盘", and the subtitle "Enterprise Cloud Drive" at the top of the sidebar, rendered in top-to-bottom vertical order within the brand block
2. THE Sidebar SHALL display a blue rounded "＋ 新建" button immediately below the brand block, visually distinct from navigation links, with minimum touch target size of 36px height
3. WHEN the user clicks the "＋ 新建" button, THE Sidebar SHALL display a dropdown menu containing at least the options "新建文件夹" and "上传文件", with the menu appearing adjacent to the button within 200ms of the click event
4. IF the dropdown menu is open and the user clicks outside the menu area, THEN THE Sidebar SHALL close the dropdown menu and return the button to its default state

### Requirement 2: 侧边栏导航菜单

**User Story:** As a 企业用户, I want 通过侧边栏导航菜单访问各功能模块, so that 我能快速切换到不同的功能区域。

#### Acceptance Criteria

1. THE Sidebar SHALL display a navigation menu containing the following items in order: 首页, 我的文件, 共享文件, 协作空间, 外链管理, 回收站
2. WHEN the user clicks a navigation menu item, THE Sidebar SHALL highlight the active item with a distinct background color and text color
3. THE Sidebar SHALL display an icon to the left of each navigation menu item label

### Requirement 3: 侧边栏快捷访问

**User Story:** As a 企业用户, I want 在侧边栏看到快捷访问列表, so that 我能一键进入常用文件夹。

#### Acceptance Criteria

1. THE Sidebar SHALL display a "快捷访问" section below the navigation menu
2. THE Sidebar SHALL display a list of shortcut items, each with a name and an optional star icon indicating favorited status
3. THE Sidebar SHALL display the following placeholder shortcut items: "产品设计资料"（带星标）, "市场部资料"（带星标）, "2024年项目资料", "企业文化手册", "培训资料"

### Requirement 4: 侧边栏存储空间指示

**User Story:** As a 企业用户, I want 在侧边栏底部看到存储空间使用情况, so that 我能了解当前存储用量并管理空间。

#### Acceptance Criteria

1. THE Sidebar SHALL display a storage usage section at the bottom of the sidebar
2. THE Sidebar SHALL display a progress bar indicating the percentage of storage used
3. THE Sidebar SHALL display the storage usage text as "已使用 2.45 TB / 10 TB" with the percentage "24.5%" (static placeholder values)
4. THE Sidebar SHALL display a "管理存储" link below the progress bar

### Requirement 5: 顶部栏搜索与用户信息

**User Story:** As a 企业用户, I want 在顶部栏搜索文件并查看个人信息和通知, so that 我能快速定位文件并了解最新动态。

#### Acceptance Criteria

1. THE TopBar SHALL display a search input with placeholder text "搜索文件、文件夹或团队空间" and a keyboard shortcut hint "⌘K"
2. THE TopBar SHALL display a notification bell icon on the right side with a red badge showing the unread count (placeholder value: 12)
3. THE TopBar SHALL display the user avatar, name "张伟", and department "产品部" on the right side (static placeholder values)

### Requirement 6: 首页欢迎横幅

**User Story:** As a 企业用户, I want 在首页看到个性化的欢迎横幅和关键统计数据, so that 我能快速了解当前状态和重要指标。

#### Acceptance Criteria

1. THE Welcome_Banner SHALL display a card with a blue gradient background as the first visible content section at the top of the HomePage, positioned before any other content panels
2. THE Welcome_Banner SHALL display a time-based greeting on the left side using the following time ranges: 06:00–11:59 显示"上午好，{用户姓名}"，12:00–17:59 显示"下午好，{用户姓名}"，18:00–05:59 显示"晚上好，{用户姓名}"；greeting 下方显示日期字符串，格式为"欢迎回来！今天是 YYYY年M月D日 星期X"
3. IF 用户姓名不可用, THEN THE Welcome_Banner SHALL 在问候语中省略姓名，仅显示时段问候（如"下午好"），不显示逗号和姓名占位符
4. THE Welcome_Banner SHALL display four statistic cards on the right side with white background, each card showing a metric label, a primary value, and a secondary indicator: 存储空间（已用量 / 总量，百分比已使用，"管理"链接跳转至管理控制台页面）、文件数量（文件总数，环比变化百分比）、共享文件（共享文件数，环比变化百分比）、外链数量（外链总数，环比变化百分比）
5. THE Welcome_Banner SHALL use static placeholder values for all statistics, trend indicators shall display fixed demonstration values and not reflect real-time data

### Requirement 7: 最近访问文件列表

**User Story:** As a 企业用户, I want 在首页查看最近访问的文件并进行筛选, so that 我能快速找到并操作近期使用的文件。

#### Acceptance Criteria

1. THE Recent_Files_Table SHALL display a section title "最近访问" with filter tabs in the following fixed order: 全部, 我创建的, 我修改的, 共享给我的, with "全部" selected as the default active tab on initial page load
2. THE Recent_Files_Table SHALL display view toggle buttons (list view and grid view) with list view as the default active mode, and a "更多操作" dropdown on the right side of the header
3. THE Recent_Files_Table SHALL display a table with columns: 名称 (with file type icon and favorite star), 所有者 (with avatar and name), 修改时间, 大小, 操作 (context menu), showing a maximum of 20 rows sorted by 修改时间 descending (most recent first)
4. THE Recent_Files_Table SHALL display file type icons with distinct colors: folder (yellow), Word (blue W), Excel (green X), PPT (orange P), video (blue play icon), PDF (red)
5. WHEN the user clicks a filter tab, THE Recent_Files_Table SHALL highlight the active tab with a visually distinct style (background or underline) and filter the displayed files accordingly (placeholder behavior: visual toggle only, file list content does not change)
6. IF the file list for the currently active filter tab contains zero items, THEN THE Recent_Files_Table SHALL display an empty state placeholder with a text message indicating no recent files exist for the selected filter, centered within the table area
7. THE Recent_Files_Table SHALL display 修改时间 in relative format for files modified within the last 24 hours (e.g., "今天 09:30", "昨天 18:20") and in "YYYY-MM-DD HH:mm" format for files modified more than 24 hours ago, and SHALL display 大小 in human-readable units (B, KB, MB, GB) rounded to one decimal place

### Requirement 8: 右侧面板快速入口

**User Story:** As a 企业用户, I want 在右侧面板看到常用操作的快速入口, so that 我能一键执行上传、新建等高频操作。

#### Acceptance Criteria

1. THE Right_Panel SHALL display a "快速入口" section with a title
2. THE Quick_Actions SHALL display four circular icon buttons in a 2x2 grid layout: 上传文件, 新建文件夹, 创建共享, 发起协作
3. WHEN the user clicks a quick action button, THE Quick_Actions SHALL trigger the corresponding action (placeholder behavior for current phase)

### Requirement 9: 右侧面板通知列表

**User Story:** As a 企业用户, I want 在右侧面板查看最新通知, so that 我能及时了解文件共享、上传完成和协作邀请等动态。

#### Acceptance Criteria

1. THE Notification_List SHALL display a "通知" section title with a "全部已读" link on the right
2. THE Notification_List SHALL display a list of notification items, each containing: sender/event description, detail text, and timestamp
3. THE Notification_List SHALL display the following placeholder notifications: "李娜 共享了文件夹 '市场活动方案'" (今天 10:30), "文件上传完成：2024年宣传视频.mp4 等 3 个文件" (今天 09:15), "协作邀请：王强 邀请你加入协作 '产品优化需求文档'" (昨天 16:40)
4. THE Notification_List SHALL display a "查看全部通知" link at the bottom of the list

### Requirement 10: 首页整体布局结构

**User Story:** As a 企业用户, I want 首页采用三栏布局（侧边栏 + 主内容区 + 右侧面板）, so that 信息层次清晰、操作便捷。

#### Acceptance Criteria

1. THE CloudNext_Shell SHALL 使用 CSS Grid 将页面划分为左侧固定侧边栏（宽度为 var(--sidebar-width)，即 248px）和右侧主区域两列，侧边栏宽度在视口宽度大于 960px 时保持不变
2. THE HomePage SHALL 将主内容区域划分为左侧内容列（grid 比例不小于 1.3fr）和右侧面板列（最小宽度 280px，grid 比例不大于 0.7fr），两列之间间距为 var(--spacing-4)（20px）
3. THE HomePage SHALL 在主内容列中按从上到下顺序排列：Welcome_Banner 在最上方，Recent_Files_Table 在其下方
4. THE HomePage SHALL 在右侧面板列中按从上到下顺序排列：Quick_Actions 在上，Notification_List 在下，两个区域之间间距为 var(--spacing-4)（20px）
5. THE CloudNext_Shell SHALL 保持侧边栏和顶栏（cloudnext-topbar）在页面滚动时固定不动（position: fixed 或 sticky），主内容区域（cloudnext-content）作为整体可滚动区域
6. WHILE 视口宽度小于等于 1200px, THE HomePage SHALL 将主内容列和右侧面板列折叠为单列布局（grid-template-columns: 1fr），右侧面板内容排列在主内容列下方

### Requirement 11: 视觉风格与交互一致性

**User Story:** As a 企业用户, I want 界面视觉风格统一、交互流畅, so that 使用体验专业且舒适。

#### Acceptance Criteria

1. THE CloudNext_Shell SHALL use the existing CSS custom properties (design tokens) defined in style.css for colors, spacing, border-radius, and shadows
2. THE HomePage SHALL use consistent font sizes, weights, and colors across all sections following the established design system
3. WHEN the user hovers over interactive elements (buttons, links, table rows), THE HomePage SHALL display appropriate hover states with smooth transitions
4. THE CloudNext_Shell SHALL maintain responsive behavior, adapting the layout for smaller viewports by collapsing the right panel below the main content area
