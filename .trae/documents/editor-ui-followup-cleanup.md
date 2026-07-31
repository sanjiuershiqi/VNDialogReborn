# 编辑器 UI 收尾改进计划（第二轮）

## 摘要

延续第一轮「UI 地基升级」（commit `62bc833`，已完成 6 项），本轮聚焦第一轮遗留的**收尾问题**：清理已被 EditorScreenState 取代但未删的死代码链路、接入 EditorScreenState 两处死字段、补齐 Sparkle-Morpher 已有而 VNDialog 缺失的滚动条拖拽 + 平滑滚动交互。

**不做**：属性页 dirty/apply/undo 模型（D 项，中型改造留后续）、纹理异步加载（G）、性能剖析（H）、组件复用整理（J）。本轮控制风险、快速可验证。

## 当前状态分析（第一轮遗留痛点）

基于对 Sparkle-Morpher `OptionScreen`/`OptionRow` 与 VNDialog 现状的代码级对比：

1. **recoverXxxTab 死代码链路**：[AppearancePropertyPage.java#L243-L248](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/AppearancePropertyPage.java#L243-L248) 的 `recoverAppearanceTab()` 和 [LogicPropertyPage.java#L564-L569](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java#L564-L569) 的 `recoverLogicTab()`，均在子屏（FileBrowser/PortraitList/OptionEdit/NodePicker/InventoryItemPicker/BuiltInTextureBrowser）的 `onSelected` 回调内调用。但子屏回调执行时 `Minecraft.getInstance().screen` 仍是子屏（子屏先 `onSelected.accept(...)` 再 `setScreen(parent)`），`screen instanceof VNDialogEditorScreen` **永不命中**，`setPropertyPanelTab` 根本不被调到。标签恢复实际已由 `EditorScreenState` 在 [PropertyPanel.java#L48](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/widget/PropertyPanel.java#L48) 构造时接管。整条 recover 链路是纯死代码。`VNDialogEditorScreen.pendingTabIndex` 与 `EditorScreenState.activePropertyTab` 形成双重跟踪，亦冗余。

2. **EditorScreenState 两死字段**：[EditorScreenState.java#L19-L21](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/EditorScreenState.java#L19-L21) 定义了 `selectedNodeId` 和 `treeScrollOffset`，但 grep 全仓 `DialogTreeWidget` 完全未读写。[DialogTreeWidget.java#L51-L56](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/widget/DialogTreeWidget.java#L51-L56) 的 `setSequence` 把 `selectedIndex=-1`、`scrollOffset=0` 重置，子屏返回重建后选中节点与树滚动全丢。两字段是死字段。

3. **滚动条不可拖拽 + 无平滑滚动**：[PropertyPanel.java#L207-L212](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/widget/PropertyPanel.java#L207-L212)、[DialogTreeWidget.java#L230-L234](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/widget/DialogTreeWidget.java#L230-L234)、[PortraitListScreen.java#L780-L785](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/PortraitListScreen.java#L780-L785) 三处滚动条都**只渲染、不可拖拽**，仅 `mouseScrolled` 滚轮触发，且 `translate(-scrollOffset)` 硬切无过渡。对比 Sparkle [OptionScreen.java#L56-L60](file:///workspace/Sparkle-Morpher/src/main/java/com/micaftic/morpher/core/gui/OptionScreen.java#L56-L60) 有 `draggingRowScrollbar` 标志 + `mouseDragged`/`mouseReleased` + `updateRowScrollFromMouse`，以及 [OptionScreen.java#L291-L299](file:///workspace/Sparkle-Morpher/src/main/java/com/micaftic/morpher/core/gui/OptionScreen.java#L291-L299) 的 `rowScrollDisplay += (offset - display) * (1 - exp(-dt*18))` 指数 lerp 平滑滚动。

## 借鉴的 Sparkle-Morpher 模式

- **滚动条拖拽**：`draggingXxxScrollbar` 布尔标志 + `isOnXxxScrollbar` 命中检测 + `mouseClicked` 置位 + `mouseDragged` 调 `updateXxxScrollFromMouse`（按鼠标 Y 在轨道中的比例映射到 scrollOffset）+ `mouseReleased` 复位。
- **平滑滚动**：维护 `scrollDisplay`（浮点显示值）与 `scrollOffset`（整型目标值）分离，每帧 `scrollDisplay += (scrollOffset - scrollDisplay) * lerp`，`lerp = 1 - exp(-dt * 18)`，差值 <0.5 吸附；渲染用 `Math.round(scrollDisplay)` 做 translate，鼠标命中也用 round 后值补偿。

## 改造项（3 项，按依赖顺序）

### 项 1：清理 recoverXxxTab 死代码链路（B 项）

**目标**：删除已被 EditorScreenState 取代的死代码，消除 `pendingTabIndex` 双重跟踪。

**文件 1**：[AppearancePropertyPage.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/AppearancePropertyPage.java)
- 删除 `recoverAppearanceTab()` 方法（L243-L248）。
- 删除 3 处调用：L219（onBackgroundBrowse 回调）、L227（onBackgroundBuiltIn 回调）、L239（openPortraitList 回调）。
- 删除 `import top.yourzi.dialog.editor.gui.VNDialogEditorScreen;`（若无其它用途，grep 确认）和 `import net.minecraft.client.gui.screens.Screen;`（recoverAppearanceTab 内唯一用途）。
- **不删** `import top.yourzi.dialog.editor.gui.Screen` 之类若其它方法仍用。删除前 grep 确认每个 import 的其它引用点。

**文件 2**：[LogicPropertyPage.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java)
- 删除 `recoverLogicTab()` 方法（L564-L569）。
- 删除 4 处调用：L424（onPickFromInventory）、L539（openOptionEditor onSave）、L547（onAudioBrowse）、L560（openNodePicker）。
- 删除 `import top.yourzi.dialog.editor.gui.VNDialogEditorScreen;` 和 `import net.minecraft.client.gui.screens.Screen;`（grep 确认无其它用途）。

**文件 3**：[VNDialogEditorScreen.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java)
- 删除 `pendingTabIndex` 字段（L66）。
- 删除 `setPropertyPanelTab(int)` 方法（L72-L77）。
- `init()` 中 L107 `this.propertyPanel.setActiveTab(this.pendingTabIndex);` 改为 `this.propertyPanel.setActiveTab(EditorScreenState.get().getActivePropertyTab());`（从单例恢复，与 PropertyPanel 构造时读取一致）。
- L168 `setOnTabChangeListener(index -> this.pendingTabIndex = index)` 整行删除（PropertyPanel.setActiveTab 已写回 EditorScreenState，无需双重跟踪）。
- 确认无其它 `pendingTabIndex` 引用后清理。

**改动范围**：3 文件，纯删除 + 1 处改读单例。无行为变化（死代码本就不执行）。

### 项 2：接入 EditorScreenState 死字段到 DialogTreeWidget（C 项）

**目标**：让 `selectedNodeId`/`treeScrollOffset` 真正生效，子屏返回后选中节点与树滚动位置保留。

**文件 1**：[DialogTreeWidget.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/widget/DialogTreeWidget.java)
- `setSequence(DialogSequence)`（L51-L56）：不再无条件重置。改为：
  - 仍重置 `selectedIndex=-1`、`scrollOffset=0` 作为默认。
  - 紧接着从 `EditorScreenState.get()` 读取 `selectedNodeId`，若非 null 且在 `visibleNodes` 中找到匹配 entry，设 `selectedIndex` 为该索引；读 `treeScrollOffset` 并 clamp 到 `[0, maxScroll]` 赋给 `scrollOffset`。
  - 保留 `buildTree()` 调用。
- `mouseClicked` 选中节点处（L299 `this.selectedIndex = index;` 之后）：写回 `EditorScreenState.get().setSelectedNodeId(node.entry.getId());`。
- `mouseClicked` 取消选中处（L312 `this.selectedIndex = -1;` 之后）：写回 `EditorScreenState.get().setSelectedNodeId(null);`。
- `mouseScrolled`（L326）滚动后：写回 `EditorScreenState.get().setTreeScrollOffset(this.scrollOffset);`。
- 重命名成功后（L281 附近 setSelectedIndex 之后）：写回新 ID 到 `EditorScreenState.get().setSelectedNodeId(newId);`。
- `import top.yourzi.dialog.editor.gui.EditorScreenState;`（同包 `top.yourzi.dialog.editor.gui`，DialogTreeWidget 在 `...gui.widget` 子包，需显式 import）。

**文件 2**：[VNDialogEditorScreen.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java)
- `onClose`（L817 `EditorScreenState.get().reset();` 已存在）：确认 reset 在最后，行为正确（编辑器整体关闭清空，下次打开为初始状态）。无需改动，仅验证。
- 切换序列（switchToSequence 或 setSequence 调用处）：切换到不同对话序列时，原选中节点 ID 不应跨序列保留。在切换序列的代码路径里调 `EditorScreenState.get().setSelectedNodeId(null); EditorScreenState.get().setTreeScrollOffset(0);`，避免新序列里找不到旧 ID 而空选中。需先 grep 定位 switchToSequence 实现。

**改动范围**：2 文件。DialogTreeWidget 加读写单例 + setSequence 恢复逻辑；VNDialogEditorScreen 在序列切换处清空节点/滚动状态。

### 项 3：滚动条拖拽 + lerp 平滑滚动（A 项）

**目标**：PropertyPanel、DialogTreeWidget、PortraitListScreen 三处滚动条支持鼠标拖拽，滚动用指数 lerp 平滑过渡。

**文件 1（新增）**：[EditorRenderHelper.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/EditorRenderHelper.java)
- 新增滚动状态辅助（封装拖拽 + 平滑滚动样板，避免三处重复）：
```java
/** 滚动条交互状态（拖拽标志 + 平滑显示值）。建议作为宿主控件的字段持有。 */
public static final class ScrollState {
    public boolean dragging;
    public float display;       // 平滑显示值（浮点）
    public ScrollState() {}
    /** 每帧推进 display 向 offset 逼近，差值<0.5 吸附。dt 秒。返回 round 后的显示偏移。 */
    public int tick(float offset, float dt) {
        if (dt <= 0) { display = offset; return Math.round(offset); }
        float lerp = 1.0f - (float) Math.exp(-dt * 18.0f);
        display += (offset - display) * lerp;
        if (Math.abs(offset - display) < 0.5f) display = offset;
        return Math.round(display);
    }
    public void reset(float offset) { display = offset; }
}
/** 命中检测：鼠标是否在垂直滚动条轨道上。 */
public static boolean isOnVerticalScrollbar(double mx, double my, int trackX, int trackY, int trackW, int trackH) {
    return mx >= trackX && mx < trackX + trackW && my >= trackY && my < trackY + trackH;
}
/** 按鼠标 Y 在轨道中的比例映射到 scrollOffset，返回 clamp 后的目标 offset。 */
public static int offsetFromMouseY(double mouseY, int trackTop, int trackBottom, int maxScroll) {
    double t = net.minecraft.util.Mth.clamp((mouseY - trackTop) / Math.max(1, trackBottom - trackTop), 0.0, 1.0);
    return (int) (t * maxScroll);
}
```
- 现有 `drawVerticalScrollbar` 不变（仍用 EditorTheme.SCROLLBAR_TRACK/THUMB），但调用方改用 `ScrollState.tick` 的返回值做 translate。

**文件 2**：[PropertyPanel.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/widget/PropertyPanel.java)
- 新增字段：`private final EditorRenderHelper.ScrollState scrollState = new EditorRenderHelper.ScrollState();`
- `renderWidget`（L195-L213）：
  - 计算 `dt`（用 `System.nanoTime()` 维护 `lastFrameNanos`，首帧 0 → tick 直接吸附）。
  - `int displayOffset = scrollState.tick(scrollOffset, dt);`
  - L197 `translate(0, -displayOffset, 0)` 替代 `-scrollOffset`。
  - L199 `mouseY + displayOffset` 替代 `+ scrollOffset`（悬停/点击补偿）。
  - 滚动条渲染（L207-L212）改用 `displayOffset` 计算 thumbY，拖拽中 thumb 用更亮色（`0xFFFFFFFF`）。
- `mouseClicked`：在现有逻辑前加 `if (maxScroll>0 && EditorRenderHelper.isOnVerticalScrollbar(mx,my, trackX, pageTop, SCROLLBAR_WIDTH, pageH)) { scrollState.dragging=true; scrollOffset=EditorRenderHelper.offsetFromMouseY(my, pageTop, pageTop+pageH, maxScroll); return true; }`
- `mouseDragged`（新增 override）：`if (scrollState.dragging) { scrollOffset=EditorRenderHelper.offsetFromMouseY(mouseY, pageTop, pageTop+pageH, getMaxScroll()); clampScroll(); return true; } return super.mouseDragged(...);`
- `mouseReleased`（新增 override）：`if (scrollState.dragging) { scrollState.dragging=false; return true; } return super.mouseReleased(...);`
- `setActiveTab`/`bindTo` 重置滚动时同步 `scrollState.reset(0);`。

**文件 3**：[DialogTreeWidget.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/widget/DialogTreeWidget.java)
- 新增字段：`private final EditorRenderHelper.ScrollState scrollState = new EditorRenderHelper.ScrollState();` + `private long lastFrameNanos;`
- `renderWidget`（L188-L239）：
  - 计算 dt，`int displayOffset = scrollState.tick(scrollOffset, dt);`
  - L199 `yOffset = this.getY() - displayOffset;`
  - L232 滚动条 thumbY 用 `displayOffset` 计算，拖拽中亮色。
- `mouseClicked`（L242）：在现有 `isMouseOver` 检查后，加滚动条命中检测（trackX = `getX()+getWidth()-SCROLLBAR_WIDTH`，track 覆盖全高），命中置 `scrollState.dragging=true` 并按 mouseY 映射 scrollOffset。
- 新增 `mouseDragged`/`mouseReleased` override，同 PropertyPanel 模式。
- `setSequence` 重置时 `scrollState.reset(scrollOffset);`（项 2 已恢复 scrollOffset，reset 让 display 同步避免首帧跳变）。

**文件 4**：[PortraitListScreen.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/PortraitListScreen.java)
- 左列表滚动（L780-L785 区域）同样加 ScrollState + 拖拽。需先读该文件确认 `scrollOffset` 字段名、`mouseClicked`/`mouseDragged`/`mouseReleased` 现状（PortraitListScreen 是 Screen 子类，事件直接 override）。
- 中/右面板若无滚动则不动。

**改动范围**：1 新增辅助 + 3 宿主控件。模式统一，每处约 +30 行（字段+tick+命中+dragged+released）。

## 假设与决策

1. **本轮只做收尾**：B/C/A 三项是第一轮的未竟收尾 + 一个明确交互痛点。D（undo 模型）/G（异步）/H（剖析）/J（组件）留后续单独立项，避免本轮膨胀。
2. **recoverXxxTab 删除而非保留**：代码级证据确认其 `instanceof` 永不命中（子屏回调时 screen 仍是子屏），是纯死代码，删除无行为变化。保留只会误导后续维护者。
3. **pendingTabIndex 删除**：与 EditorScreenState.activePropertyTab 双重跟踪，PropertyPanel.setActiveTab 已写回单例，init 从单例读，pendingTabIndex 冗余。
4. **EditorScreenState 两字段接入而非删除**：用户决策。接入后子屏返回保留选中节点/树滚动，是 EditorScreenState 的原始意图。
5. **序列切换时清空节点/滚动状态**：selectedNodeId 跨序列无意义（不同序列 ID 体系不同），切换序列时清空避免空选中。
6. **滚动平滑用 lerp 而非动画库**：复刻 Sparkle 的 `1 - exp(-dt*18)` 指数 lerp，自包含在 ScrollState.tick，无需引入动画依赖。
7. **ScrollState 作为控件字段持有**：每控件一个实例，状态隔离，不引入全局。dt 由控件自己用 nanoTime 算。
8. **拖拽中 thumb 高亮**：复刻 Sparkle `draggingRowScrollbar ? 0xFFFFFFFF : 0xFFAAAAAA`，视觉反馈拖拽状态。
9. **不重构 EditorRenderHelper.drawVerticalScrollbar 签名**：现有签名（返回 bool、用 EditorTheme 色）保持，仅调用方改用 displayOffset。新增 ScrollState/isOnVerticalScrollbar/offsetFromMouseY 三个独立辅助。

## 验证步骤

每项完成后不本地构建，提交 GitHub 由 Actions 构建。逐项验证清单：

- **项 1**：编译通过（死代码删除无副作用）；功能验证：打开外观标签 → 浏览背景 → 选图返回 → 标签仍停留外观（由 EditorScreenState 恢复，不再依赖死代码）；逻辑标签同理（选项编辑/节点选择/音频浏览/背包选取返回后停留逻辑）。
- **项 2**：编译通过；选中树中某节点 → 打开立绘编辑子屏 → 返回 → 该节点仍选中（高亮）；树滚动到某位置 → 子屏往返 → 滚动位置保留；切换到另一对话序列 → 选中清空（不残留旧 ID）；关闭编辑器重开 → 选中/滚动为初始（reset 生效）。
- **项 3**：编译通过；属性面板内容超长时，鼠标按住滚动条滑块拖拽 → 内容实时跟随；松开后停止；滚轮滚动 → 内容平滑过渡（非硬切）；拖拽中滑块变亮；对话树、PortraitListScreen 左列表同理；拖拽滚动条时不误触选中树节点或列表项。

全部完成后整体回归：新建/保存/读取对话序列、立绘位置微调、背景/音频/选项/物品/节点子屏往返、滚动条拖拽与滚轮、标签切换、关闭重开，确认无功能回归。
