# VNDialog 可视化编辑器 UI 重构计划

## 概述

当前编辑器 UI 存在严重的拥挤问题：所有控件统一 16px 高、2px 间距、硬编码 Y 偏移不统一（20/22/25/30 混用）、树面板固定 150px 偏窄、标签栏仅 15-18px 高、颜色按钮 16x16 密集排列、LogicPropertyPage 选项编辑按钮硬编码 220px 偏移。本次重构将引入统一的设计令牌系统（Design Tokens），重新规划间距/尺寸/配色，使编辑器更宽敞、更有层次感、更易用。

## UX 决策简报

- **任务**: 在游戏内可视化编辑 VNDialog 对话序列（文本/外观/逻辑三方面属性）
- **用户模式**: 创作者，频繁重复使用，需要高效操作
- **频率/风险**: 高频使用，可撤销（JSON 文件），低风险
- **模式**: Master/Detail（左侧对话树 + 右侧属性面板）+ 工具栏 + 标签页
- **核心路径**: 选择节点 → 编辑属性 → 保存 → 测试
- **设计方向**: 精炼暗色主题，蓝青色强调色，宽敞间距，清晰的视觉分组（分节标题栏）

## 当前状态分析

### 拥挤根因（基于代码审查）

| 问题 | 当前值 | 影响 |
|------|--------|------|
| 控件高度全统一 16px | 无视觉层次 | 主次按钮无区分 |
| 间距 2px，不统一 Y 偏移 | 20/22/25/30 混用 | 视觉节奏混乱 |
| 树面板固定 150px / 行高 12px | 长 ID 被截断 | 难以辨认节点 |
| 标签栏 15-18px | 太矮 | 文字贴边 |
| 颜色按钮 16x16 + 2px gap | 极密集 | 难以点击 |
| contentBox 高度 80px | 偏小 | 正文编辑受限 |
| 硬编码偏移 x+285 (LogicPage) | 不响应式 | 窄屏溢出 |
| 配色散落为 magic number | 无系统 | 不一致 |

### 涉及文件

1. `src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java` — 主屏幕（工具栏/标签栏/树+面板布局）
2. `src/main/java/top/yourzi/dialog/editor/gui/widget/PropertyPanel.java` — 属性面板（标签页/滚动）
3. `src/main/java/top/yourzi/dialog/editor/gui/property/TextPropertyPage.java` — 文本属性页
4. `src/main/java/top/yourzi/dialog/editor/gui/property/AppearancePropertyPage.java` — 外观属性页
5. `src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java` — 逻辑属性页
6. `src/main/java/top/yourzi/dialog/editor/gui/widget/DialogTreeWidget.java` — 对话树组件

## 设计令牌系统

### 新建文件: `editor/util/EditorTheme.java`

集中管理所有设计常量，消除散落的 magic number。

```java
public class EditorTheme {
    // ===== 配色 - 精炼暗色主题 =====
    public static final int BG_DEEPEST   = 0xFF121212;  // 屏幕底色
    public static final int BG_SURFACE   = 0xFF1C1C1C;  // 面板底色
    public static final int BG_ELEVATED  = 0xFF282828;  // 标签栏/分节标题
    public static final int BG_HOVER     = 0xFF353535;  // 悬停
    public static final int BG_SELECTED  = 0xFF1A3354;  // 选中（蓝调）
    public static final int BORDER       = 0xFF383838;  // 分隔线
    public static final int BORDER_LIGHT = 0xFF444444;  // 高亮分隔线
    public static final int TEXT_PRIMARY   = 0xFFFFFFFF;
    public static final int TEXT_SECONDARY = 0xFFB0B0B0;
    public static final int TEXT_MUTED     = 0xFF707070;
    public static final int ACCENT       = 0xFF4A9EFF;  // 主强调色（蓝）
    public static final int ACCENT_DIM   = 0xFF2A5A8A;
    public static final int DANGER       = 0xFFE05555;  // 删除/危险

    // ===== 间距 =====
    public static final int PADDING       = 6;   // 内边距
    public static final int GAP           = 5;   // 控件间距
    public static final int GAP_TIGHT     = 3;   // 紧凑间距（按钮组内）
    public static final int SECTION_GAP   = 10;  // 分节间距
    public static final int SCROLLBAR_W   = 5;

    // ===== 尺寸 =====
    public static final int TOOLBAR_H     = 24;
    public static final int TAB_BAR_H     = 22;
    public static final int STATUS_H      = 14;
    public static final int TREE_WIDTH    = 180;
    public static final int TREE_ROW_H    = 14;
    public static final int TREE_INDENT   = 12;
    public static final int PROP_TAB_H    = 18;
    public static final int PROP_TAB_W    = 56;
    public static final int LABEL_WIDTH   = 62;
    public static final int FIELD_HEIGHT  = 18;
    public static final int BTN_HEIGHT    = 18;
    public static final int BTN_WIDTH     = 52;
    public static final int SECTION_HDR_H = 14;
    public static final int COLOR_BTN_SZ  = 18;
    public static final int COLOR_BTN_GAP = 3;
}
```

## 具体修改方案

### 1. VNDialogEditorScreen — 主屏幕布局

**工具栏** (y=0 ~ y=24):
- 按钮高度 20px（原 16），Y 起点 2，间距 GAP=5
- 按钮宽度 52px（原 48），更易点击
- 6 个按钮 + 间距总宽约 `2 + 6*57 - 5 = 339px`

**标签栏** (y=24 ~ y=46):
- 高度 22px（原 18），标签按钮 18px 高
- 标签 Y 起点 25（原 21）
- 激活标签底部亮线改用 ACCENT 色（原白色）
- 标签栏背景 BG_ELEVATED

**树 + 属性面板** (y=46 ~ y=height-14):
- 树宽 180px（原 150），行高 14（原 12）
- addNode 按钮高 18，Y=46
- 树内容 Y=64（46+18）
- 属性面板 X=181（原 151），更宽

**状态栏** (y=height-14 ~ height):
- 高度 14px（原 12），文字 Y=height-12

### 2. PropertyPanel — 属性面板标签页

- 标签高度 18px（原 15），宽度 56px（原 50）
- 页面内容区：`pageY = y + PROP_TAB_H + 3`（原 +2），`pageHeight = h - PROP_TAB_H - 6`（原 -4）
- 页面宽度：`width - 8`（原 -4），左右各留 4px padding
- 标签渲染改进：
  - 激活标签底部 2px ACCENT 色亮线（原 1px 白）
  - 非激活标签文字 TEXT_SECONDARY
  - 标签间分隔线 BORDER
- 滚动条宽度 5px，滑块圆角效果（fill 改为两端缩进）

### 3. TextPropertyPage — 文本属性页

统一间距 GAP=5，分节标题栏，增大 contentBox：

| 控件 | 新 Y 偏移 | 尺寸 | 变化 |
|------|-----------|------|------|
| 分节标题"说话者" | y+2 | 全宽 x 14 | 新增分节标题 |
| speakerBox | y+20 | fieldWidth x 18 | 高度+2 |
| 分节标题"正文" | y+42 | 全宽 x 14 | 新增 |
| contentBox | y+60 | fieldWidth x **100** | 高度+20 |
| modeSwitchBtn | y+164 | 60 x 18 | |
| 分节标题"翻译" | y+186 | 全宽 x 14 | 新增（仅翻译模式） |
| translationKeyBox | y+204 | fieldWidth x 18 | |
| translationZhCnBox | y+226 | fieldWidth x 18 | 间距 22 |
| translationEnUsBox | y+248 | fieldWidth x 18 | |
| generateLangBtn | y+270 | 80 x 18 | |
| 分节标题"格式" | y+292 | 全宽 x 14 | 新增 |
| 颜色按钮网格 | y+310 | 18x18, gap 3 | 按钮更大 |
| 格式按钮行 | y+332 | 18x18, gap 3 | |
| hexColorBox + applyBtn | y+354 | 50+30 x 18 | |

- 颜色按钮：18x18（原 16x16），间距 3（原 2），每行 8 个
- 格式按钮：18x18（原 16x16），间距 3
- getContentHeight() 改为动态计算（约 380）
- 分节标题用 `graphics.fill()` 绘制 BG_ELEVATED 背景条 + TEXT_SECONDARY 文字

### 4. AppearancePropertyPage — 外观属性页

| 控件 | 新 Y 偏移 | 尺寸 | 变化 |
|------|-----------|------|------|
| 分节标题"背景" | y+2 | 全宽 x 14 | 新增 |
| backgroundPathBox | y+20 | fieldWidth x 18 | |
| browse/builtin/folder 按钮 | y+20 | 48/40/20 x 18 | 高度+2 |
| 分节标题"立绘" | y+42 | 全宽 x 14 | 新增 |
| portraitListBtn | y+60 | 110 x 18 | 宽度+10 |
| 分节标题"渲染" | y+82 | 全宽 x 14 | 新增 |
| backgroundRenderDropdown | y+100 | 90 x 18 | 宽度+10 |
| backgroundAnimDropdown | y+100 | 90 x 18 | 宽度+10 |
| 背景预览 | y+122 | min(110, fw-30) x 64 | 高度+8 |

- getContentHeight() 改为约 200

### 5. LogicPropertyPage — 逻辑属性页

统一间距，修复硬编码偏移，添加分节标题：

| 控件 | 新 Y 偏移 | 变化 |
|------|-----------|------|
| 分节标题"流程" | y+2 | 新增 |
| nextNodeBtn | y+20 | 高度 18 |
| endDialogCheck | y+42 | 间距 22 |
| allowSkipCheck | y+64 | 间距 22 |
| 分节标题"音频" | y+86 | 新增 |
| audioPathBox + 按钮 | y+104 | |
| 分节标题"可见性" | y+128 | 新增 |
| visibilityCommandBox | y+146 | |
| 分节标题"命令" | y+168 | 新增 |
| addCommandBtn | y+186 | |
| commandListStartY | y+206 | |
| 分节标题"物品" | 动态 | 新增 |
| addItemBtn | 动态 | |
| displayItemsStartY | 动态 | |
| 分节标题"选项" | 动态 | 新增 |
| addOptionBtn | 动态 | |
| optionListStartY | 动态 | |

**修复硬编码偏移**: 选项行的 editBtn/deleteBtn 位置改为基于 `fieldWidth` 比例计算，不再硬编码 `x+285`：
```java
int editX = fieldX + fieldWidth - 90;  // 编辑+删除按钮靠右，总宽 90
int deleteX = editX + 56;
```

**relayoutSections() 更新**: 各区块间隔从 8 改为 SECTION_GAP=10，header 到列表从 20 改为 22。

### 6. DialogTreeWidget — 对话树

- 行高 14（原 12），缩进 12（原 10）
- 文字 Y 偏移 +2（rowY+2，原 +1）
- 选中背景 BG_SELECTED（原 0x66FFFFFF）
- 悬停背景 BG_HOVER（原 0x44FFFFFF）
- 文字颜色 TEXT_SECONDARY（原 -3355444）
- 连接信息颜色 TEXT_MUTED
- 滚动条宽 5px

### 7. 分节标题渲染辅助方法

在各 PropertyPage 中添加通用方法（或在 EditorTheme 中提供静态方法）：

```java
// 在 PropertyPage 实现中或 EditorTheme 中
public static void drawSectionHeader(GuiGraphics g, Font font, int x, int y, int width, Component title) {
    g.fill(x, y, x + width, y + EditorTheme.SECTION_HDR_H, EditorTheme.BG_ELEVATED);
    g.fill(x, y + EditorTheme.SECTION_HDR_H - 1, x + width, y + EditorTheme.SECTION_HDR_H, EditorTheme.BORDER);
    g.drawString(font, title, x + 4, y + 3, EditorTheme.TEXT_SECONDARY);
}
```

## 假设与决策

1. **不改变整体架构**: 保持 Master/Detail（树+面板）布局，不引入新的屏幕结构
2. **保持接口不变**: PropertyPage 接口方法签名不变，只改 init/render 内部实现
3. **保持功能完整**: 所有现有功能（文本格式/立绘/背景/命令/物品/选项/可见性）不受影响
4. **配色方向**: 精炼暗色 + 蓝青强调色，适合长时间编辑使用，与 Minecraft 暗色 UI 风格协调
5. **间距策略**: 以 5px 为基础间距单位，分节间距 10px，在 Minecraft 字体尺度下既宽敞又不浪费空间
6. **不改语言文件**: 现有翻译键不变，分节标题使用已有键或新增少量键

## 新增语言键

```
gui.vn_edit.section.speaker = 说话者 / Speaker
gui.vn_edit.section.content = 正文 / Content
gui.vn_edit.section.translation = 翻译 / Translation
gui.vn_edit.section.format = 格式 / Format
gui.vn_edit.section.background = 背景 / Background
gui.vn_edit.section.portraits = 立绘 / Portraits
gui.vn_edit.section.render = 渲染 / Render
gui.vn_edit.section.flow = 流程 / Flow
gui.vn_edit.section.audio = 音频 / Audio
gui.vn_edit.section.visibility = 可见性 / Visibility
gui.vn_edit.section.commands = 命令 / Commands
gui.vn_edit.section.items = 物品 / Items
gui.vn_edit.section.options = 选项 / Options
```

## 验证步骤

1. **编译验证**: GitHub Actions 构建通过
2. **功能验证**: 所有现有功能仍可用（文本编辑/格式按钮/立绘管理/背景选择/命令/物品/选项/可见性命令/序列属性）
3. **视觉验证**: 
   - 工具栏按钮更易点击（20px 高）
   - 属性面板分节标题清晰可见
   - 颜色/格式按钮间距增大，不再密集
   - 对话树行高增大，长 ID 更易辨认
   - LogicPropertyPage 选项编辑/删除按钮在窄屏下不溢出
4. **滚动验证**: 属性面板内容增多后滚动正常工作
5. **下拉框验证**: DropdownWidget 弹出列表仍在外层渲染不被遮挡
