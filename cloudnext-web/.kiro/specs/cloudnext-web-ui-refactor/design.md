# Design Document: CloudNext Web UI Refactor

## Overview

本设计文档描述 cloudnext-web 前端工作台的 UI 重构方案，覆盖三个核心目标：

1. **文案清理与替换** — 移除所有面向开发者的设计思想说明文字（Design_Copy），替换为面向最终用户的正式产品文案（Product_Copy）
2. **嵌套边框消除** — 重构 DOM 结构和 CSS，确保任何区域内可见边框层数不超过 1 层
3. **间距规范统一** — 使用已定义的 Spacing Token 统一容器内边距和标题/内容间距

技术栈：Vue 3 + Vite + Vue Router，无 UI 组件库依赖，样式通过全局 `style.css` 管理。

## Architecture

### 重构范围与文件映射

```
src/
├── App.vue                  ← Requirement 4: 侧边栏文案清理
├── style.css                ← Requirement 5/6/7: 布局与间距重构
└── pages/
    ├── HomePage.vue         ← Requirement 1: 首页文案清理
    ├── AdminPage.vue        ← Requirement 2: 管理台文案清理
    ├── WorkspacesPage.vue   ← Requirement 3: 工作区文案清理
    └── LoginPage.vue        ← 不在本次重构范围内
```

### 设计原则

- **就地修改**：不引入新组件或新文件，直接在现有 Vue SFC 和 CSS 中完成修改
- **Token 驱动**：所有间距值使用 `style.css` 中已定义的 `--spacing-1` 到 `--spacing-5`，不引入硬编码像素值
- **单层边框**：任何视觉区域内从外层容器到内容之间最多 1 层可见边框
- **渐进式**：文案替换和布局修改可独立进行，互不阻塞

## Components and Interfaces

### 文案替换策略

#### App.vue（应用外壳）

| 位置 | 当前文案 | 替换方案 |
|------|---------|---------|
| `.brand-subtitle` | "独立会话、独立存储、复用 O2OA 组织身份" | "企业级文件协作平台"（≤12 汉字） |
| `.sidebar-section-title`（第二个） | "即将开放" | "更多功能" |
| 更多功能区域 | 最近/共享/回收站链接 | 保留链接但移除"即将开放"暗示；若无可用功能则隐藏整个 section |

实现方式：
- `brand-subtitle` 直接替换静态文本
- "即将开放" section 添加 `v-if` 条件判断，当内部链接均不可用时隐藏整个区域

#### HomePage.vue（首页）

| 位置 | 当前文案 | 替换方案 |
|------|---------|---------|
| `page-head <p>` | "当前阶段优先打通独立认证…" | 移除该 `<p>` 元素 |
| 快捷入口 section `<p>` | "按企业文件、共享协作和管理任务组织首屏入口。" | "快速访问常用功能区域。" |
| 最近访问 section `<p>` | "先用静态骨架承接未来最近访问与变更记录。" | "查看近期打开和编辑的文件。" |
| 侧边"当前阶段"卡片 | MVP 范围 / 核心页面 + 开发描述 | 替换为"存储概览"：已用空间、文件总数 |
| 侧边"后续接入方向"卡片 | 最近/收藏 + 预览/搜索 + 开发描述 | 替换为"系统公告"或"功能快捷入口" |
| 欢迎语 | "你好，欢迎进入企业云盘。" | 保留（符合 ≤30 字符要求） |

侧边卡片重构细节：

```html
<!-- 存储概览卡片（替换"当前阶段"） -->
<section class="overview-side-card">
  <div class="section-head-copy">
    <span class="section-kicker">空间概览</span>
    <h2>存储用量</h2>
  </div>
  <div class="metric-strip">
    <div class="metric-row">
      <div class="metric-copy">
        <strong>已用空间</strong>
      </div>
      <span class="metric-value">{{ storageUsed }}</span>
    </div>
    <div class="metric-row">
      <div class="metric-copy">
        <strong>文件总数</strong>
      </div>
      <span class="metric-value">{{ fileCount }}</span>
    </div>
  </div>
</section>

<!-- 系统公告卡片（替换"后续接入方向"） -->
<section class="overview-side-card">
  <div class="section-head-copy">
    <span class="section-kicker">系统公告</span>
    <h2>最新动态</h2>
  </div>
  <div class="overview-card-grid">
    <div class="overview-item">
      <strong>收藏夹</strong>
      <p>标记常用文件，快速定位。</p>
    </div>
    <div class="overview-item">
      <strong>全文搜索</strong>
      <p>按关键词检索文件内容。</p>
    </div>
  </div>
</section>
```

#### AdminPage.vue（管理台）

| 位置 | 当前文案 | 替换方案 |
|------|---------|---------|
| `page-head <p>` | "继续沿用当前接口能力…" | "查看组织同步状态与系统配置。"（≤30 汉字） |
| 组织镜像统计 `<p>` | "用于快速感知独立云盘侧的组织镜像落地规模。" | "当前已同步的组织数据统计。" |
| 当前节奏 `<p>` | "重点观察最近结果、计划周期和下一步动作。" | 移除该 `<p>` 元素 |
| 配置与连通性 `<p>` | "把独立服务最关键的运行字段收敛到一个后台状态区。" | "系统运行参数与连接状态。" |

#### WorkspacesPage.vue（工作区）

| 位置 | 当前文案 | 替换方案 |
|------|---------|---------|
| `page-head <p>` | "统一承接企业文件…" | "管理和浏览所有企业空间。"（≤20 汉字） |
| 空间导航 `<p>` | "先用与企业云盘相近的信息架构…" | 移除该 `<p>` 元素 |
| 底部 `.overview-card-grid` | 两张含"后续会接入""后续可继续补齐"的卡片 | 整体移除该 `<div class="overview-card-grid">` 区域 |

### 布局重构策略

#### 嵌套边框消除方案

当前问题分析：

```
style.css 中以下选择器共享同一条 border 声明：
.page-card, .surface-panel, .workspace-list, .workspace-summary-card,
.workspaces-sidebar-panel, .stats-card, .status-item, .admin-summary,
.admin-status-panel, .auth-capability-card, .workspace-card,
.overview-item, .overview-side-card, .quick-link, .overview-table,
.overview-table-row, .workspace-row, .workspace-filter
```

嵌套边框出现的典型场景：

1. **AdminPage**: `.admin-summary`（有边框）→ `.surface-panel`（有边框）→ `.admin-metric`（有边框）= 3 层
2. **HomePage**: `.surface-panel`（有边框）→ `.overview-table`（有边框）→ `.overview-table-row`（有边框）= 3 层
3. **HomePage**: `.surface-panel`（有边框）→ `.quick-link`（有边框）= 2 层
4. **HomePage**: `.overview-side-card`（有边框）→ `.overview-item`（有边框）= 2 层

修复策略：

| 场景 | 修复方案 |
|------|---------|
| `.admin-summary` 包含 `.surface-panel` | 移除 `.admin-summary` 的 border，仅保留 padding 和 gap |
| `.admin-metric` 在 `.surface-panel` 内 | 移除 `.admin-metric` 的 border，改用 `background: var(--bg-panel-soft)` 区分 |
| `.overview-table` 在 `.surface-panel` 内 | 移除 `.overview-table` 的 border 和 border-radius |
| `.overview-table-row` 在 `.overview-table` 内 | 移除 `.overview-table-row` 的 border 和 border-radius，保留 `border-top` 分割线 |
| `.quick-link` 在 `.surface-panel` 内 | 保留 `.quick-link` 的 border（它是独立交互卡片），移除外层 `.surface-panel` 的 border |
| `.overview-item` 在 `.overview-side-card` 内 | 移除 `.overview-item` 的 border，改用背景色区分 |

CSS 修改方案：

```css
/* 1. admin-summary 不再作为边框容器 */
.admin-summary {
  border: none;
  background: transparent;
}

/* 2. admin-metric 用背景色替代边框 */
.admin-metric {
  border: none;
  background: var(--bg-panel-soft);
  border-radius: var(--radius-md);
}

/* 3. overview-table 继承外层边框 */
.overview-table {
  border: none;
  border-radius: 0;
}

/* 4. overview-table-row 仅保留分割线 */
.overview-table-row {
  border: none;
  border-radius: 0;
}

/* 5. overview-item 用背景色替代边框 */
.overview-item {
  border: none;
  background: var(--bg-panel-soft);
}
```

#### 快捷入口区域特殊处理

`.quick-link` 是独立的可交互按钮卡片，保留其边框。但需确保其外层容器不再有边框：

```css
/* quick-link 所在的 surface-panel 改为无边框容器 */
/* 方案：不改 surface-panel 全局样式，而是在 HomePage 中将快捷入口区域的外层改为无边框 div */
```

实现方式：在 HomePage.vue 中，将包含 `.quick-link-grid` 的 `<section class="surface-panel">` 保留边框（因为 quick-link 是其内部的交互元素，视觉上 surface-panel 边框 = 区域边界，quick-link 边框 = 交互提示）。这属于"同一区域内 1 层边框"的例外——quick-link 的边框是交互反馈而非容器嵌套。但为严格满足需求 5.5（最多 1 层边框），将 quick-link 的 border 改为 hover 时才显示：

```css
.quick-link {
  border: 1px solid transparent;
  transition: border-color 0.2s ease, background 0.2s ease;
}

.quick-link:hover {
  border-color: var(--border-color);
  background: var(--bg-panel-soft);
}
```

### 间距规范方案

#### 容器内边距统一（Requirement 6）

```css
/* 确保所有带边框容器 padding ≥ var(--spacing-3) */
.surface-panel,
.page-card,
.overview-side-card,
.workspace-card,
.workspace-summary-card,
.status-item,
.admin-status-panel {
  padding: var(--spacing-5); /* 24px - 舒适的默认内边距 */
}

.overview-item,
.admin-metric,
.quick-link {
  padding: var(--spacing-4); /* 20px */
}

/* 移动端 ≤720px 保持 ≥ var(--spacing-2) */
@media (max-width: 720px) {
  .surface-panel,
  .page-card,
  .overview-side-card,
  .workspace-card,
  .workspace-summary-card,
  .status-item,
  .admin-status-panel,
  .overview-item,
  .admin-metric,
  .quick-link {
    padding: var(--spacing-3); /* 16px，仍 ≥ 12px */
  }
}
```

#### 标题与内容间距统一（Requirement 7）

```css
/* section-head 与后续内容间距 ≥ var(--spacing-3) */
.section-head {
  margin-bottom: var(--spacing-3); /* 16px */
}

/* page-head 与主体内容间距 ≥ var(--spacing-4) */
.page-head {
  margin-bottom: var(--spacing-4); /* 20px */
}

/* surface-panel 内部使用 gap 控制间距 */
.surface-panel {
  display: grid;
  gap: var(--spacing-3); /* 16px */
}
```

## Data Models

本次重构不涉及数据模型变更。所有修改限于模板文案和 CSS 样式。

HomePage 侧边卡片替换为"存储概览"后，需要从 API 获取以下数据（可在后续迭代中接入，当前使用占位值）：

```typescript
interface StorageOverview {
  storageUsed: string;   // 如 "2.4 GB"
  fileCount: number;     // 如 1,234
}
```

当前阶段使用静态占位值，不阻塞本次 UI 重构。

## Error Handling

本次重构为纯前端 UI 调整，不涉及新的错误场景。需注意：

1. **文案替换后的空状态**：若移除某个 `<p>` 元素后导致 section-head 内仅剩标题，确保 CSS gap 不会产生多余空白
2. **条件渲染**：App.vue 中"更多功能"区域使用 `v-if` 隐藏时，确保侧边栏布局不塌陷
3. **响应式兼容**：间距修改后需验证 720px / 960px / 1200px 三个断点下布局不溢出

## Testing Strategy

### 为什么不使用属性基测试（PBT）

本次重构属于 **UI 渲染和 CSS 布局** 范畴：
- 文案替换是静态文本修改，无输入/输出变换逻辑
- 边框和间距修改是 CSS 声明式样式，无可量化的函数行为
- 不存在可以用"对所有输入 X，性质 P(X) 成立"表述的通用属性

因此跳过 Correctness Properties 部分，采用以下测试策略。

### 测试方案

#### 1. 手动视觉验证（主要）

- 逐页检查四个页面在 1440px / 960px / 720px / 375px 宽度下的渲染效果
- 确认无 Design_Copy 文本残留
- 确认无嵌套边框出现
- 确认内容与容器边框间距 ≥ 16px（移动端 ≥ 12px）

#### 2. 文案回归检查（自动化）

编写简单的文本搜索脚本，扫描所有 `.vue` 文件确认以下关键词不出现在模板中：

```bash
# 禁止出现的开发术语
grep -rn "当前阶段\|后续接入\|MVP\|独立壳层\|骨架\|沿用\|收敛\|感知\|落地\|承接\|架构" src/pages/ src/App.vue
```

预期结果：无匹配输出。

#### 3. 构建验证

```bash
npm run build
```

确认 Vite 构建无错误、无警告。

#### 4. CSS 规则审计

手动审查 `style.css` 确认：
- 所有带边框容器的 padding 值使用 spacing token
- 无硬编码像素值用于间距
- `.admin-metric`、`.overview-item`、`.overview-table`、`.overview-table-row` 的 border 已移除或设为 none

