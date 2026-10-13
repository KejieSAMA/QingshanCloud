# 青山云盘桌面设计约束

## Color
默认商务蓝 #3264d8，浅绿 #28634c 仍可选。保留已保存主题；文件类型色、警告色和权限语义色不随主色改变。

## Typography
沿用 V3.1 字体栈、字号、行高及名称/扩展名层级，不替换字体。

## Layout
侧栏 236px、顶栏 70px、横向间距 30px、详情栏 326px、紧凑行上下 8px；table-scroll 负责主区滚动，表头吸顶，工具栏和分页固定。560px 以上沿用 Demo 响应式布局。

## Components
沿用原 DOM、SVG、输入框、按钮、表格、菜单与弹窗规则。桌面登录采用品牌介绍和登录表单双栏，用同一蓝色变量与控件；手机登录保持原样。

## Isolation
原始 reference/demo-v3.1.html 不改动。运行 CSS 只增加 desktop/mobile 场景作用域；应用级 desktop-overlays 承载跨布局对话框与预览。禁止桌面重新导入旧 PC 样式或手机 UI 组件。

## Interaction
单应用 History 路由，登录后无跨 HTML 跳转；无版本切换。保留键盘操作、Esc、焦点归还、主题预览与取消。上传、冲突提示和编辑预览不因尺寸切换销毁。

## Boundaries
不加入示例数据、假预览、模拟成功或原生桌面业务 prompt。新建文档仍禁用。人工验收视觉，静态检查验证 CSS 声明与原始 Demo 一致，明确例外：按用户要求移除桌面 .preview-body 的 padding:32px；原始参考副本不修改。
