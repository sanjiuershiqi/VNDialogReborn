# 编辑器视觉美化（第八轮）—— 学习 Sparkle-Morpher

## 背景与定位

前七轮已完成功能补齐（快捷键、键盘导航、空状态、列表排序、复制粘贴等）。
本轮纯做**视觉美化**：对比 Sparkle-Morpher 调研发现，VNDialog 当前是"扁平实色方块 +
硬边框 + 布尔 hover"的工程化风格，Sparkle 是"半透明玻璃 + 模糊纵深 + 颜色混合"的精致风格。

最大视觉差距在四个维度：**半透明叠层 / 阴影深度 / 颜色过渡 / 圆角柔化**。
滚动 lerp 与间距体系 VNDialog 已对齐，本轮不动。

本轮聚焦 P0 + P1（高收益、低成本），不引入 GLSL 模糊（P2 备忘，风险高需独立轮）。

---

## 项 1：EditorTheme 扩充色板

**问题**：[EditorTheme.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/util/EditorTheme.java) 色板全不透明、文字纯白偏冷、强调色单一、`DIVIDER` 定义了零引用。

**方案**：新增以下常量（值借鉴 Sparkle `RoulettePanelStyle`/`RouletteTheme`）：
- `TEXT_WARM = 0xFFEDE1CC`：暖米色文字（用于标题/重要值，比纯白柔和，Sparkle 面板主文字色）
- `SHADOW_DROP = 0x4D000000`：30% 黑，浮层投影
- `SHADOW_INNER_GLOW = 0x36FFFFFF`：22% 白，内发光（借鉴 Sparkle `SLICE_INNER_GLOW`）
- `GLOW_ACCENT = 0x404A9EFF`：25% 蓝，强调发光
- `PANEL_GLASS = 0x60405058`：半透明玻璃底（借鉴 Sparkle `GLASS`）
- `PANEL_GLASS_HOVER = 0x66576B76`：玻璃 hover（借鉴 Sparkle `PANEL_HOVER`）
- `PANEL_GLASS_BORDER = 0x6EE4F5FF`：半透明亮蓝边框（借鉴 Sparkle `BORDER`，玻璃边缘反光）

保留现有常量不动，仅新增。`DIVIDER` 已存在（`0x40FFFFFF`），本轮开始真正使用。

**改动文件**：`EditorTheme.java`

---

## 项 2：EditorRenderHelper 新增渲染工具

**问题**：[EditorRenderHelper.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/EditorRenderHelper.java) 只有 `drawBorder`/`fillWithBorder`/`drawVerticalScrollbar`，无圆角、无投影、无颜色插值、无玻璃面板。

**方案**：新增以下静态方法：

- `fillRoundedRect(g, x, y, w, h, radius, color)`：圆角矩形（radius 建议 2，MC GUI 缩放下不宜过大）。实现：四角补小方块遮角 + 中心十字 fill 近似圆角。
- `fillWithShadow(g, x, y, w, h, fill, shadowColor)`：浮层投影。在 `(x-1,y-1)` 到 `(x+w+2,y+h+2)` 画 1-2px `shadowColor` 外扩阴影，再画实色填充 + 1px 边框。
- `fillGlassPanel(g, x, y, w, h)`：玻璃面板。先画 `PANEL_GLASS` 半透明底，再画 1px `PANEL_GLASS_BORDER` 亮蓝边框（无模糊也能有磨砂观感）。
- `lerpColor(from, to, t)`：按通道线性插值 alpha/R/G/B（供 hover 渐变用）。
- `brighten(argb, amount)`：RGB 各 `+amount`，alpha 不变（复制 Sparkle `OptionRow.blendBg` 逻辑）。

**改动文件**：`EditorRenderHelper.java`

---

## 项 3：分节标题美化

**问题**：[EditorTheme.drawSectionHeader](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/util/EditorTheme.java) 当前是"实色条 + 灰字 + 硬底线"，朴素。

**方案**：改 `drawSectionHeader`：
- 标题文字色：`TEXT_SECONDARY` → `TEXT_WARM`（暖米色，更突出）
- 标题文字加阴影 `true`（`drawString` 第 6 参）
- 左侧加 2px `ACCENT` 竖条（x 到 x+2，高度 = `SECTION_HDR_H`）—— 形成"分节锚点"，与 `DialogTreeWidget` 选中项竖条统一视觉语言
- 底部分割线：`BORDER` → `DIVIDER`（半透明白，更轻盈）

**改动文件**：`EditorTheme.java`

---

## 项 4：hover 颜色 lerp 渐变

**问题**：所有 hover 都是布尔硬切换（`? color1 : color2`），视觉"跳变"。Sparkle 用 `blendBg` RGB 偏移 + 半透明叠加更柔和。

**方案**：为核心控件引入 `hoverProgress` 浮点字段，每帧用 `1-exp(-dt*18)` 系数（复用 `ScrollState.tick` 的 lerp 公式）逼近目标（hover=1，非 hover=0），render 时用 `lerpColor` 取色。

涉及控件：
- [EditorButton](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/widget/EditorButton.java)：加 `hoverProgress` 字段，`renderWidget` 背景用 `lerpColor(BG_ELEVATED, BG_HOVER, hoverProgress)`，边框用 `lerpColor(BORDER, ACCENT, hoverProgress)`。需在 render 前调一次推进（用 `System.nanoTime()` 算 dt）。
- [DialogTreeWidget](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/widget/DialogTreeWidget.java)：行 hover 用 `brighten(BG_SURFACE, (int)(20*hoverProgress))` 叠加而非硬切 `BG_HOVER`。因树有动态行数，用 `Map<Integer, Float>` 存每行 hoverProgress 按行索引键（hover 行索引每帧更新）。
- [DropdownWidget](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/widget/DropdownWidget.java)：按钮条 hover 同 EditorButton 模式，加 `hoverProgress` 字段。
- [PropertyPanel](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/widget/PropertyPanel.java) 标签：标签 hover/选中用 lerp，加 `float[] tabHoverProgress` 数组。

为避免每控件重复 dt 计算，在 `EditorRenderHelper` 加 `tickProgress(current, target, dt)` 工具方法封装 lerp 推进。

**改动文件**：`EditorRenderHelper.java`（工具）、`EditorButton.java`、`DialogTreeWidget.java`、`DropdownWidget.java`、`PropertyPanel.java`

---

## 项 5：浮层投影

**问题**：[DropdownWidget.renderPopupInternal](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/widget/DropdownWidget.java) 弹出列表 `0xFF181818` 实色无阴影，"贴"在按钮下方无悬浮感。[renderHelpOverlay](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java) 同样无投影。

**方案**：
- `DropdownWidget.renderPopupInternal`：画背景前先在弹出区域四周画 2px `SHADOW_DROP` 投影（向下偏移 1px 模拟光源在上），再画背景 + 边框。
- `VNDialogEditorScreen.renderHelpOverlay`：面板四周加 2px `SHADOW_DROP` 投影。
- `InputDialogScreen` / `EditorConfirmScreen` 模态框同理加投影（若两者已有半透明遮罩，投影画在遮罩之上、面板之下）。

**改动文件**：`DropdownWidget.java`、`VNDialogEditorScreen.java`、`InputDialogScreen.java`、`EditorConfirmScreen.java`

---

## 项 6：选中态锚点统一

**问题**：`DialogTreeWidget` 选中项有 2px `ACCENT` 左竖条（最精致的选中态），但 `DropdownWidget` 选中项、`PropertyPanel` 活动标签无此锚点，视觉语言不统一。

**方案**：
- `DropdownWidget.renderPopupInternal` 选中项（第 179 行 `i == selectedIndex`）：除 `BG_SELECTED` 背景，左侧加 2px `ACCENT` 竖条（x 到 x+2，行高范围内）。
- `PropertyPanel` 活动标签（第 206-211 行）：已有 2px `ACCENT` 底线，额外在左侧加 2px `ACCENT` 竖条，与树选中项呼应。

**改动文件**：`DropdownWidget.java`、`PropertyPanel.java`

---

## 项 7：圆角柔化

**问题**：全直角硬边，视觉生硬。

**方案**：用项 2 的 `fillRoundedRect(radius=2)` 替换关键控件的 `graphics.fill` 背景：
- `EditorButton.renderWidget` 按钮背景（第 51-57 行）
- `DropdownWidget.renderWidget` 按钮条背景（第 135 行）+ `renderPopupInternal` 弹出列表背景（第 164 行）
- `PropertyPanel` 标签背景（第 206 行）

radius=2 足够柔和又不会在小尺寸控件上显得怪异。边框仍用 `drawBorder`（直角，因圆角边框需 shader，成本高，本轮用圆角填充 + 直角边框的折中，视觉上填充圆角会略微超出边框角，但 radius=2 几乎不可见）。

**改动文件**：`EditorButton.java`、`DropdownWidget.java`、`PropertyPanel.java`

---

## 项 8：启用 DIVIDER 分割线

**问题**：`EditorTheme.DIVIDER = 0x40FFFFFF` 定义了却零引用。各功能区（工具栏/标签栏/树/面板）靠色差分隔，边界模糊。

**方案**：在以下边界画 1px `DIVIDER` 线：
- `VNDialogEditorScreen` 工具栏与标签栏之间（`tabBarTop` 处画横线）
- `VNDialogEditorScreen` 树与属性面板之间（`propX = TREE_WIDTH + 1` 处画竖线）
- `PropertyPanel` 标签栏与内容区之间（`tabX + TAB_WIDTH` 处画竖线）

**改动文件**：`VNDialogEditorScreen.java`、`PropertyPanel.java`

---

## 项 9：选择性文字阴影 + 移除 emoji

**问题**：暗色背景下某些文字对比度偏弱；`AppearancePropertyPage` 第 127 行用 `📂` emoji，MC 字体下渲染不一致（常显示方框）。

**方案**：
- `DropdownWidget.renderPopupInternal` 列表项 hover 时 `drawString` 加阴影 `true`（第 186 行，hover 背景变化时文字需保持清晰）
- `EditorButton` hover 时 `drawCenteredString` 加阴影 `true`（第 98 行）
- `DialogTreeWidget` 选中项文字加阴影 `true`（第 512 行）
- `AppearancePropertyPage` 第 127 行 `📂` → `"..."`（文字省略号，风格统一）

**改动文件**：`DropdownWidget.java`、`EditorButton.java`、`DialogTreeWidget.java`、`AppearancePropertyPage.java`

---

## 不在本轮范围（P2/P3 备忘）

| 项 | 原因 | 建议时机 |
|---|---|---|
| 真实高斯模糊（移植 BlurStack/BlurShader + GLSL） | 需移植 GLSL shader + 降级兜底，风险高 | 独立一轮 |
| 统一图标精灵图（editor_icons.png + EditorIcons 类） | 需美术资源制作 | 独立一轮 |
| dirty 行级视觉态（属性页行级标记已修改） | 需改 AbstractPropertyPage 行渲染，配合 OptionRow 迁移 | 配合 OptionRow 全量迁移 |
| hover 外发光（GLOW_ACCENT 多层描边） | 收益低，lerp 已够 | 远期 |
| 暖色文字色板全量替换（TEXT_WARM/LINK/KEYBIND） | 本轮只用于标题，全量替换改动面大 | 远期 |

---

## 实施顺序与验证

1. 项 1（色板）→ 项 2（工具方法）—— 基础设施先行
2. 项 3（分节标题）→ 项 8（DIVIDER 分割线）→ 项 9（文字阴影 + emoji）—— 低风险静态美化
3. 项 7（圆角）→ 项 6（选中态锚点）→ 项 5（浮层投影）—— 控件视觉提升
4. 项 4（hover lerp）—— 最后做，因涉及多控件状态字段，风险最高

每项完成后代码审查（沙箱无 gradle 依赖无法编译，靠严格审查 API 使用）。全部完成后提交：`style(editor): round 8 视觉美化 - 半透明叠层/投影/圆角/hover渐变/分节标题`。

**验收**：打开编辑器 → 分节标题有暖色字 + 蓝竖条 → 工具栏/树/面板间有半透明分割线 → 按钮/下拉 hover 有颜色渐变非跳变 → 下拉弹出列表有投影悬浮感 + 选中项有蓝竖条 → 按钮/下拉边缘微圆角 → 无 emoji 方框。
