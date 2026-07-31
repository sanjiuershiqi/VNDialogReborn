# 编辑器 UI 完善改进（第六轮）

## 摘要

延续前五轮改造，本轮聚焦**三项稳定优先的混合小改**，对照 Sparkle-Morpher 的设计令牌体系与 VNDialog 短板分析，修复可见的视觉/体验缺陷。范围严格限定，不碰大架构（PortraitList 硬编码收编、模糊体系、图标图集留后续）：

1. **OptionEditScreen 迁移 PageLayout**：当前手写双套 y 游标（init 用 `y+10`、render 用 `y`），已导致标签与输入框垂直错位。是唯一未迁移 PageLayout 的表单屏。迁移后统一游标，消除错位。
2. **焦点可见性**：DropdownWidget 聚焦/未聚焦外观一致，原生 EditBox 在暗色主题下仅靠光标闪烁，键盘/高缩放用户难辨焦点落点。补聚焦描边。
3. **i18n 收尾**：`"None"`（6 处）、`"X"`（3 处）、`"Dropdown"`（1 处）硬编码未走 i18n，非中文 locale 下显示英文/符号，与已 i18n 的界面割裂。

**不做**：PortraitListScreen 硬编码收编（改动面大、风险高）、图标图集系统化（需制作 PNG 素材）、模糊体系、属性页 Tab hover（独立小改可后续）、状态栏长消息处理（独立小改可后续）。

## 当前状态分析（差距定位）

### 差距 1：OptionEditScreen 双套游标错位

[OptionEditScreen.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/OptionEditScreen.java) 是唯一未迁移 PageLayout 的表单屏。init() 与 render() 各维护一套 y 游标，且偏移不一致：

- init() L64：`textBox` 在 `y+10`（y=25 → 35）
- render() L177：标签画在 `y`（25），`y += 36` 后下一个标签画在 61
- init() L71：`targetNodeBtn` 在 `y+10`（y=45 → 55）
- render() L179：第二个标签画在 61

结果：标签 y=25/61，输入框 y=35/55，标签与输入框垂直中心不对齐（标签在输入框上方 10px 而非对齐中心）。这是"序列属性标签画进输入框"同类的错位缺陷。

对比 [SequencePropertiesScreen](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/SequencePropertiesScreen.java) 已迁移 PageLayout（第四轮），`customRow` 返回行顶 Y，标签与输入框共用 Y 但输入框 +12 偏移到标签下方，无错位。

### 差距 2：焦点可见性缺失

- [DropdownWidget.java L133-150](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/widget/DropdownWidget.java#L133-L150)：renderWidget 仅 `isHovered()` 时 `BG_HOVER`，无 `isFocused()` 判定。键盘 Tab 聚焦到下拉框时无任何视觉提示，与 EditorButton（L61 聚焦 ACCENT 描边）、MultiLineEditBox（L264 聚焦 ACCENT 描边）不一致。
- 原生 `EditBox`：MC 默认聚焦时仅边框色微变 + 光标闪烁，在 VNDialog 暗色主题下（背景深色、边框 `0xFFE0E0E0` 亮色）对比度不足，高缩放/低视力用户难辨。本工程未对 EditBox 做聚焦描边包装。

对比 Sparkle：`RangedSliderWidget` 用 `canChangeValue` + `setFocused` 联动视觉；`OptionRow` 聚焦时整体 ACCENT 描边。VNDialog 借鉴"聚焦即描边"统一模式。

### 差距 3：i18n 硬编码残留

| 文件 | 行号 | 硬编码 | 应 i18n key |
|------|------|--------|------------|
| OptionEditScreen.java | 68, 134 | `"None"` | `gui.vn_edit.none` |
| LogicPropertyPage.java | 96, 196, 214, 545 | `"None"` | `gui.vn_edit.none` |
| OptionEditScreen.java | 146 | `"X"`（删除命令） | `gui.vn_edit.delete` 或图标 `✕` |
| LogicPropertyPage.java | 328, 493 | `"X"`（删除命令/项） | 同上 |
| DropdownWidget.java | 250 | `"Dropdown"`（narration） | `gui.vn_edit.dropdown` |

`"None"` 表示"无目标节点"，非中文 locale 下显示英文 None 与已汉化界面割裂。`"X"` 删除按钮用字母 X，与树控件 `⊗`（U+2297）风格不一致，应统一为 `✕`（U+2715）并可选 i18n。`"Dropdown"` 是读屏硬编码英文。

## 借鉴的 Sparkle-Morpher 模式

- **PageLayout 统一游标**：SequencePropertiesScreen 已验证的 `customRow` + 标签/输入框共用 Y + 输入框偏移模式，直接套用到 OptionEditScreen。
- **聚焦描边统一**：EditorButton/MultiLineEditBox 已有的 ACCENT 1px 描边模式，扩展到 DropdownWidget 和 EditBox 包装。
- **i18n 收尾**：Sparkle 所有用户可见文字走 `Component.translatable`，VNDialog 收编残留硬编码。

## 改造项（3 项）

### 项 1：OptionEditScreen 迁移 PageLayout

**目标**：消除双套游标错位，统一用 PageLayout 推进，标签与输入框垂直对齐。

**文件**：[OptionEditScreen.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/OptionEditScreen.java)

**改动**：
- 新增 `PageLayout layout` 字段，init() 开头创建 `new PageLayout(fieldX, 25, fieldWidth)`。注意 OptionEditScreen 是居中表单，fieldX/fieldWidth 需自定义（不沿用 PageLayout 的 fieldX/fieldWidth，因其基于 LABEL_WIDTH）。改为：`int fieldX = (this.width - 200) / 2;` `PageLayout layout = new PageLayout(fieldX, 25, 200)`，但 PageLayout 的 fieldX() 会加 LABEL_WIDTH 偏移，不适合居中表单。
- **决策**：OptionEditScreen 无标签列（标签在输入框上方），不适合 PageLayout 的"标签+字段"双列模型。采用**简化方案**：不引入 PageLayout，而是消除双套游标——用单一 `cursorY` 字段，init() 和 render() 共用同一套 Y 坐标常量（存为字段），彻底消除漂移。
- 具体改动：
  - 新增字段记录各控件 Y：`textRowY`、`targetRowY`、`visibilityRowY`、`commandListY`、`fieldX`、`fieldWidth`。
  - init() 用游标推进计算这些 Y 并创建控件，控件 Y 直接用字段值（无 `+10` 偏移）。
  - render() 用同一组字段值画标签，标签 Y = 控件 Y（垂直对齐，标签在输入框左侧或上方根据布局）。
  - 布局调整：标签画在输入框上方（`控件Y - 10`），而非左侧，因为居中表单无标签列空间。与当前"标签在输入框上方"的视觉一致，只是修正 Y 对齐。

**布局规格**（统一后）：
- 标题 y=10
- 选项文本标签 y=25，输入框 y=36（标签下方 11px，与 SequenceProperties 的 +12 一致）
- 目标节点标签 y=58（36+16+6），按钮 y=69
- 始终可见 checkbox y=91（69+16+6）
- 可见性命令标签 y=115（checkbox 24px 高 + 间距），输入框 y=126
- 添加命令按钮 y=148，命令列表 y=166
- 底部保存/取消按钮 y=this.height-30

**改动范围**：1 文件，重写 init() 和 render() 的 Y 计算。中等风险（需保证命令列表动态增删时 Y 正确）。

### 项 2：焦点可见性

**目标**：DropdownWidget 聚焦时 ACCENT 描边，EditBox 聚焦时 ACCENT 描边（通过包装或 render hook）。

**文件 1**：[DropdownWidget.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/widget/DropdownWidget.java)

**改动**：renderWidget L133-150，在按钮条渲染末尾，若 `isFocused()` 且未展开，画 ACCENT 1px 描边（与 EditorButton 一致）：
```java
if (this.isFocused() && !this.expanded) {
    graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + 1, EditorTheme.ACCENT);
    graphics.fill(this.getX(), this.getY() + this.getHeight() - 1, this.getX() + this.getWidth(), this.getY() + this.getHeight(), EditorTheme.ACCENT);
    graphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.getHeight(), EditorTheme.ACCENT);
    graphics.fill(this.getX() + this.getWidth() - 1, this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), EditorTheme.ACCENT);
}
```

**文件 2**：EditBox 聚焦描边。原生 EditBox 无法直接改渲染，采用**全局 render hook** 方案：在主屏 VNDialogEditorScreen.render 和各子屏 render 末尾，遍历 children 中聚焦的 EditBox 画 ACCENT 描边。

**决策**：为避免每个 Screen 重复代码，在 [EditorRenderHelper](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/EditorRenderHelper.java) 加静态方法 `drawFocusedEditBoxBorder(GuiGraphics, Screen)`，遍历 `screen.children()` 找 `isFocused()` 的 EditBox 画描边。各 Screen render 末尾调用一次。

**改动**：
- EditorRenderHelper 新增 `drawFocusedEditBoxBorders(GuiGraphics g, List<? extends GuiEventListener> children)`：遍历 children，对 `EditBox` 且 `isFocused()` 的画 ACCENT 描边。
- VNDialogEditorScreen.render、SequencePropertiesScreen.render、OptionEditScreen.render、PortraitListScreen.render、InputDialogScreen.render 末尾调用此方法。

**改动范围**：1 helper + 5 Screen render 各加一行调用。低风险（纯视觉叠加，不改交互）。

### 项 3：i18n 收尾

**目标**：`"None"`/`"X"`/`"Dropdown"` 走 i18n，消除硬编码。

**文件**：OptionEditScreen.java、LogicPropertyPage.java、DropdownWidget.java、zh_cn.json、en_us.json

**改动**：
- 语言文件新增：
  - `gui.vn_edit.none` = "无"/"None"
  - `gui.vn_edit.dropdown` = "下拉框"/"Dropdown"
  - 删除按钮 `"X"` 统一改为 `✕`（U+2715）符号，不走 i18n（符号无翻译需求，且与树控件 `⊗` 风格统一）。但为可读性保留 `Component.literal("\u2715")`。
- OptionEditScreen L68/L134：`"None"` → `Component.translatable("gui.vn_edit.none").getString()`（按钮 message 用 Component）。
- LogicPropertyPage L96/L196/L214/L545：同上。
- OptionEditScreen L146、LogicPropertyPage L328/L493：`Component.literal("X")` → `Component.literal("\u2715")`。
- DropdownWidget L250：`Component.literal("Dropdown")` → `Component.translatable("gui.vn_edit.dropdown")`。

**改动范围**：3 Java 文件 + 2 语言文件。低风险（纯文本替换）。

## 假设与决策

1. **OptionEditScreen 不引入 PageLayout**：PageLayout 的 fieldX/fieldWidth 基于 LABEL_WIDTH 双列模型，不适合居中表单。采用"单游标字段化"方案——消除双套游标，Y 坐标存为字段共享，效果等同但不强行套用不适配的工具。这与"要改就完善"一致：修掉错位 bug，不引入不匹配的抽象。
2. **EditBox 描边用全局 hook 而非包装类**：原生 EditBox 实例散落各处，包装类需逐个替换且影响 setResponder 等调用。全局 render hook 只加视觉叠加，零侵入。
3. **`"X"` 改 `✕` 符号不走 i18n**：符号是视觉元素无翻译需求，且 `✕` 与树控件 `⊗` 单色几何风格统一。`"None"` 走 i18n 因其是语义文字。
4. **不补属性页 Tab hover**：独立小改，与焦点描边逻辑类似但作用域不同，留后续避免本轮膨胀。
5. **不碰 PortraitListScreen 硬编码**：用户明确只迁 OptionEdit。
6. **稳定性优先**：三项改动均为视觉/文本层，不改数据流和交互逻辑，回归风险低。

## 验证步骤

不本地构建，提交 GitHub 由 Actions 构建。逐项验证：

- **项 1**：编译通过；打开选项编辑屏，标签"选项文本/目标节点/可见性命令"与对应输入框垂直对齐（标签在输入框上方，间距均匀）；命令列表增删时 Y 正确不重叠；保存/取消按钮位置正常。
- **项 2**：编译通过；Tab 键聚焦到 DropdownWidget → 出现 ACCENT 蓝色描边；聚焦到任意 EditBox → 出现 ACCENT 描边；失焦后描边消失；hover 效果不受影响。
- **项 3**：编译通过；切换到英文 locale，目标节点空值显示 "None"（i18n）；中文 locale 显示"无"；删除按钮显示 `✕`；读屏朗读 Dropdown 时用本地化名称。

整体回归：选项编辑/序列属性/立绘编辑/节点 CRUD/属性编辑，确认焦点描边不遮挡内容、i18n 文字正常、OptionEditScreen 布局无错位。
