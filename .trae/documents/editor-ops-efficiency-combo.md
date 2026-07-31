# 编辑器操作效率综合改进（第五轮）

## 摘要

延续前四轮改造，本轮聚焦**三项高价值小改组合**，不碰大架构（模态浮层 / OptionRow 全面重构 / 布局自适应留后续）。对照 Sparkle-Morpher 的快捷交互、搜索持久化、三态列表模式，识别出 VNDialog 编辑器在"操作效率"层的三个差距：

1. **主屏幕快捷键几乎为零**：仅 `Ctrl+S`，高频操作（删节点/重命名/加节点/切标签/测试）全靠鼠标点按钮。
2. **字段编辑 dirty 残留缺陷**：第四轮 OptionRow 试点只修了 `endDialog`/`allowSkip` 两个字段，其余 EditBox/Dropdown 的 `setResponder` 仍不调 `dirtyListener`，编辑这些字段后标签页不显示 `*`、`hasUnsavedChanges` 漏报。
3. **对话树 / NodePicker 完全无搜索**：节点多时只能滚动查找，`NodePickerScreen` 选下一节点同样无搜索。

**不做**：节点拖拽重排、右键上下文菜单、内联编辑替代全屏 InputDialogScreen（属模态浮层改造，留后续大改）、OptionEditScreen 迁移 PageLayout（留后续）、布局自适应、从当前节点测试。

## 当前状态分析（差距定位）

### 差距 1：快捷键体系缺失

[VNDialogEditorScreen.keyPressed](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java#L898-L904) 仅处理 `Ctrl+S`（keyCode 83）：

```java
if (keyCode == 83 && Screen.hasControlDown()) {
    this.onSave();
    return true;
}
return super.keyPressed(keyCode, scanCode, modifiers);
```

高频操作无快捷键：删除选中节点（Delete）、重命名节点（F2）、新建对话（Ctrl+N）、添加节点（Insert）、测试（Ctrl+Enter）、切换标签（Ctrl+Tab / Ctrl+Shift+Tab）。

对比 Sparkle：`RangedSliderWidget` 焦点驱动方向键步进、`UnifiedRouletteScreen` 滚轮翻页，键盘交互下沉到控件自治。VNDialog 借鉴其"焦点闸门"思路：EditBox 聚焦时放行字符/编辑键，仅功能键和 Ctrl 组合键在非 EditBox 聚焦时拦截。

### 差距 2：字段编辑 dirty 残留

第四轮 [OptionRow 试点](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java) 只让 `endDialog`/`allowSkip` 两个 Checkbox 通过 `Option.onDirty` 联动 `markDirty`。其余字段仍直接写回 `currentEntry` 不触发 dirty：

- [TextPropertyPage](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/TextPropertyPage.java#L101-L133)：`speakerBox`/`contentBox`/`translationKeyBox`/`translationZhCnBox`/`translationEnUsBox` 的 `setResponder` 直接 `setSpeaker`/`saveTextToEntry`/`saveTranslationToEntry`，不调 dirtyListener。
- [AppearancePropertyPage](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/AppearancePropertyPage.java)：背景渲染/动画下拉框回调、颜色/偏移 EditBox 回调，不调 dirtyListener。
- [LogicPropertyPage](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java)：`nextIdBox`、command/item 列表编辑回调，不调 dirtyListener。

结果：编辑这些字段后标签页无 `*` 标记，关闭编辑器时 `hasUnsavedChanges` 漏报，可能丢失修改。`AbstractPropertyPage` 已有 `dirtyListener` 字段（第四轮加），可直接复用。

### 差距 3：对话树 / NodePicker 无搜索

[DialogTreeWidget](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/widget/DialogTreeWidget.java) 全文无 search/filter，节点多时只能滚动查找。[NodePickerScreen](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/NodePickerScreen.java) 选下一节点/起始节点同样无搜索，节点多时定位困难。

对比 Sparkle：[ModernPlayerModelScreen](file:///workspace/Sparkle-Morpher/src/main/java/com/micaftic/morpher/client/gui/ModernPlayerModelScreen.java#L102-L103) 用静态 STATE 持久化搜索文本，`init()` 重建后回填；搜索时全局扁平过滤，空结果显示 "no_results" 提示。VNDialog 借鉴此模式。

## 借鉴的 Sparkle-Morpher 模式

- **搜索持久化**：`STATE.modelSearchText` 静态字段，`init()` 重建后 `searchBox.setValue(STATE.modelSearchText)` 回填。VNDialog 用 `EditorScreenState` 持久化树搜索文本。
- **三态列表**：加载/空/有数据三态，空结果居中显示 "no_results"。VNDialog 树搜索无匹配时显示提示。
- **焦点闸门**：`RangedSliderWidget` 用 `canChangeValue` 配合 `InputType` 判定，仅 Tab 聚点进入才响应方向键。VNDialog 借鉴"EditBox 聚焦时放行编辑键"思路。

## 改造项（3 项，按依赖顺序）

### 项 1：主屏幕快捷键体系

**目标**：为高频操作补齐快捷键，EditBox 聚焦时不拦截编辑输入。

**文件**：[VNDialogEditorScreen.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java)、[DialogTreeWidget.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/widget/DialogTreeWidget.java)

**改动 1a：DialogTreeWidget 暴露重命名能力**

当前重命名逻辑内联在 [mouseClicked 双击分支](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/widget/DialogTreeWidget.java#L294-L328)（L294-328），含 ID 唯一性校验、引用更新、重建树、恢复选中。为支持 F2 触发，提取为公开方法：

```java
/** 重命名当前选中节点。返回 true 表示成功（ID 未冲突）。由双击和 F2 快捷键共用。 */
public boolean renameSelectedEntry(String newId) {
    if (this.selectedIndex < 0 || this.selectedIndex >= this.visibleNodes.size()) return false;
    TreeNode node = this.visibleNodes.get(this.selectedIndex);
    String oldId = node.entry.getId();
    if (oldId.equals(newId) || this.sequence.findEntryById(newId) != null) return false;
    // ... 移动 L303-320 的引用更新逻辑到此
    this.buildTree();
    // 恢复选中到新 ID
    return true;
}

/** 获取当前选中节点，无选中返回 null。 */
public DialogEntry getSelectedEntry() {
    if (this.selectedIndex < 0 || this.selectedIndex >= this.visibleNodes.size()) return null;
    return this.visibleNodes.get(this.selectedIndex).entry;
}
```

双击分支改为调用 `renameSelectedEntry`，保持行为不变。

**改动 1b：VNDialogEditorScreen.keyPressed 扩展**

替换 [L898-904](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java#L898-L904)：

```java
@Override
public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    // EditBox 聚焦时放行字符/编辑键，仅 Ctrl 组合键和功能键由屏幕处理
    boolean editBoxFocused = this.getFocused() instanceof EditBox;

    // Ctrl+S 保存（已有）
    if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_S) {
        this.onSave();
        return true;
    }
    // Ctrl+N 新建对话序列
    if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_N) {
        this.onNew();
        return true;
    }
    // Ctrl+Enter 测试当前序列
    if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_ENTER) {
        this.onTest();
        return true;
    }
    // Ctrl+Tab / Ctrl+Shift+Tab 切换标签页
    if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_TAB) {
        int dir = Screen.hasShiftDown() ? -1 : 1;
        int next = this.activeSequenceIndex + dir;
        if (next < 0) next = this.openSequences.size() - 1;
        if (next >= this.openSequences.size()) next = 0;
        if (next >= 0 && next < this.openSequences.size()) this.switchToSequence(next);
        return true;
    }
    // 以下功能键仅在 EditBox 未聚焦时拦截，避免与文本编辑冲突
    if (!editBoxFocused) {
        // Delete 删除选中节点
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            DialogEntry sel = this.treeWidget.getSelectedEntry();
            if (sel != null) { this.onEntryDelete(sel); return true; }
        }
        // F2 重命名选中节点
        if (keyCode == GLFW.GLFW_KEY_F2) {
            DialogEntry sel = this.treeWidget.getSelectedEntry();
            if (sel != null) { this.startRenameEntry(sel); return true; }
        }
        // Insert 添加节点
        if (keyCode == GLFW.GLFW_KEY_INSERT) {
            this.onAddNode();
            return true;
        }
    }
    return super.keyPressed(keyCode, scanCode, modifiers);
}
```

新增 `startRenameEntry(DialogEntry)` 方法：弹出 `InputDialogScreen`，确认后调 `treeWidget.renameSelectedEntry(newId)`，失败显示状态栏警告（修复 [L300](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/widget/DialogTreeWidget.java#L300) 重命名失败无 UI 反馈的缺陷）。

需 `import org.lwjgl.glfw.GLFW;` 和 `import net.minecraft.client.gui.components.EditBox;`。

**改动范围**：2 Java 文件。keyPressed 扩展 + 1 方法提取 + 1 新方法。中等风险（需正确处理 EditBox 聚焦边界，漏判会拦截文本输入）。

### 项 2：字段编辑 dirty 残留修复

**目标**：所有字段编辑都触发 `markDirty`，标签页显示 `*`、`hasUnsavedChanges` 正确报告。

**文件**：[TextPropertyPage.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/TextPropertyPage.java)、[AppearancePropertyPage.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/AppearancePropertyPage.java)、[LogicPropertyPage.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java)

**改动**：在每个 `setResponder` 回调末尾追加 `if (this.dirtyListener != null) this.dirtyListener.run();`。`dirtyListener` 字段已在 `AbstractPropertyPage` 中定义（第四轮加），由 `VNDialogEditorScreen.buildWidgets` 注入 `() -> markDirty(currentSequence)`。

定位所有 `setResponder` 调用点（grep `setResponder` 在三个属性页）：

- **TextPropertyPage**（5 处）：`speakerBox`（L101-106）、`contentBox`（L114）、`translationKeyBox`（L125）、`translationZhCnBox`（L129）、`translationEnUsBox`（L133）。
- **AppearancePropertyPage**：背景渲染/动画 Dropdown 回调、颜色/偏移 EditBox 回调（grep 定位）。
- **LogicPropertyPage**：`nextIdBox`、command/item 列表编辑回调（grep 定位）。`endDialog`/`allowSkip` 已通过 Option 联动，无需改。

**不引入完整 Option**：仅在 setResponder 末尾补 dirtyListener 调用，保持即时回写架构不变（符合"小改"原则）。

**改动范围**：3 文件，约 10-15 处 setResponder 追加一行。低风险（纯增量，不改现有写回逻辑）。

### 项 3：对话树搜索 + NodePicker 搜索

**目标**：树顶部加搜索框实时过滤节点，NodePicker 同步加搜索，搜索文本跨 init 重建持久化。

**文件 1**：[EditorScreenState.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/EditorScreenState.java)

新增字段：
```java
/** 对话树搜索文本（重建后回填，借鉴 Sparkle ModelPanelState）。 */
private String treeSearchText = "";
public String getTreeSearchText() { return treeSearchText; }
public void setTreeSearchText(String text) { this.treeSearchText = text; }
```
`reset()` 中清空 `treeSearchText = ""`。

**文件 2**：[DialogTreeWidget.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/widget/DialogTreeWidget.java)

改动：
- 新增 `EditBox searchBox` 字段。在 `setSequence` 或 `init` 等价时机创建搜索框，放在树内容区顶部（`addNodeBtn` 下方），宽度 = `TREE_WIDTH`，高 14。
- `searchBox` 初始值从 `EditorScreenState.get().getTreeSearchText()` 回填。
- `searchBox.setResponder(text -> { EditorScreenState.get().setTreeSearchText(text); this.applySearch(); })`。
- 新增 `applySearch()`：若搜索文本为空，正常 `flattenTree()`；否则遍历所有 `roots`+`orphans`，收集 ID 包含搜索文本（不区分大小写）的节点，并展开其父链使其可见，重建 `visibleNodes` 仅含匹配项及其祖先链。
- `renderWidget`：搜索框不为空且 `visibleNodes` 为空时，居中显示 "无匹配节点" 提示（`EditorTheme.TEXT_SECONDARY`）。
- 搜索框占用树内容区顶部 16px，`treeContentY` 相应下移（在 `VNDialogEditorScreen.buildWidgets` 调整，或 DialogTreeWidget 内部留出顶部 padding）。

为减少对 `VNDialogEditorScreen.buildWidgets` 布局的影响：搜索框由 `DialogTreeWidget` 内部管理，渲染在 widget 区域顶部 16px，内容区从 y+16 开始（widget 内部 scissor 和行绘制偏移 +16）。

**文件 3**：[NodePickerScreen.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/NodePickerScreen.java)

改动：
- 新增 `EditBox searchBox` 字段和 `List<String> filteredIds` 字段。
- `init()` 顶部加搜索框（居中，宽 200，y=8，与"清空选择"按钮同行或上方）。
- 维护 `filteredIds`：`nodeIds` 中 ID 包含搜索文本（不区分大小写）的子集。
- `render` 按 `filteredIds` 绘制行；`mouseClicked` 按 `filteredIds` 索引选择。
- 搜索文本用静态字段 `private static String lastSearchText = ""` 持久化（NodePicker 是临时屏，静态字段足够）。
- 空结果居中显示 "无匹配节点"。

**改动范围**：3 文件。DialogTreeWidget 搜索逻辑较复杂（父链展开），NodePicker 简单。中等风险（搜索过滤与 buildTree/flattenTree 交互需仔细，避免破坏选中/滚动恢复）。

## 假设与决策

1. **快捷键与 EditBox 冲突处理**：`Delete` 在 EditBox 聚焦时放行（删字符），仅焦点不在 EditBox 时删除节点；`F2`/`Insert` 是功能键不与文本输入冲突，但仍只在非 EditBox 聚焦时拦截以避免意外；`Ctrl+*` 组合键任何情况都拦截（Ctrl+S/N/Enter/Tab 不用于文本编辑）。
2. **dirty 修复用最小方案**：setResponder 末尾调 dirtyListener，不引入完整 Option，避免过度工程。与第四轮 OptionRow 试点共存，未来 OptionRow 全面铺开时再统一。
3. **F2 重命名复用现有 InputDialogScreen**：不引入内联编辑（属模态浮层改造，留后续）。但修复重命名失败无 UI 反馈的缺陷（L300 仅 LOGGER.warn）。
4. **树搜索框由 DialogTreeWidget 内部管理**：避免改动 VNDialogEditorScreen.buildWidgets 布局，搜索框渲染在 widget 区域顶部 16px，内容区下移。
5. **搜索匹配按 ID 包含**：不区分大小写，不引入 Sparkle 的 `@`/`#` 前缀语法（VNDialog 节点只有 ID 一个维度，前缀语法无意义）。
6. **NodePicker 搜索用静态字段持久化**：NodePicker 是临时选择屏，跨屏切换频繁，静态字段比 EditorScreenState 更合适（不污染主编辑器状态）。
7. **不做节点拖拽/右键菜单/内联编辑**：这些属较大交互改造，留后续轮次。
8. **改动顺序**：项1（快捷键，含 DialogTreeWidget 方法提取）→ 项2（dirty 修复，独立）→ 项3（搜索，依赖项1 的 DialogTreeWidget 改动稳定）。项1 和项2 可并行，项3 在项1 后。

## 验证步骤

不本地构建，提交 GitHub 由 Actions 构建。逐项验证：

- **项 1**：编译通过；Ctrl+S 保存、Ctrl+N 新建、Ctrl+Enter 测试、Ctrl+Tab/Ctrl+Shift+Tab 循环切换标签；焦点在树（非 EditBox）时 Delete 删除选中节点、F2 弹重命名框、Insert 添加节点；焦点在 EditBox（如 speaker 输入框）时 Delete 删字符不删节点、F2/Insert 不触发；F2 重命名输入已存在 ID → 状态栏显示警告（而非静默）。
- **项 2**：编译通过；编辑 speaker/content/audio/背景/动画/nextId/command/item 等字段 → 标签页立即显示 `*`；保存后 `*` 消失；不编辑直接关闭不弹"保存全部"。
- **项 3**：编译通过；树顶部出现搜索框，输入文本实时过滤节点（仅显示 ID 包含文本的节点及其祖先链）；清空搜索框恢复完整树；搜索无匹配显示"无匹配节点"；切换标签/缩放窗口后搜索文本保留；NodePicker 顶部出现搜索框，过滤生效，空结果显示提示。

整体回归：新建/保存/读取/导入/测试/标签开关/节点 CRUD/属性编辑/重命名，确认快捷键不误触发、搜索不破坏选中恢复、dirty 标记准确。
