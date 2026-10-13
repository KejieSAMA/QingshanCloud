# Implementation Plan: CloudNext Web UI Refactor

## Overview

本实现计划将 UI 重构拆分为两个阶段：第一阶段并行清理四个文件中的设计思想文案（Requirement 1-4），第二阶段在文案清理完成后统一修改 style.css 中的布局与间距规范（Requirement 5-7）。所有修改均在现有文件中就地完成，不引入新组件或新文件。

## Tasks

- [x] 1. 文案清理 — 并行修改各页面组件
  - [x] 1.1 清理 HomePage.vue 设计思想文案
    - 移除 `page-head` 中的 `<p>` 元素（"当前阶段优先打通独立认证…"）
    - 将快捷入口 section-head 的 `<p>` 替换为"快速访问常用功能区域。"
    - 将最近访问 section-head 的 `<p>` 替换为"查看近期打开和编辑的文件。"
    - 将侧边"当前阶段"卡片重构为"存储概览"卡片：标题改为"存储用量"，内容改为"已用空间"和"文件总数"两个指标行，移除所有 Design_Copy 描述文本
    - 将侧边"后续接入方向"卡片重构为"系统公告"卡片：标题改为"最新动态"，内容改为"收藏夹"（标记常用文件，快速定位。）和"全文搜索"（按关键词检索文件内容。）
    - 保留欢迎语"你好，欢迎进入企业云盘。"
    - 添加 `storageUsed` 和 `fileCount` 的静态占位 ref 变量（如 "2.4 GB" 和 1234）
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

  - [x] 1.2 清理 AdminPage.vue 设计思想文案
    - 将 `page-head` 中的 `<p>` 替换为"查看组织同步状态与系统配置。"
    - 将"组织镜像统计" section-head 的 `<p>` 替换为"当前已同步的组织数据统计。"
    - 移除"当前节奏" section-head 的 `<p>` 元素（"重点观察最近结果…"）
    - 将"配置与连通性" section-head 的 `<p>` 替换为"系统运行参数与连接状态。"
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

  - [x] 1.3 清理 WorkspacesPage.vue 设计思想文案
    - 将 `page-head` 中的 `<p>` 替换为"管理和浏览所有企业空间。"
    - 移除"空间导航" section-head 的 `<p>` 元素（"先用与企业云盘相近的信息架构…"）
    - 整体移除底部 `<div class="overview-card-grid">` 区域（包含"空间权限"和"协作入口"两张卡片）
    - _Requirements: 3.1, 3.2, 3.3_

  - [x] 1.4 清理 App.vue 应用外壳设计思想文案
    - 将 `.brand-subtitle` 文本替换为"企业级文件协作平台"
    - 将第二个 `.sidebar-section-title`（"即将开放"）替换为"更多功能"
    - 为"更多功能"区域添加 `v-if` 条件判断：当内部链接均不可用时隐藏整个 section（当前保留显示，因为有最近/共享/回收站链接）
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [x] 2. Checkpoint — 文案清理验证
  - 确保所有 .vue 文件中不再包含"当前阶段""后续接入""MVP""独立壳层""骨架""沿用""收敛""感知""落地""承接""架构"等开发术语
  - 运行 `npm run build` 确认构建无错误
  - Ensure all tests pass, ask the user if questions arise.

- [x] 3. 布局重构 — 消除嵌套边框
  - [x] 3.1 修改 style.css 消除嵌套边框
    - 移除 `.admin-summary` 的 border，设为 `border: none; background: transparent;`
    - 移除 `.admin-metric` 的 border，改用 `background: var(--bg-panel-soft); border: none;`
    - 移除 `.overview-table` 的 border 和 border-radius，设为 `border: none; border-radius: 0;`
    - 移除 `.overview-table-row` 的 border 和 border-radius（保留 `border-top` 分割线通过 `+` 选择器）
    - 移除 `.overview-item` 的 border，改用 `background: var(--bg-panel-soft); border: none;`
    - 将 `.quick-link` 的 border 改为 `border: 1px solid transparent;`，hover 时显示 `border-color: var(--border-color);`
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

- [x] 4. 布局重构 — 统一容器内边距
  - [x] 4.1 修改 style.css 统一容器 padding
    - 为 `.surface-panel, .page-card, .overview-side-card, .workspace-card, .workspace-summary-card, .status-item, .admin-status-panel` 设置 `padding: var(--spacing-5);`
    - 为 `.overview-item, .admin-metric, .quick-link` 设置 `padding: var(--spacing-4);`
    - 在 `@media (max-width: 720px)` 断点中将上述所有容器 padding 调整为 `var(--spacing-3);`（≥ 12px）
    - 确保不引入硬编码像素值，全部使用 spacing token
    - _Requirements: 6.1, 6.2, 6.3, 6.4_

  - [x] 4.2 修改 style.css 统一标题与内容间距
    - 为 `.section-head` 添加 `margin-bottom: var(--spacing-3);`
    - 为 `.page-head` 添加 `margin-bottom: var(--spacing-4);`
    - 为 `.surface-panel` 添加 `display: grid; gap: var(--spacing-3);`
    - 确保 surface-panel 内 section-head 与内容列表间距 ≥ 16px
    - 确保不引入硬编码像素值，全部使用 spacing token
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

- [x] 5. Final checkpoint — 构建验证与回归检查
  - 运行 `npm run build` 确认 Vite 构建无错误无警告
  - 使用 grep 扫描确认所有 .vue 文件中无禁止出现的开发术语
  - 审查 style.css 确认无硬编码像素值用于间距、所有带边框容器 padding 使用 spacing token
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- 所有修改在现有文件中就地完成，不引入新组件或新文件
- 文案清理任务（1.1-1.4）互相独立，可并行执行
- 布局修改任务（3.1, 4.1, 4.2）依赖文案清理完成后进行，避免模板结构冲突
- HomePage 侧边卡片中的存储数据当前使用静态占位值，后续迭代接入真实 API
- 设计文档无 Correctness Properties 部分（纯 UI/CSS 重构），不添加属性基测试任务
- 间距值全部使用 style.css 中已定义的 --spacing-1 到 --spacing-5

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3", "1.4"] },
    { "id": 1, "tasks": ["3.1"] },
    { "id": 2, "tasks": ["4.1", "4.2"] }
  ]
}
```
