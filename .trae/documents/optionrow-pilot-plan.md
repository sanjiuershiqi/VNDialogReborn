# OptionRow 值模型试点改造计划

## 摘要

本轮聚焦落地此前分析中遗留的 **OptionRow 值模型**改进方向（布局自适应与模态浮层本轮不做，留待后续）。

目标：从 Sparkle-Morpher 移植 `Option<T>` 值模型与 `BooleanOptionRow` 控件，在 [LogicPropertyPage](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java) 的 `endDialog` / `allowSkip` 两个复选框上试点，验证：
1. 统一的「值—控件」抽象，取代当前「重建 Checkbox 以规避 silent-set」的脆弱模式。
2. 行级 dirty 视觉反馈（字段自上次保存/绑定以来是否变更）。
3. 顺带修复试点字段的「字段编辑不标记序列 dirty」缺陷（当前仅增删节点调 `markDirty`，复选框/EditBox 编辑不标记）。

**不做**：不引入完整 pending 缓冲（VNDialog 是即时回写架构），不迁移 EditBox/下拉框等其它字段（留后续），不改布局，不改模态浮层。

## 当前状态分析（差距定位）

### 现状 1：Checkbox 用「重建实例」规避 silent-set

[LogicPropertyPage.buildEndDialogCheck](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java#L693-L704) / [buildAllowSkipCheck](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java#L709-L720) 在 [refreshDisplay](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java#L202-L223)（L210-211）和 [unbind](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java#L188-L199)（L192-193）中**重建 Checkbox 实例**，用新实例初始值即目标值的方式避免 `onPress + suppressCallback` 状态机。这是与 `AbstractPropertyPage.setBoxSilent` 同一类问题的另一种解法（[setBoxSilent](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/AbstractPropertyPage.java#L41-L45) 用 setResponder 回环规避）。

对比 Sparkle：`Option` 持有 getter/setter，控件读 `option.get()`、写 `option.setPending()`，刷新时调 `snapshot()` 重置基线即可，无需重建控件。Sparkle 的 `LiveOption`（[LiveOption.java](file:///workspace/Sparkle-Morpher/src/main/java/com/micaftic/morpher/core/gui/molang/LiveOption.java)）重写 `setPending` 内部立即 `apply()`，正是即时回写模式的范例。

### 现状 2：字段编辑不标记序列 dirty（缺陷）

`markDirty` 仅在 [onAddNode L339](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java#L339) 与 [onDeleteEntry L608](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java#L608) 调用。Checkbox 的 `onValueChange`（[L700](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java#L698-L703)）只写 `currentEntry.setEndDialog(value)`，**不调 markDirty**。结果：切换复选框后标签页不显示 `*`，"有关闭前未保存修改"判断（[hasUnsavedChanges L423](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java#L423)）也不触发——用户可能丢失字段编辑。

对比：Sparkle `Option.setPending` 内部联动 dirty 状态，`OptionScreen` footer 据此启用 apply 按钮。VNDialog 借鉴此模式，让 `Option` 在值变更时通过回调触发 `markDirty`，使行级 dirty 与序列级 dirty 一致。

### 现状 3：无行级 dirty 视觉

当前 dirty 反馈仅在标签页标题前缀 `*`（[rebuildTabButtons L220](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java#L219-L220)），粒度为序列级。Sparkle `OptionRow.renderWidget`（[OptionRow.java](file:///workspace/Sparkle-Morpher/src/main/java/com/micaftic/morpher/core/gui/OptionRow.java)）据 `option.isDirty()` 改背景色与文字色（dirty → 纯白文字 + 更深背景）。VNDialog 试点在复选框行引入字段级 dirty 视觉。

## 借鉴的 Sparkle-Morpher 模式

- **Option 三态模型**：`getter`/`setter`/`pending`/`dirty` + `setPending`/`apply`/`undo`（[Option.java](file:///workspace/Sparkle-Morpher/src/main/java/com/micaftic/morpher/core/gui/Option.java)）。VNDialog 适配为即时回写变体：去掉 `pending`（即时模式下 `getter.get()` 即当前值），`set` 内部立即写回 + 重算 dirty + 触发 onDirty 回调；`snapshot` 重置基线。
- **LiveOption 即时 apply**：[LiveOption](file:///workspace/Sparkle-Morpher/src/main/java/com/micaftic/morpher/core/gui/molang/LiveOption.java) 重写 `setPending` 立即 `apply()`。VNDialog 的 Option 默认即即时模式（无需子类化）。
- **BooleanOptionRow**：[BooleanOptionRow.java](file:///workspace/Sparkle-Morpher/src/main/java/com/micaftic/morpher/core/gui/components/BooleanOptionRow.java) 读 `option.get()` 渲染，`onClick` 调 `option.setPending(!get())`。VNDialog 移植此控件，dirty 视觉用 EditorTheme 语义色。
- **OptionGroup.apply/undo 传播**：Sparkle 用组聚合。VNDialog 试点仅 2 个字段，不引入 OptionGroup，直接在 LogicPropertyPage 管理；后续字段迁移到一定规模再引入。

## 改造项（按依赖顺序）

### 项 1：新增 `Option<T>` 值模型（即时回写变体）

**新文件**：`/workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/Option.java`

**设计**（适配 VNDialog 即时回写，非 Sparkle 的 pending 缓冲）：

```java
package top.yourzi.dialog.editor.gui.property;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 字段值模型：getter/setter 读写字段，baseline 记录上次保存/绑定时的值，
 * dirty 表示当前值 != baseline。即时回写：set() 立即写回数据源并触发 onDirty。
 * 借鉴 Sparkle Option 的三态语义，去掉 pending（即时模式下 getter.get() 即当前值）。
 */
public class Option<T> {
    private final Supplier<T> getter;
    private final Consumer<T> setter;
    private final Runnable onDirty;       // 值变更时触发（用于 markDirty 序列），可为 null
    private T baseline;                    // 上次 snapshot 时的值
    private boolean dirty;

    public Option(Supplier<T> getter, Consumer<T> setter, Runnable onDirty) {
        this.getter = getter;
        this.setter = setter;
        this.onDirty = onDirty;
        this.baseline = getter.get();      // 构造时即以当前值为基线
    }

    public T get() { return getter.get(); }

    /** 即时写回 + 重算 dirty + 触发 onDirty（若变脏）。 */
    public void set(T value) {
        setter.accept(value);
        boolean nowDirty = !Objects.equals(value, baseline);
        if (nowDirty && !this.dirty && onDirty != null) {
            onDirty.run();                 // 仅在「由干净变脏」时触发一次，markDirty 幂等
        }
        this.dirty = nowDirty;
    }

    /** 重置基线为当前值，清除 dirty。绑定/保存后调用。 */
    public void snapshot() {
        this.baseline = getter.get();
        this.dirty = false;
    }

    public boolean isDirty() { return dirty; }
}
```

**关键决策**：
- 不设 `pending`/`apply`/`undo`：即时模式下无 pending 缓冲，写即生效；undo 需要历史栈，超出试点范围。
- `onDirty` 仅在「由干净变脏」触发一次（避免每次 set 都调 markDirty，尽管 markDirty 幂等；保持语义清晰）。
- `baseline` 在构造时初始化为 `getter.get()`，故 Option 必须在 `currentEntry` 已绑定后构造，或 getter 对 null 做防护（见项 4）。

### 项 2：新增 `BooleanOptionRow` 控件

**新文件**：`/workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/widget/BooleanOptionRow.java`

**设计**（移植 Sparkle BooleanOptionRow，视觉适配 EditorTheme）：

```java
package top.yourzi.dialog.editor.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.gui.property.Option;
import top.yourzi.dialog.editor.util.EditorTheme;

/**
 * 布尔选项行：自绘复选框 + 标签，绑定 Option<Boolean>。
 * 借鉴 Sparkle BooleanOptionRow：读 option.get() 渲染，onClick 翻转 option.set。
 * dirty 视觉：dirty 时标签 TEXT_PRIMARY、复选框边框 ACCENT；干净时标签 TEXT_SECONDARY、边框 BORDER_LIGHT。
 */
public class BooleanOptionRow extends AbstractWidget {
    private static final int BOX_SIZE = 12;
    private final Option<Boolean> option;
    private final Font font;

    public BooleanOptionRow(int x, int y, int width, int height, Component label, Option<Boolean> option, Font font) {
        super(x, y, width, height, label);
        this.option = option;
        this.font = font;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean checked = Boolean.TRUE.equals(option.get());
        boolean dirty = option.isDirty();
        int boxX = getX() + 2;
        int boxY = getY() + (getHeight() - BOX_SIZE) / 2;
        int boxColor = EditorTheme.BG_SURFACE;
        int borderColor = dirty ? EditorTheme.ACCENT : EditorTheme.BORDER_LIGHT;
        g.fill(boxX, boxY, boxX + BOX_SIZE, boxY + BOX_SIZE, boxColor);
        g.renderOutline(boxX, boxY, BOX_SIZE, BOX_SIZE, borderColor);
        if (checked) {
            // 选中：内填 ACCENT + 白色对勾（用文字符号简化，避免画线复杂度）
            g.fill(boxX + 2, boxY + 2, boxX + BOX_SIZE - 2, boxY + BOX_SIZE - 2, EditorTheme.ACCENT);
            g.drawCenteredString(font, "\u2713", boxX + BOX_SIZE / 2, boxY + 1, EditorTheme.TEXT_PRIMARY);
        }
        int labelColor = dirty ? EditorTheme.TEXT_PRIMARY : EditorTheme.TEXT_SECONDARY;
        g.drawString(font, getMessage(), boxX + BOX_SIZE + 6, getY() + (getHeight() - 8) / 2, labelColor);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        // 整行点击均可翻转（与原 Checkbox 行为一致）
        option.set(!option.get());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput n) {
        n.add(NarratedElementType.TITLE, getMessage());
    }
}
```

**关键决策**：
- 整行可点击翻转（原 Checkbox 也是整区域响应），不限定命中复选框小区域，避免小屏难命中。
- 选中态用 ACCENT 内填 + 对勾符号，比 Sparkle 的实心白块更符合 VNDialog 蓝调主题。
- dirty 视觉用边框色 + 标签色双信号，不引入新形状（与编辑器现有视觉语言一致）。

### 项 3：扩展 PropertyPage 生命周期接口

**文件**：[PropertyPage.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/PropertyPage.java)

**改动**：新增两个 default 方法（不破坏现有三个实现）：

```java
/** 设置字段变更回调，字段变脏时触发（用于主屏 markDirty 序列）。 */
default void setDirtyListener(Runnable listener) {}

/** 序列保存成功后调用，各页重置字段 dirty 基线。 */
default void onSequenceSaved() {}
```

**文件**：[AbstractPropertyPage.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/AbstractPropertyPage.java)

**改动**：存储 dirtyListener 供子类（LogicPropertyPage）构造 Option 时引用：

```java
protected Runnable dirtyListener;
@Override
public void setDirtyListener(Runnable listener) { this.dirtyListener = listener; }
```

### 项 4：PropertyPanel 转发生命周期

**文件**：[PropertyPanel.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/widget/PropertyPanel.java)

**改动**：新增两个转发方法，先 `ensureInitialized()` 再转发到所有 tab 页：

```java
public void setDirtyListener(Runnable listener) {
    this.ensureInitialized();
    for (Tab tab : this.tabs) tab.page.setDirtyListener(listener);
}
public void onSequenceSaved() {
    this.ensureInitialized();
    for (Tab tab : this.tabs) tab.page.onSequenceSaved();
}
```

注意：`ensureInitialized()` 在 `setDirtyListener` 被调用时确保页已 init（页内 Option 在 init/bind 时构造，需能拿到 dirtyListener）。若 Option 在 `init()` 构造，则 `setDirtyListener` 必须在 `init()` 之后调用；VNDialogEditorScreen 在 `buildWidgets()` 创建 propertyPanel 后立即 `setDirtyListener`，而 PropertyPanel.init 由 AbstractWidget.ensureInitialized 触发——需保证 setDirtyListener 内部 ensureInitialized 后页已 init。验证：`initializePages()` 调 `page.init(...)`，故 ensureInitialized 后页已 init，dirtyListener 可正确存入 AbstractPropertyPage 字段，子类后续构造 Option 时能读到。但若 Option 在 LogicPropertyPage.init 中构造，而 init 发生在 ensureInitialized 内（早于 setDirtyListener 转发），则构造时 dirtyListener 仍为 null。

**解决**：LogicPropertyPage 的 Option 不在 init 中构造，而在 `bindTo` 时延迟构造（bindTo 晚于 setDirtyListener），或在 init 中构造但 onDirty 用 `() -> { if (dirtyListener != null) dirtyListener.run(); }` 捕获可变字段。采用后者：Option 构造时传入 `() -> { if (this.dirtyListener != null) this.dirtyListener.run(); }`，dirtyListener 由 setDirtyListener 后置注入，调用时再读取。这样顺序无关。

### 项 5：LogicPropertyPage 替换两个 Checkbox

**文件**：[LogicPropertyPage.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java)

**改动**：

1. 字段替换：删除 `endDialogCheck`/`allowSkipCheck`（Checkbox）及 `buildEndDialogCheck`/`buildAllowSkipCheck`、相关位置缓存字段（endDialogCheckX/Y/W、allowSkipCheckX/Y/W）；新增 `endDialogRow`/`allowSkipRow`（BooleanOptionRow）与 `endDialogOption`/`allowSkipOption`（Option<Boolean>）。

2. [init](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java#L86-L179)：在原 endY/skipY 位置构造 BooleanOptionRow（相同 bounds）。Option 在此用 `() -> { if (this.dirtyListener != null) this.dirtyListener.run(); }` 作 onDirty 构造，getter/setter 防护 currentEntry 为 null：
   ```java
   this.endDialogOption = new Option<>(
       () -> this.currentEntry != null && this.currentEntry.isEndDialog(),
       v -> { if (this.currentEntry != null) this.currentEntry.setEndDialog(v); },
       () -> { if (this.dirtyListener != null) this.dirtyListener.run(); });
   this.endDialogRow = new BooleanOptionRow(fieldX, endY, fieldW, EditorTheme.FIELD_HEIGHT,
       Component.translatable("gui.vn_edit.end_dialog"), this.endDialogOption, this.font);
   ```
   allowSkip 同理。删除 endDialogCheckX/Y/W 等缓存字段及赋值。

3. [bindTo](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java#L181-L185)：设 currentEntry 后调 refreshDisplay（不变）。

4. [refreshDisplay](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java#L202-L223)：将 `buildEndDialogCheck`/`buildAllowSkipCheck` 重建改为 `this.endDialogOption.snapshot(); this.allowSkipOption.snapshot();`（行控件读 option.get() 自动反映新值，无需重建）。其余 audioPathBox/visibilityCommandBox 的 setBoxSilent 不变。

5. [unbind](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java#L188-L199)：删除重建 Checkbox 两行，改为 `this.endDialogOption.snapshot(); this.allowSkipOption.snapshot();`（currentEntry 已置 null，getter 防护返回 false，基线重置为 false）。其余不变。

6. [render](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java#L553-L632)：`this.endDialogCheck.render(...)` / `this.allowSkipCheck.render(...)` 改为 `this.endDialogRow.render(...)` / `this.allowSkipRow.render(...)`（L561-562）。

7. [children](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java#L635-L649)：`this.endDialogCheck, this.allowSkipCheck` 改为 `this.endDialogRow, this.allowSkipRow`（L636）。

8. [setVisible](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java#L651-L674)：`this.endDialogCheck.visible`/`this.allowSkipCheck.visible` 改为 `this.endDialogRow.visible`/`this.allowSkipRow.visible`（L655-656）。

9. 新增 `onSequenceSaved` 覆写：
   ```java
   @Override
   public void onSequenceSaved() {
       if (this.endDialogOption != null) this.endDialogOption.snapshot();
       if (this.allowSkipOption != null) this.allowSkipOption.snapshot();
   }
   ```

### 项 6：VNDialogEditorScreen 接线

**文件**：[VNDialogEditorScreen.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java)

**改动**：

1. [buildWidgets](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java#L135-L192)：创建 `propertyPanel` 后立即注入 dirtyListener：
   ```java
   this.propertyPanel.setDirtyListener(() -> this.markDirty(this.currentSequence));
   ```
   放在 `this.addRenderableWidget(this.propertyPanel);`（L191）之后。`markDirty` 对 null/currentSequence 已有防护（[L408-L413](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java#L408-L413) 由 seq.getId() 判定）。

2. [onSave](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java#L344-L369)：在 `this.markClean(this.currentSequence);`（L358）之后追加：
   ```java
   this.propertyPanel.onSequenceSaved();
   ```
   仅在保存成功分支调用（L358 已在 `if (!ok) return;` 之后），保证失败时保留字段 dirty 基线不变。

**不改动**其它 markClean 调用点（onLoad/onImport/closeAll）：这些路径会重新 bindTo/重建序列，字段基线由 bindTo→refreshDisplay→snapshot 重置，无需额外 onSequenceSaved。

## 假设与决策

1. **即时回写，不引入 pending 缓冲**：VNDialog 架构是 UI 变更直接写回 currentEntry。Option 适配为 `set()` 即时写回 + 触发 onDirty，而非 Sparkle 的 `setPending` + 延迟 `apply`。这是与 Sparkle 的关键差异，避免架构冲突。`undo` 暂不实现（需历史栈，超范围）。
2. **onDirty 仅在「由干净变脏」触发一次**：避免每次 set 都调 markDirty；markDirty 本身幂等（Set.add），但语义上「变脏事件」触发一次更清晰。若用户改回基线值，dirty 归 false，但序列 dirty 不清除（安全行为，因其它字段可能脏）。
3. **字段级 dirty 语义 = 自上次保存/绑定以来是否变更**：snapshot 在 bindTo/refreshDisplay（绑定）与 onSave（保存）后调用。切换 entry 会重置字段 dirty（新 entry 基线），但序列级 `*` 仍保留（因 onDirty 已标记序列脏）。二者不矛盾：字段 dirty 是细粒度「这个字段改过」，序列 dirty 是粗粒度「这个序列有未保存改动」。
4. **顺带修复试点字段的序列 dirty 缺陷**：当前 Checkbox/EditBox 编辑不调 markDirty，是既有缺陷。Option 的 onDirty 回调对试点字段修复此问题；其它字段（EditBox/下拉框）仍有此缺陷，留待后续迁移 OptionRow 时一并修复。本轮不扩大范围修其它字段。
5. **dirty 视觉用边框色 + 标签色，不引入新形状**：dirty → 复选框边框 ACCENT + 标签 TEXT_PRIMARY；干净 → 边框 BORDER_LIGHT + 标签 TEXT_SECONDARY。与编辑器现有视觉语言一致（不照搬 Sparkle 的更深背景，因 VNDialog 复选框是内联控件非整行）。
6. **不引入 OptionGroup**：试点仅 2 字段，直接在 LogicPropertyPage 管理。后续迁移规模化再引入 OptionGroup 聚合。
7. **Option 在 init 中构造，onDirty 捕获可变 dirtyListener 字段**：避免 init 与 setDirtyListener 的顺序依赖。onDirty lambda 读 `this.dirtyListener`（运行时求值），setDirtyListener 后置注入即可生效。
8. **不删 AbstractPropertyPage.setBoxSilent**：EditBox 类字段仍用 setBoxSilent，OptionRow 仅试点 Checkbox 字段。setBoxSilent 与 Option 是同一问题的两种解法，未来 EditBox 迁移到 StringOptionRow 后再删 setBoxSilent。
9. **改动顺序**：项1（Option）→ 项2（BooleanOptionRow）→ 项3（接口）→ 项4（PropertyPanel 转发）→ 项5（LogicPropertyPage 替换）→ 项6（主屏接线）。项5 依赖前四项，项6 依赖项4。

## 验证步骤

不本地构建，提交 GitHub 由 Actions 构建。逐项验证：

- **编译通过**：新增 2 文件 + 改 5 文件，无破坏性 API 变更（PropertyPage 新增 default 方法）。
- **功能等价**：选中节点 → endDialog/allowSkip 复选框显示当前值（与改造前一致）；点击复选框 → 值翻转并立即写回 entry（切换节点再切回，值保持）。
- **行级 dirty 视觉**：选中节点 → 复选框边框 BORDER_LIGHT、标签 TEXT_SECONDARY（干净）；点击翻转 endDialog → 边框变 ACCENT、标签变 TEXT_PRIMARY（脏）；保存 → 边框回 BORDER_LIGHT、标签回 TEXT_SECONDARY（基线重置）；再切回原值（不保存）→ 边框回 BORDER_LIGHT（值==基线，dirty 归 false）。
- **序列 dirty 联动**：点击翻转 endDialog → 标签页立即出现 `*` 前缀（修复验证）；保存 → `*` 消失；关闭编辑器时若有未保存字段编辑 → 触发"未保存修改"提示（hasUnsavedChanges 现在能捕获字段编辑）。
- **无回归**：refreshDisplay（切换节点/序列）后复选框值正确显示，无重建闪烁；unbind（无选中节点）后复选框不渲染异常；属性面板滚动/标签切换不受影响；audio/visibility/command/item/option 等其它字段不受影响。
- **顺序无关**：GUI 缩放变化触发 init 重建后，复选框仍正常工作（Option 在 init 重建，dirtyListener 由 setDirtyListener 重新注入）。
