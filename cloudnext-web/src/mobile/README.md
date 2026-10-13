# 手机端回归检查

## 文件页生命周期修复

旧 PC 菜单清理后，手机文件页遗留的 closeSelectionMoreMenu 引用会在挂载、目录变化和卸载时抛出 ReferenceError，影响后续页面切换。本次删除这三处引用和已无用途的全局点击监听，不恢复空函数，也不改底部导航的 RouterLink。

首页悬浮按钮跳转后的待执行任务现在保存句柄，页面卸载时取消；共享搜索的防抖任务和文件事件监听继续成对清理。

## 检查命令

在 cloudnext-web 目录运行：

```text
npm run check:mobile-scope
npm test -- --exclude "src/next/tests/**"
node --test src/next/tests/navigation.test.mjs src/next/tests/isolation.test.mjs
```

作用域检查覆盖手机页面、手机组件、共享 composable 和认证脚本；使用开发依赖 ESLint 的 no-undef 规则，不生成应用构建，不加载真实接口。

生命周期测试在 Node 中执行页面源代码中的实际挂载、卸载、watch 和悬浮按钮回调，业务及请求函数使用模拟对象；内存路由验证连续页面切换，不渲染浏览器 UI。

本次结果：39 个脚本零作用域错误，108 项单元测试通过，14 项静态/导航检查通过。

## 人工验收

- 首页 → 文件 → 文件夹 → 首页 → 共享 → 协作连续切换，无需每次刷新。
- 直接打开首页、文件页或带 parentId 的文件页，能正常离开。
- 首页悬浮按钮进入文件页后，上传/新建动作只执行一次；快速离开不在其他页面触发。
- 文件点击、预览、上传和底部导航实际交互由人工验收，不使用 Computer Use。

本次没有编译、重启、推送或数据库变更；此前的面包屑与预览样式修改保留。
