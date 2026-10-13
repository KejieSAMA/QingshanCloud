# Implementation Plan: CloudNext 首页 UI 重构

## Overview

基于设计文档对 CloudNext 企业云盘首页进行全面 UI 重构。实现路径为：先搭建基础设施（工具函数、测试框架），再改造 App.vue 的侧边栏和顶部栏，然后完全重构 HomePage.vue，最后完善样式和响应式适配。所有数据使用静态占位值，技术栈为 Vue 3 (Composition API) + Vite。

## Tasks

- [x] 1. 基础设施：工具函数与测试框架配置
  - [x] 1.1 配置 Vitest 测试框架和 fast-check 属性测试库
    - 安装 vitest 和 fast-check 为 devDependencies
    - 在 vite.config.js 中添加 test 配置块
    - 创建 src/__tests__/ 目录结构
    - _Requirements: 11.1_

  - [x] 1.2 实现工具函数模块 src/utils/formatters.js
    - 实现 `getGreeting(hour, userName)` 函数：根据小时数返回时段问候语，支持用户姓名拼接
    - 实现 `formatFileSize(bytes)` 函数：将字节数转换为人类可读格式（B/KB/MB/GB），保留一位小数
    - 实现 `formatTime(timestamp, now)` 函数：24小时内显示相对时间（今天/昨天 HH:mm），超过24小时显示 YYYY-MM-DD HH:mm
    - 实现 `getDateString()` 函数：返回"欢迎回来！今天是 YYYY年M月D日 星期X"格式字符串
    - 对负数和非数字输入做防御性处理，返回 '--'
    - _Requirements: 6.2, 6.3, 7.7_

  - [ ]* 1.3 编写工具函数单元测试 src/__tests__/formatters.spec.js
    - 测试 getGreeting 边界值：hour=5→晚上好, hour=6→上午好, hour=11→上午好, hour=12→下午好, hour=17→下午好, hour=18→晚上好
    - 测试 getGreeting 用户名为空时省略逗号和姓名
    - 测试 formatFileSize：0→"0 B", 1023→"1023 B", 1024→"1.0 KB", 1048576→"1.0 MB"
    - 测试 formatTime：今天时间→"今天 HH:mm"，昨天→"昨天 HH:mm"，更早→"YYYY-MM-DD HH:mm"
    - _Requirements: 6.2, 6.3, 7.7_

  - [ ]* 1.4 编写属性测试：问候语时段正确性 src/__tests__/greeting.property.js
    - **Property 1: Greeting time range correctness**
    - 使用 fast-check 生成 0-23 的随机小时数和可选用户名
    - 验证 hour 6-11 返回"上午好"，12-17 返回"下午好"，18-23 和 0-5 返回"晚上好"
    - 验证有用户名时包含逗号+姓名，无用户名时不含逗号
    - **Validates: Requirements 6.2, 6.3**

  - [ ]* 1.5 编写属性测试：文件大小格式化正确性 src/__tests__/formatters.property.js
    - **Property 2: File size formatting correctness**
    - 使用 fast-check 生成 0 到 1TB 范围的随机字节数
    - 验证单位选择正确（B < 1024, KB < 1024², MB < 1024³, GB ≥ 1024³）
    - 验证数值精度在浮点容差范围内
    - **Validates: Requirements 7.7**

  - [ ]* 1.6 编写属性测试：时间显示格式选择正确性 src/__tests__/formatters.property.js
    - **Property 3: Time display format selection**
    - 使用 fast-check 生成 2024-2025 范围内的随机时间戳
    - 验证 24 小时内使用相对格式（今天/昨天 HH:mm），超过 24 小时使用绝对格式 YYYY-MM-DD HH:mm
    - **Validates: Requirements 7.7**

- [x] 2. Checkpoint - 确保基础设施就绪
  - Ensure all tests pass, ask the user if questions arise.

- [x] 3. App.vue 侧边栏改造
  - [x] 3.1 改造侧边栏品牌区和新建按钮
    - 修改品牌区 subtitle 为 "Enterprise Cloud Drive"
    - 将"上传文件"按钮替换为"＋ 新建"按钮
    - 实现下拉菜单（v-if 控制），包含"新建文件夹"和"上传文件"选项
    - 添加 showNewMenu ref 状态和 toggleNewMenu/closeNewMenu 方法
    - 实现点击外部关闭菜单逻辑（document click listener）
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

  - [x] 3.2 改造侧边栏导航菜单
    - 替换现有导航为完整菜单：首页、我的文件、共享文件、协作空间、外链管理、回收站
    - 每个菜单项添加图标（使用 Unicode 字符或 SVG）
    - 保持 RouterLink 的 active 高亮样式
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 3.3 新增侧边栏快捷访问区域
    - 在导航菜单下方添加"快捷访问"section
    - 显示 5 个快捷项：产品设计资料（星标）、市场部资料（星标）、2024年项目资料、企业文化手册、培训资料
    - 星标项显示 ⭐ 图标
    - _Requirements: 3.1, 3.2, 3.3_

  - [x] 3.4 新增侧边栏存储空间指示
    - 在侧边栏底部添加存储空间区域（替换现有 sidebar-session）
    - 实现进度条组件（.storage-bar + .storage-bar-fill）
    - 显示"已使用 2.45 TB / 10 TB (24.5%)"静态文本
    - 添加"管理存储"链接
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [x] 4. App.vue 顶部栏改造
  - [x] 4.1 重构顶部栏为搜索+通知+用户信息布局
    - 替换现有 topbar-copy 区域为搜索框（placeholder: "搜索文件、文件夹或团队空间"，快捷键提示 ⌘K）
    - 添加通知铃铛按钮，带红色未读数 badge（静态值 12）
    - 添加用户信息区域：头像（首字符"张"）、姓名"张伟"、部门"产品部"
    - 移除现有的"新建文件夹"按钮和页面标题/描述
    - _Requirements: 5.1, 5.2, 5.3_

- [x] 5. Checkpoint - 确保 App.vue 改造完成
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. HomePage.vue 完全重构
  - [x] 6.1 实现首页整体布局结构（homepage-grid）
    - 使用 CSS Grid 创建两列布局：左侧主内容列（≥1.3fr）+ 右侧面板列（min 280px, ≤0.7fr）
    - 设置列间距为 var(--spacing-4)
    - 添加 1200px 响应式断点折叠为单列
    - _Requirements: 10.2, 10.3, 10.4, 10.6_

  - [x] 6.2 实现欢迎横幅组件（Welcome Banner）
    - 创建蓝色渐变背景卡片
    - 左侧显示动态问候语（调用 getGreeting）和日期字符串（调用 getDateString）
    - 右侧显示 4 个统计卡片（存储空间、文件数量、共享文件、外链数量）
    - 统计卡片使用白色背景，显示指标名称、主值、趋势百分比
    - 使用静态占位数据
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

  - [x] 6.3 实现最近访问文件列表（Recent Files Table）
    - 实现 section 标题"最近访问"和筛选标签（全部/我创建的/我修改的/共享给我的）
    - 实现视图切换按钮（列表/网格），默认列表视图
    - 实现文件表格：名称（含文件类型图标+收藏星标）、所有者（头像+姓名）、修改时间、大小、操作列
    - 实现文件类型图标颜色映射：folder(黄)、word(蓝W)、excel(绿X)、ppt(橙P)、video(蓝播放)、pdf(红)
    - 实现筛选标签点击切换高亮（视觉切换，数据不变）
    - 实现空状态占位提示
    - 使用 formatFileSize 和 formatTime 格式化显示
    - 填充 6 条静态占位文件数据
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7_

  - [x] 6.4 实现右侧面板：快速入口
    - 创建"快速入口"section
    - 实现 2×2 网格布局的圆形图标按钮：上传文件、新建文件夹、创建共享、发起协作
    - 按钮点击为占位行为（console.log）
    - _Requirements: 8.1, 8.2, 8.3_

  - [x] 6.5 实现右侧面板：通知列表
    - 创建"通知"section，标题右侧显示"全部已读"链接
    - 显示 3 条静态通知：李娜共享文件夹（今天 10:30）、文件上传完成（今天 09:15）、协作邀请（昨天 16:40）
    - 底部显示"查看全部通知"链接
    - _Requirements: 9.1, 9.2, 9.3, 9.4_

- [x] 7. Checkpoint - 确保 HomePage 重构完成
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. 样式完善与响应式适配
  - [x] 8.1 在 style.css 中追加新组件样式
    - 添加欢迎横幅样式（.welcome-banner, .welcome-banner-left, .welcome-banner-stats, .welcome-stat-card）
    - 添加最近访问样式（.recent-files, .recent-files-header, .recent-files-filters, .recent-files-table, .file-type-icon）
    - 添加右侧面板样式（.homepage-side, .quick-actions-panel, .quick-actions-grid, .notifications-panel, .notification-item）
    - 添加侧边栏扩展样式（.sidebar-new-dropdown, .sidebar-shortcuts, .sidebar-shortcut-item, .sidebar-storage, .storage-bar, .storage-bar-fill）
    - 添加顶部栏改造样式（.search-shortcut, .topbar-notification, .notification-badge, .topbar-user, .topbar-avatar）
    - 所有样式复用现有 CSS 自定义属性（design tokens）
    - _Requirements: 11.1, 11.2_

  - [x] 8.2 实现响应式断点适配
    - 1200px 断点：homepage-grid 折叠为单列，统计卡片变为 2×2 网格
    - 960px 断点：cloudnext-shell 折叠为单列（侧边栏隐藏或折叠），欢迎横幅改为纵向排列
    - 确保交互元素 hover 状态有平滑过渡动画
    - _Requirements: 10.1, 10.5, 10.6, 11.3, 11.4_

- [x] 9. Final Checkpoint - 确保所有功能完成
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties defined in the design document
- Unit tests validate specific examples and edge cases
- 所有数据使用静态占位值，不依赖后端 API
- 样式复用 style.css 中已有的 CSS 自定义属性（design tokens）

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2"] },
    { "id": 2, "tasks": ["1.3", "1.4", "1.5", "1.6"] },
    { "id": 3, "tasks": ["3.1", "3.2", "3.3", "3.4", "4.1"] },
    { "id": 4, "tasks": ["6.1"] },
    { "id": 5, "tasks": ["6.2", "6.3", "6.4", "6.5"] },
    { "id": 6, "tasks": ["8.1"] },
    { "id": 7, "tasks": ["8.2"] }
  ]
}
```
