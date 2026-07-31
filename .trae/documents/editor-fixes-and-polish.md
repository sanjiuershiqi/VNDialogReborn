# 编辑器修复与基础完善计划（第三轮）

## 摘要

延续前两轮改造（第一轮 UI 地基升级 `62bc833`、第二轮死代码清理+滚动条拖拽 `29f3e4b`），本轮聚焦**修复已知痛点 + 收尾半成品基础**，不引入搜索功能（用户明确不需要）。

对照 Sparkle-Morpher 的 `Option/OptionRow` 值模型与 `OptionScreen` 交互范式，识别出 VNDialog 编辑器当前**真实存在的 5 个痛点**：死工具未被使用、Checkbox 反射 hack 残留风险、SequencePropertiesScreen 手写布局脆弱、表单静默设置三处重复、关闭标签即删源文件的语义混淆。这些是"基础没打牢"的典型表现，解决后能为后续 OptionRow 大型重构扫清障碍。

**不做**：OptionRow 全量重构（大型改造留后续单独立项）、搜索过滤（用户不需要）、dirty/undo 三态值管理（依赖 OptionRow）、自适应布局（低优先级）。

## 当前状态分析（痛点定位）

基于对编辑器现有代码的逐文件审查：

### 痛点 1：AbstractPropertyPage.setBoxSilent 死工具（半成品）

[AbstractPropertyPage.java#L41-L56](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/AbstractPropertyPage.java#L41-L56) 提供了 `setBoxSilent` 工具方法，注释明说"收编各属性页重复的 `setResponder(null)→setValue→setResponder` 模板"。但 grep 确认**三个属性页都没调用它**：

- [TextPropertyPage.java#L325-L342](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/TextPropertyPage.java#L325-L342) `refreshDisplay` 手写 `speakerBox.setResponder(null)` → `setValue` → `setResponder(闭包)`。
- [AppearancePropertyPage.java#L352-L370](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/AppearancePropertyPage.java#L352-L370) `refreshDisplay` 手写 `backgroundPathBox.setResponder(null)` → `setValue` → `setResponder`。
- [LogicPropertyPage.java#L223-L236](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java#L223-L236) `refreshDisplay` 手写 `audioPathBox`/`visibilityCommandBox` 的 `setResponder(null)→setValue→setResponder`。

工具写了不用 + 三处重复手写 = 半成品状态。这是第一轮抽象提取未完成的遗留。

### 痛点 2：LogicPropertyPage Checkbox 抑制回调的脆弱方案

[LogicPropertyPage.java#L67](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java#L67) 维护 `suppressCheckboxCallback` 标志，[L709-L720](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java#L709-L720) `setCheckboxSelectedSilent` 用 `onPress()` 公开 API 翻转选中态 + 标志抑制回调。

注释 [L707](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java#L707) 写"避免使用反射访问私有字段（C6 反射修复）"——说明这是早期为规避反射而设计的折中。问题：

- `onPress()` 翻转的是**当前选中态的相反值**，若 `selected` 与当前态相同则不翻转（L710 提前 return），逻辑可读性差。
- 抑制靠实例标志 + try/finally 恢复，若 onValueChange 回调内抛异常，标志仍能恢复（finally 兜底），但**回调内若再触发别的 UI 重建**，标志可能在不期望的时机被读取。
- 对比 Sparkle `BooleanOptionRow`（`Option.setPending` 天然分离 pending/source），VNDialog 这套是即时回写 + 抑制回调的权宜之计。

虽然当前能工作，但属于"脆弱基础"，应在引入 OptionRow 前先稳定化。

### 痛点 3：SequencePropertiesScreen 手写布局 + 临时变量恢复

[SequencePropertiesScreen.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/SequencePropertiesScreen.java) 是唯一**没用 PageLayout** 的表单屏幕：

- [L42-L45](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/SequencePropertiesScreen.java#L42-L45) 用 `curTitle`/`curDesc`/`curEffect`/`curStart` 临时变量在 `init()` 重入时保留输入框值（子屏 NodePicker 返回触发）。
- [L47-L70](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/SequencePropertiesScreen.java#L47-L70) 裸 `y += 38` 推进，[L102-L109](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/SequencePropertiesScreen.java#L102-L109) `render()` 里又手写一遍 `y += 38` 绘制标签，两处 Y 必须手动同步。
- 对比 [TextPropertyPage](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/TextPropertyPage.java) 等用 `PageLayout.section()/fieldRow()` 推进，这里是遗漏未迁移的旧代码。

风险：Y 不同步会导致标签与输入框错位；临时变量恢复模式脆弱（加新字段要同步改 init 暂存 + render 绘制 + saveAndClose 回写三处）。

### 痛点 4：关闭标签即删源文件的语义混淆

[onCloseTab](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java#L650-L684) 方法名是"关闭标签"，实际行为是**删除对话 JSON 文件**（[L661-L666](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java#L661-L666) `Files.deleteIfExists`）。

[TabButton.mouseClicked](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java#L886-L891) 右键标签触发 `onRightClick → onCloseTab`，用户右键关闭标签会**永久删除对话文件**，且确认弹窗文案是"删除对话"（[L657-L658](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java#L657-L658)），与"关闭标签"心智不符。

这是真实的误操作风险：用户想"关闭不看了"结果文件没了。Sparkle 的 `deleteModels` 是显式删除操作，关闭标签与删除是分离的。

### 痛点 5：标签栏右键只有单一"删除"动作，无上下文菜单

[TabButton.mouseClicked L886-L891](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java#L886-L891) 右键直接调 `onCloseTab`（实为删除），无菜单选择。常见编辑器标签右键应有：关闭当前/关闭其他/关闭右侧全部/重命名/复制路径等。当前只有删除，且删除语义混淆（痛点 4）。

## 借鉴的 Sparkle-Morpher 模式

- **值与渲染解耦**：`Option<T>`（getter/setter/saver）+ `OptionRow<T>`（只管渲染/交互），`setPending` 写入缓冲而非直接改源，`apply` 才落盘。本轮不引入 Option，但痛点 2 的 Checkbox 稳定化借鉴"缓冲值"思路。
- **统一行布局**：`OptionRow.renderWidget` 标签固定左、控件右对齐，子类只实现 `renderControl`。本轮痛点 1/3 借鉴"静默设置下沉到基类工具"的最小修复，不引入 OptionRow。
- **文件操作分离**：`ModelPanelFileActions` 静态工具返回 `Component` 状态消息。本轮痛点 4 借鉴"关闭标签 ≠ 删除文件"的语义分离。

## 改造项（4 项，按依赖顺序）

### 项 1：让 setBoxSilent 真正生效（消除三处静默设置重复）

**目标**：三个属性页的 `refreshDisplay` 改用 `AbstractPropertyPage.setBoxSilent`，删除手写的 `setResponder(null)→setValue→setResponder` 三段式。

**文件 1**：[TextPropertyPage.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/TextPropertyPage.java)
- `refreshDisplay`（L320-L342 起）：`speakerBox` 的三段式改为一行 `this.setBoxSilent(this.speakerBox, speakerStr, s -> {...})`。注意 responder 闭包体保持不变（parseFormattingCodesToComponent + setSpeaker）。
- 同文件内 `transKeyBox`/`transZhBox`/`transEnBox` 等其它 EditBox 的静默设置（若 refreshDisplay 内有）同样改造，grep `setResponder(null)` 定位全部点位。
- 验证：responder 闭包逻辑零变化，仅外层包装从手写三行变为一行调用。

**文件 2**：[AppearancePropertyPage.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/AppearancePropertyPage.java)
- `refreshDisplay`（L345-L370）：`backgroundPathBox` 的三段式改为 `setBoxSilent`。若 refreshDisplay 内还有其它 EditBox 静默设置，一并改造。

**文件 3**：[LogicPropertyPage.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java)
- `refreshDisplay`（L214-L236）：`audioPathBox`/`visibilityCommandBox` 的三段式改为 `setBoxSilent`。

**改动范围**：3 文件，纯机械替换，每处 3 行变 1 行。无行为变化（setBoxSilent 内部就是那三行）。验证工具方法被实际使用，消除半成品状态。

### 项 2：稳定化 Checkbox 静默设置（消除 suppressCheckboxCallback 标志风险）

**目标**：用更直接的"重建 Checkbox"或"setValue 静默"方式替代 `onPress() + suppressCallback` 方案，降低脆弱性。

**文件**：[LogicPropertyPage.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java)

**方案**（经审查 Checkbox API 后选定）：Minecraft 1.21.1 的 `Checkbox` 没有 `setValue` 静默 API，`onValueChange` 在构造时绑定无法解绑。最稳妥的稳定化是**重建 Checkbox 实例**：在 `refreshDisplay`/`bindTo` 需要静默设置选中态时，移除旧 Checkbox、用新 Checkbox（带正确初始 selected 值 + 同样 onValueChange 回调）替换，重新 add 到 children。

具体改动：
- 删除 `suppressCheckboxCallback` 字段（L67）。
- 删除 `setCheckboxSelectedSilent` 方法（L709-L720）。
- 新增 `rebuildCheckbox(boolean initialSelected, Consumer<Boolean> onToggle, int x, int y)` 工具方法：构造新 Checkbox、设置 bounds、注册 onValueChange、加到 children、返回新实例。
- `endDialogCheck`/`allowSkipCheck` 字段在 `refreshDisplay`/`bindTo` 中改为调用 `rebuildCheckbox` 重建（[L204-L205](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java#L204-L205) setCheckboxSelectedSilent 调用点、[L221-L222](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/property/LogicPropertyPage.java#L221-L222) 调用点）。
- 需确保 children 列表里旧实例被移除（避免事件分发到隐藏旧实例）。检查 `children()` 实现是 List 还是自定义，若需手动 remove 则在 rebuild 前移除。

**风险与缓解**：重建 Checkbox 会丢失焦点状态，但 refreshDisplay 本就是切换 entry 时的全量刷新，焦点本应重置，可接受。需验证 children 列表一致性，避免泄漏旧实例接收事件。

**改动范围**：1 文件，删 1 字段 + 1 方法，新增 1 工具方法 + 2 处调用点改造。中等风险，需仔细验证 children 管理。

### 项 3：SequencePropertiesScreen 迁移到 PageLayout

**目标**：消除手写 Y 推进和临时变量恢复，统一到 PageLayout 游标布局。

**文件**：[SequencePropertiesScreen.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/SequencePropertiesScreen.java)

**改动**：
- `init()`（L37-L78）：用 `PageLayout` 替代裸 `y += 38`。创建 `PageLayout layout = new PageLayout(fieldX - EditorTheme.LABEL_WIDTH - EditorTheme.GAP, 30, FIELD_WIDTH + EditorTheme.LABEL_WIDTH + EditorTheme.GAP)`（对齐属性页的标签+字段布局）。每个字段用 `layout.section()` 或 `layout.fieldRow()` 推进，记录返回 Y 供 init 放置 EditBox 与 render 绘制标签。
- 删除 `curTitle`/`curDesc`/`curEffect`/`curStart` 临时变量（L42-L45）：子屏返回时 `init()` 重入，EditBox 重建，值从 `sequence` 读取即可——但需保留用户**未保存的编辑内容**。方案：在 `openStartNodePicker` 跳转前把当前 EditBox 值写回 `sequence`（临时持久化到 model），返回后从 sequence 读回。或保留临时变量但改用一个 `String[] pendingValues` 数组简化。**决策：保留临时变量恢复（最小改动），仅把 Y 推进迁移到 PageLayout**——避免引入"编辑中写回 model"的语义变化。
- `render()`（L97-L111）：用同一个 `PageLayout` 实例（或在 render 重新算一遍 layout）获取标签 Y，与 init 的 EditBox Y 自动一致。**关键：init 和 render 用相同的 layout 参数，确保 Y 对齐**。推荐在 init 中把 PageLayout 存为字段，render 复用。

**改动范围**：1 文件，Y 推进从裸算术改 PageLayout，消除 init/render 双处 Y 同步负担。低风险（纯布局迁移，行为不变）。

### 项 4：分离"关闭标签"与"删除文件"语义 + 标签右键菜单

**目标**：右键标签弹出上下文菜单，"关闭"只从编辑器移除不删文件，"删除"才是删文件（需二次确认）。

**文件 1**：[VNDialogEditorScreen.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java)

**改动**：
- 新增 `closeTab(int index)` 方法：仅从 `openSequences` 移除、调整 `activeSequenceIndex`、rebuildTabButtons，**不删文件**。若关闭的是脏序列（dirtySequences 含该 id），提示先保存或丢弃（复用 EditorConfirmScreen）。
- 重命名 `onCloseTab(int)` → `onDeleteTab(int)`，语义明确为"删除对话文件"（保留删除文件逻辑 L661-L666）。
- 新增 `TabContextMenu` 内部类（或直接在 mouseClicked 用 `EditorConfirmScreen`/简单浮层）：右键标签时显示菜单项：「关闭标签」「关闭其他」「关闭右侧」「重命名」「复制 ID」「删除对话文件（危险）」。
  - **简化方案**（避免引入完整菜单组件）：右键直接弹 `EditorConfirmScreen` 列出操作选项？不合适。改用**最小可行**：右键弹出 `InputDialogScreen` 风格的选项列表屏幕，或直接在标签栏下方画一个临时浮层菜单。
  - **决策（最小改动）**：本轮先实现"关闭标签（不删文件）"作为右键默认动作 + 保留"删除"作为工具栏或二次确认入口。具体：右键标签 → 弹 EditorConfirmScreen "关闭标签？未保存的修改将丢失" → 确认后调 `closeTab`（不删文件）。删除文件入口移到别处（如序列属性屏加"删除"按钮，或在关闭确认弹窗里加"删除文件"选项）。
- `TabButton.mouseClicked`（L886-L891）右键回调从 `onCloseTab` 改为新的 `onTabRightClick`，内部弹关闭确认。

**文件 2**：语言文件 `assets/lang/zh_cn.json` + `en_us.json`（若存在）
- 新增 `gui.vn_edit.close_tab.title`/`message`（关闭标签确认）、`gui.vn_edit.delete_dialog.title`（已有，删除文件确认，文案明确"将删除 JSON 文件"）。

**风险与缓解**：改变右键行为可能影响用户习惯，但"右键关闭=删除文件"本就是危险行为，分离后更安全。需确认 `dirtySequences` 标记在关闭时正确处理（脏序列关闭前提示保存）。

**改动范围**：1 Java 文件 + 2 语言文件。新增 closeTab 方法 + 右键行为改为关闭确认 + 删除入口迁移。中等风险（行为变化，需验证脏标记流程）。

## 假设与决策

1. **本轮不做 OptionRow 全量重构**：OptionRow 影响 3 个属性页 + SequencePropertiesScreen 共 4 个表单，是大型改造。本轮先把 setBoxSilent 用起来、Checkbox 稳定化、SequenceProperties 迁 PageLayout，为 OptionRow 铺路。OptionRow 留第四轮单独立项。
2. **不引入搜索**：用户明确不需要。
3. **setBoxSilent 改造为零行为变化**：工具方法内部就是那三行，调用它等价于手写，纯消除重复。
4. **Checkbox 重建方案优于 suppressCallback**：重建虽简单粗暴，但消除标志位状态机，可读性更好。Minecraft Checkbox API 限制下这是最稳妥方案。
5. **SequenceProperties 保留临时变量恢复**：仅迁移 Y 布局到 PageLayout，不动值恢复逻辑，避免引入"编辑中写回 model"的语义变化。
6. **关闭标签与删除文件分离**：右键默认改为"关闭（不删文件）"，删除文件作为独立危险操作。这是安全修复，符合编辑器惯例。
7. **右键菜单用最小方案**：本轮不引入完整上下文菜单组件，右键直接弹关闭确认。完整菜单（关闭其他/关闭右侧/重命名/复制 ID）留后续。
8. **改动顺序**：项1（零风险）→ 项3（低风险）→ 项2（中风险）→ 项4（行为变化，最后验证）。

## 验证步骤

每项完成后不本地构建，提交 GitHub 由 Actions 构建。逐项验证清单：

- **项 1**：编译通过；打开文本/外观/逻辑属性页 → 选中节点 → 切换不同节点 → 各 EditBox 正确显示当前 entry 值（setBoxSilent 生效，无回调副作用导致值错乱）；编辑某字段 → 切走再切回 → 值保留。
- **项 2**：编译通过；逻辑页勾选 endDialog/allowSkip → 切换节点 → 复选框正确反映新 entry 状态（无 suppressCallback 残留导致误触发）；连续快速切换节点无异常。
- **项 3**：编译通过；打开序列属性 → 标签与输入框对齐（无错位）；编辑 title → 打开 NodePicker 选 start → 返回 → title 值保留（临时变量恢复仍有效）；保存后 sequence 字段正确写入。
- **项 4**：编译通过；右键标签 → 弹"关闭标签"确认 → 确认后标签移除且**对话文件仍在**（重新读取/导入可见）；关闭脏序列 → 提示未保存；删除文件入口可用（序列属性屏或别处）且二次确认文案明确"删除 JSON 文件"。

全部完成后整体回归：新建/保存/读取/导入对话、节点 CRUD、属性页编辑、立绘/背景/选项/节点子屏往返、标签开关、序列属性编辑，确认无功能回归。
