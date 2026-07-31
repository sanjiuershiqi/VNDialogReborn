# 编辑器 UI 地基升级计划

## 目标

借鉴 Sparkle-Morpher（neo1.21.1 分支，已克隆至 `/workspace/Sparkle-Morpher`）的 UI 架构经验，针对 VNDialogReborn 编辑器当前 UI 架构的痛点，落地**基础地基**改造：统一渲染辅助、统一纹理缓存、DropdownWidget 自包含化、EditorTheme 增强、AbstractPropertyPage 抽象、子屏状态保持单例。

本计划**不**改动整体布局方式（保留现有硬编码坐标 + PageLayout 混合），**不**引入完整 LayoutManager，**不**做主题换肤，控制风险、分批可验证。

## 当前状态分析（痛点摘要）

基于对 16 个核心文件的审阅，本次计划针对的痛点：

1. **渲染辅助重复**：4+ 处手画 1px 边框（`graphics.fill` 画 4 条线），4+ 处手画滚动条（公式 `max(10, h*h/totalH)` 重复）。
2. **纹理缓存重复**：[AppearancePropertyPage.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/AppearancePropertyPage.java) 与 [PortraitListScreen.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/PortraitListScreen.java) 各自维护一份几乎完全相同的静态 `textureCache` + `sizeCache` LRU（连 key 算法 `file.getAbsolutePath().toLowerCase() + "|" + lastModified()` 都一字不差），释放也要分别调用。
3. **DropdownWidget 抽象泄漏**：「父容器必须手动在 scissor 外调 renderPopup」契约脆弱，PropertyPanel 和 PortraitListScreen 都得手写样板，新增页面极易遗漏。
4. **EditorTheme 颜色裸 int 无 alpha 辅助**：代码散落 `0x33FFFFFF`/`0xCC000000`/`0x804A9EFF` 等半透明魔法数字，绕过主题系统。
5. **PropertyPage 无基类样板重复**：三个实现各自维护 `x/y/width/visible/currentEntry` 字段，各自写 `setResponder(null)→setValue→setResponder` 回环规避模板。
6. **子屏状态丢失靠 hack**：属性页打开子屏后用 `recoverXxxTab()` → `editor.setPropertyPanelTab(N)` 恢复标签焦点，绕过 Screen 状态机。

## 借鉴的 Sparkle-Morpher 模式

- **渲染样式工具类**：[RoulettePanelStyle.java](file:///workspace/Sparkle-Morpher/src/main/java/com/micaftic/morpher/core/gui/RoulettePanelStyle.java) 把 `fill/border/glassPanel` 等渲染动作集中，任何 Screen 直接静态调用复用。
- **浮层协议**：[OptionRow.java](file:///workspace/Sparkle-Morpher/src/main/java/com/micaftic/morpher/core/gui/OptionRow.java) 的 `isOverlayOpen()/closeOverlay()/renderOverlay()/overlayMouseClicked()/overlayMouseScrolled()` 五件套，让下拉浮层自管理层级，父 Screen 统一路由事件。
- **屏幕状态集中化**：[ModelPanelState.java](file:///workspace/Sparkle-Morpher/src/main/java/com/micaftic/morpher/client/gui/ModelPanelState.java) 把一个屏所有 UI 状态打包成单例字段，跨屏重建不丢状态。
- **配置项 dirty 模型**：[Option.java](file:///workspace/Sparkle-Morpher/src/main/java/com/micaftic/morpher/core/gui/Option.java) 的 pending/dirty + apply/undo，本次仅借鉴其「状态集中」思想用于 EditorScreenState。

## 改造项（6 项，按依赖顺序）

### 项 1：EditorRenderHelper 渲染辅助工具类（新增）

**文件**：新增 `src/main/java/top/yourzi/dialog/editor/gui/EditorRenderHelper.java`

**内容**：纯静态工具类，集中渲染动作，消除 4+ 处边框/滚动条重复：
```java
public final class EditorRenderHelper {
    /** 画 1px 边框矩形（4 条线），替代各控件手画。 */
    public static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color);
    /** 画填充矩形 + 1px 边框的组合（背景面板常用）。 */
    public static void fillWithBorder(GuiGraphics g, int x, int y, int w, int h, int fill, int border);
    /** 画垂直滚动条（含 thumb 高度计算 max(10, h*h/totalH)），返回 true 表示命中滚动区域。 */
    public static boolean drawVerticalScrollbar(GuiGraphics g, int x, int y, int barW, int viewH, int totalH, int scrollOffset);
    /** alpha 混合辅助：把完全不透明色按 alpha 比例降透明度，消除 0x33FFFFFF 魔法数字。 */
    public static int withAlpha(int argb, int alpha); // alpha 0-255
    public static int withAlphaRatio(int argb, float ratio); // ratio 0-1
}
```

**改动范围**：仅新增 1 个文件。后续项逐步替换调用点，本项不强制全量替换。

### 项 2：EditorTheme 增加 alpha 辅助（修改）

**文件**：[EditorTheme.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/util/EditorTheme.java)

**内容**：在现有常量基础上补充「语义化半透明色」常量，收编散落的魔法数字：
```java
// 语义化半透明色（替代散落的 0x33FFFFFF/0xCC000000/0x804A9EFF 等）
public static final int OVERLAY_MASK = 0xCC000000;      // 模态遮罩
public static final int HOVER_TINT = 0x33FFFFFF;         // 悬停提亮叠层
public static final int ACCENT_TINT = 0x804A9EFF;        // 强调半透明
public static final int DIVIDER = 0x40FFFFFF;            // 分隔线
public static final int SCROLLBAR_TRACK = 0x33000000;    // 滚动条轨道
public static final int SCROLLBAR_THUMB = 0x80B0B0B0;    // 滚动条滑块
```
同时把 `EditorTheme.drawSectionHeader` 内部改用 `EditorRenderHelper`（项 1 完成后）。

**改动范围**：1 个文件加常量，无行为变化。

### 项 3：统一 TextureCache 服务（新增 + 替换）

**文件**：新增 `src/main/java/top/yourzi/dialog/editor/util/TextureCacheService.java`

**内容**：把两份重复的静态 LRU 缓存合并为单一服务，提供加载/缓存/尺寸/释放统一入口：
```java
public final class TextureCacheService {
    /** 加载文件纹理并缓存，返回 ResourceLocation + 宽高；命中缓存直接返回。 */
    public static record CachedTexture(ResourceLocation location, int width, int height) {}
    public static CachedTexture load(File file);  // key = absPath.toLowerCase + "|" + lastModified
    /** 仅查询尺寸（不加载纹理），用于已缓存时恢复宽高。 */
    public static int[] getSize(String cacheKey);
    /** 释放所有缓存纹理，编辑器关闭时调用。 */
    public static void releaseAll();
}
```
内部仍用 `LinkedHashMap` + `removeEldestEntry` LRU（MAX_CACHE_SIZE=30），逻辑与现有两份一致，但集中一处。

**改动范围**：
- 新增 1 个文件。
- 修改 [AppearancePropertyPage.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/AppearancePropertyPage.java)：删除私有 `textureCache/sizeCache/loadTexture/releaseTextures`，改调 `TextureCacheService`。
- 修改 [PortraitListScreen.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/PortraitListScreen.java)：同上。
- 修改 [VNDialogEditorScreen.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java)：`onClose` 中 `releaseTextures` 调用改为 `TextureCacheService.releaseAll()` 一处。

### 项 4：DropdownWidget 自包含浮层（重构）

**文件**：[DropdownWidget.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/widget/DropdownWidget.java)

**内容**：借鉴 Sparkle 的浮层协议，消除「父容器手动调 renderPopup」契约。改为 DropdownWidget 自己管理浮层渲染层级：
- 新增 `isPopupOpen()` / `closePopup()` 公开 API（替代 `renderPopup` 由父调用的隐式契约）。
- `renderWidget` 内部：若 `expanded`，在画完自身后**自行**用 `g.pose().translate(0,0,200)` 提升 z 并画浮层（不再要求父容器在 scissor 外调用）。
- 关键：浮层绘制前调用 `g.disableScissor()` 临时解除父级 scissor（保存当前裁剪状态，绘制后恢复），确保弹出列表不被父级滚动裁剪遮挡。注：NeoForge 1.21.1 的 GuiGraphics 没有公开 scissor 状态查询 API，采用「DropdownWidget 展开时通知父容器关闭内容 scissor」的回调机制（`Consumer<Boolean> onPopupToggle`），父容器在 toggle 为 true 时跳过内容 scissor。这比「父容器手动调 renderPopup」更显式且不易遗漏。
- 父容器（PropertyPanel、PortraitListScreen）改为：注册 `onPopupToggle` 回调控制 scissor；`mouseClicked/mouseScrolled` 优先路由给所有展开的 dropdown。

**改动范围**：
- 重构 1 个控件文件。
- 修改 PropertyPanel、PortraitListScreen 的 dropdown 使用方式（删除手动 `renderPopup` 调用，改为回调控制 scissor）。

### 项 5：AbstractPropertyPage 基类（新增 + 迁移）

**文件**：新增 `src/main/java/top/yourzi/dialog/editor/gui/property/AbstractPropertyPage.java`

**内容**：抽取三个 PropertyPage 实现的公共样板：
```java
public abstract class AbstractPropertyPage implements PropertyPage {
    protected int x, y, width, height;
    protected boolean visible = false;
    protected DialogEntry currentEntry;
    protected final Font font;
    /** 设置 EditBox 值且不触发 responder（收编 setResponder(null)→setValue→setResponder 模板）。 */
    protected void setBoxSilent(EditBox box, String value);
    /** 设置 Checkbox 值且不触发回调。 */
    protected void setCheckboxSilent(CycleButton<?> btn, boolean value);
    // init/render/children/bindTo/unbind/refreshDisplay/setVisible/getContentHeight 仍由子类实现
    // 但 setVisible 可在此基类默认遍历 children() 设值
}
```

**改动范围**：
- 新增 1 个抽象基类。
- 修改 TextPropertyPage、AppearancePropertyPage、LogicPropertyPage 改为 `extends AbstractPropertyPage`，删除重复字段和方法，改用 `setBoxSilent/setCheckboxSilent`。此为机械重构，不改变行为。

### 项 6：EditorScreenState 状态单例（新增 + 接入）

**文件**：新增 `src/main/java/top/yourzi/dialog/editor/gui/EditorScreenState.java`

**内容**：借鉴 Sparkle 的 `ModelPanelState`，集中编辑器跨屏 UI 状态：
```java
public final class EditorScreenState {
    private static final EditorScreenState INSTANCE = new EditorScreenState();
    public static EditorScreenState get() { return INSTANCE; }
    // 集中管理跨屏需保持的状态
    private int activePropertyTab = 0;          // 替代 setPropertyPanelTab hack
    private String selectedNodeId = null;        // 当前选中节点
    private int treeScrollOffset = 0;
    private String lastPortraitFilter = "";      // PortraitListScreen 等
    // ... 按需扩展
    public int getActivePropertyTab() / setActivePropertyTab(int);
    // 编辑器整体关闭时清空
    public void reset();
}
```

**改动范围**：
- 新增 1 个文件。
- 修改 [PropertyPanel.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/widget/PropertyPanel.java)：`setActiveTab` / 初始化时读写 `EditorScreenState.get().getActivePropertyTab()`，标签切换不再丢失。
- 修改各属性页的 `recoverXxxTab()` 调用：改为 `EditorScreenState.get().setActivePropertyTab(N)`，子屏返回后 PropertyPanel 自动从单例恢复，删除 `editor.setPropertyPanelTab` hack。
- 修改 [VNDialogEditorScreen.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java)：`onClose` 调 `EditorScreenState.get().reset()`；`init` 时从单例恢复 selectedNodeId/treeScroll。

## 假设与决策

1. **不动布局方式**：本次不引入 LayoutManager，保留硬编码坐标 + PageLayout 现状，避免大面积坐标迁移风险。后续可单独立项。
2. **不引入换肤**：EditorTheme 仍是常量类，仅补半透明色常量，不加运行时换肤。
3. **DropdownWidget 的 scissor 处理用回调而非反射**：NeoForge GuiGraphics 无公开 scissor 状态查询，用 `onPopupToggle` 回调让父容器显式控制 scissor，不使用反射访问私有字段。
4. **TextureCacheService 为静态服务**：与现有静态缓存语义一致，不引入依赖注入。编辑器单实例运行，静态服务足够。
5. **状态单例范围克制**：EditorScreenState 只收编明确会丢失的少量状态（activeTab/selectedNode/treeScroll），不一次性集中所有 UI 状态，按需扩展。
6. **机械重构优先**：项 3/5/6 的迁移保持行为不变，逐文件验证，不夹带功能改动。

## 验证步骤

每项完成后本地不构建，提交 GitHub 由 Actions 构建。逐项验证清单：

- **项 1**：编译通过（新增工具类无调用方依赖）。
- **项 2**：编译通过；`drawSectionHeader` 渲染不变（视觉对比）。
- **项 3**：编译通过；编辑器中立绘预览、背景预览均正常显示且缓存命中；反复切换不同图片无 native 崩溃（验证 LRU 释放）；编辑器关闭后 reopen 无纹理泄漏。
- **项 4**：编译通过；属性页内所有下拉框展开/选择/滚动正常；下拉浮层不被属性面板滚动 scissor 裁剪；PortraitListScreen 的位置/动画下拉框正常；点击外部正常收起。
- **项 5**：编译通过；三个属性页绑定/解绑/刷新行为不变；EditBox/Checkbox 静默设置无回环触发；标签切换属性页显示正常。
- **项 6**：编译通过；打开属性页切到「外观」标签 → 打开立绘编辑子屏 → 返回 → 标签仍停留在「外观」（不再回退到「文本」）；关闭编辑器重开，选中节点/树滚动恢复；`reset()` 在 onClose 调用，重开后为初始状态。

全部完成后整体回归：新建/保存/读取对话序列、立绘位置微调、缩放、背景设置、命令/物品/选项编辑、子屏往返，确认无功能回归。
