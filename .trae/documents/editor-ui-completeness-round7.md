# 编辑器 UI 完整性提升（第七轮）

## 背景与定位

前六轮已完成：OptionRow 试点、布局自适应、模态浮层、焦点描边、i18n 收尾、
搜索多字段化、dirty 标记即时同步等「打磨型」改进。

本轮目标：**补功能缺口**。对比 `Sparkle-Morpher` 调研发现，VNDialog 编辑器在
「键盘可达性 / 空状态 / 节点复制 / 列表排序 / 快捷键可发现性」五个面上有明显短板，
而这些都是低成本、低回归风险、用户收益清晰的项目。本轮集中补齐这五项。

Toast/状态栏一项 VNDialog 已优于 Sparkle，本轮不动。

---

## 本轮改进项（按实施顺序）

### 项 A：F1 帮助面板（快捷键可发现性）

**问题**：`VNDialogEditorScreen.keyPressed` 已实现 Ctrl+S/N/Enter/Tab、Delete、
F2、Insert 等快捷键，但**无任何可发现入口**，用户只能靠读源码或 lang 文件知道。
`renderToolbarTooltips` 仅鼠标悬停按钮显示单行 tooltip，不系统。

**方案**：
- `VNDialogEditorScreen` 增加 `boolean showHelpOverlay` 字段。
- `keyPressed` 增加 `GLFW_KEY_F1` 分支：toggle `showHelpOverlay`，返回 true。
- `render` 末尾：当 `showHelpOverlay` 为 true，画半透明遮罩（`EditorTheme.BG_DEEPEST` + alpha 0.6）
  + 居中面板（复用 `EditorRenderHelper.fillWithBorder`），列出全部快捷键。
- 面板内按分组排列：文件（Ctrl+S/N）、节点（Insert/Delete/F2）、序列（Ctrl+Tab/Ctrl+Shift+Tab）、
  测试（Ctrl+Enter）、帮助（F1/Esc）。
- 面板显示时拦截 mouseClicked（点击任意位置关闭），keyPressed 拦截除 F1/Esc 外的键。
- lang 文件新增 `gui.vn_edit.help.title` 和 `gui.vn_edit.help.*` 各行文案。

**改动文件**：
- `VNDialogEditorScreen.java`（keyPressed、render、新增 renderHelpOverlay 方法）
- `zh_cn.json` / `en_us.json`（help.* 键值）

**验收**：按 F1 弹出面板，再按 F1 或 Esc 或点击遮罩关闭；面板列出全部快捷键。

---

### 项 B：DialogTreeWidget 方向键导航 + 折叠

**问题**：`DialogTreeWidget` 只能鼠标点选节点，键盘用户无法用方向键浏览。
`selectedIndex` 字段已存在，`node.expanded` 字段已存在（第 465 行），扩展成本低。

**方案**：
- `DialogTreeWidget` 增加 `public boolean keyPressed(int keyCode)` 公开方法
  （AbstractWidget 不自带 keyPressed，需由宿主 Screen 转发）。
- `VNDialogEditorScreen.keyPressed` 在「EditBox 未聚焦 且 树区域聚焦/鼠标在树上」时
  转发 UP/DOWN/LEFT/RIGHT/Enter 给 `treeWidget.keyPressed`。
- 键位：
  - `UP`/`DOWN`：`selectedIndex ± 1`，clamp 到 `[0, visibleNodes.size()-1]`，
    滚动到可见（若新选中项在视口外，调整 `scrollOffset`）。
  - `LEFT`：当前节点有子节点且展开 → 折叠；否则无操作。
  - `RIGHT`：当前节点有子节点且折叠 → 展开；否则无操作。
  - `Enter`：触发 `onEntrySelected.accept(visibleNodes.get(selectedIndex).entry)`，
    与单击语义一致（打开属性面板）。
- 选中变化时同步 `EditorScreenState.get().setSelectedNodeId(...)`，与鼠标点选路径一致。

**改动文件**：
- `DialogTreeWidget.java`（新增 keyPressed 方法 + scrollIntoView 辅助）
- `VNDialogEditorScreen.java`（keyPressed 转发）

**验收**：树聚焦时方向键移动选中、左右折叠展开、Enter 打开属性面板；选中项滚出视口时自动滚回。

---

### 项 C：NodePickerScreen 键盘导航

**问题**：`NodePickerScreen` 完全无 `keyPressed`，只能鼠标单击选择。当前 `filteredIds`
列表已就绪，加键盘导航成本低。

**方案**：
- 增加 `int focusedIndex = -1` 字段（与 hover 区分，键盘选中用）。
- 新增 `keyPressed`：
  - `UP`/`DOWN`：`focusedIndex ± 1`，clamp，滚动可见。
  - `Enter`：若 `focusedIndex` 有效，`onSelected.accept(filteredIds.get(focusedIndex))` + `onClose`。
  - `Esc`：`onClose()`。
- `render` 中 `focusedIndex` 行画 `EditorTheme.BG_SELECTED` + ACCENT 左竖条（与树选中风格一致）。
- 鼠标移动时清空 `focusedIndex`（避免键盘选中与 hover 重叠混淆）。
- 搜索框聚焦时方向键仍可导航列表（搜索框不消费 UP/DOWN）。

**改动文件**：
- `NodePickerScreen.java`（keyPressed、render、mouseClicked 清 focusedIndex）

**验收**：打开节点选择屏，方向键移动焦点、Enter 确认、Esc 取消；鼠标点击仍正常工作。

---

### 项 D：空状态完善（序列空 / 无匹配 / 未加载）

**问题**：`DialogTreeWidget.renderWidget` 在 `sequence == null` 或 `visibleNodes.isEmpty()`
（非搜索态）时只画空背景，无引导文字。`PortraitListScreen` 立绘列表空时同样无提示。
Sparkle 的三态列表（空/加载/错误）是明显更好的范式。

**方案**：
- `DialogTreeWidget.renderWidget` 末尾增加空状态分支（在搜索无结果提示之前）：
  - `sequence == null`：居中显示 `gui.vn_edit.tree.no_sequence`（"未加载对话序列"）。
  - `sequence != null && visibleNodes.isEmpty() && !isSearching()`：居中显示
    `gui.vn_edit.tree.empty`（"该序列暂无节点，按 Insert 添加"）。
- `PortraitListScreen.renderLeftList`：`portraits.isEmpty()` 时居中显示
  `gui.vn_edit.portrait.empty`（"暂无立绘，点击添加立绘"）。
- lang 文件新增对应键值。
- **不**引入加载态/错误态占位（VNDialog 是本地文件编辑器，无异步加载场景，
  错误已通过状态栏反馈，避免过度设计）。

**改动文件**：
- `DialogTreeWidget.java`（renderWidget 空状态分支）
- `PortraitListScreen.java`（renderLeftList 空状态分支）
- `zh_cn.json` / `en_us.json`

**验收**：新建空序列时树区显示"该序列暂无节点，按 Insert 添加"；无立绘时立绘列表显示"暂无立绘"。

---

### 项 E：选项 / 命令列表排序按钮

**问题**：`LogicPropertyPage` 的选项列表（`rebuildOptionButtons`）和命令列表
（`rebuildCommandWidgets`）只支持 add/delete，**不支持重排**。顺序对 VN 很重要
（选项呈现顺序 = 玩家看到顺序）。`PortraitListScreen` 已有 up/down swap 模式可复用。

**方案**：
- `LogicPropertyPage` 命令列表每行右侧增加 ▲▼ 两个小按钮（宽 14，复用 EditorButton）：
  - `▲`：`swap(currentEntry.getCommand(), i, i-1)`，i>0 时启用。
  - `▼`：`swap(currentEntry.getCommand(), i, i+1)`，i<size-1 时启用。
  - swap 后调 `rebuildCommandWidgets()` + `notifyDirty()`。
- 选项列表同理：每行右侧增加 ▲▼ 按钮，swap `currentEntry.getOptions()` 数组，
  `rebuildOptionButtons()` + `notifyDirty()`。
- 按钮宽度挤压：现有删除按钮 ✕ 宽 20，改为 ▲▼✕ 三个 14 宽按钮，总宽 42+gap，
  确保不超出 fieldWidth（参考 LogicPropertyPage 现有布局）。
- 借鉴 Sparkle `PortraitListScreen` 第 289-302 行的 swap 实现。

**改动文件**：
- `LogicPropertyPage.java`（rebuildCommandWidgets、rebuildOptionButtons 增加 ▲▼ 按钮 + swap 逻辑）

**验收**：命令/选项行右侧 ▲▼ 按钮可上移下移，顺序变化立即反映且标记 dirty；首项 ▲ 禁用、末项 ▼ 禁用。

---

### 项 F：复制 / 粘贴 / 复制并选中节点

**问题**：VN 编辑器高频需求"复制一个相似节点再改"，当前只能从零新建。
Sparkle 也无此功能，但 VNDialog 收益更高。`DialogEntry` 是数据类，深拷贝成本低。

**方案**：
- `VNDialogEditorScreen` 增加静态剪贴板字段 `static DialogEntry clipboard = null`。
- `keyPressed` 在 Ctrl 分支扩展：
  - `Ctrl+C`：`clipboard = deepCopy(treeWidget.getSelectedEntry())`，状态栏提示"已复制节点"。
  - `Ctrl+V`：若 clipboard 非空，生成新节点：
    - 新 ID = `原ID + "_copy"`（若已存在则追加数字 `_copy2`/`_copy3`...）。
    - 追加到 `currentSequence.getEntries()`。
    - 重建树、选中新节点、`markDirty()`、状态栏提示"已粘贴节点"。
  - `Ctrl+D`：= Ctrl+C + Ctrl+V 一步到位。
- 深拷贝实现：`DialogEntry` 的 `text`/`speaker`/`audioPath`/`nextId` 是 String，
  `options` 是 `DialogOption[]`（需逐个 new），`commands`/`displayItems` 是 `List<String>`
  （需 new ArrayList）。在 `DialogEntry` 增加 `deepCopy()` 方法或工具类静态方法。
- 仅在 EditBox 未聚焦时拦截 Ctrl+C/V/D（避免与文本编辑冲突）。
- ID 冲突检测：遍历 `currentSequence.getEntries()` 查重。

**改动文件**：
- `VNDialogEditorScreen.java`（clipboard 字段、keyPressed 扩展、pasteNode 方法）
- `DialogEntry.java` 或新增 `DialogEntryCopier` 工具（deepCopy）
- `zh_cn.json` / `en_us.json`（"已复制节点"/"已粘贴节点"等提示）

**验收**：选中节点 Ctrl+C，再 Ctrl+V 出现 `_copy` 副本并被选中；Ctrl+D 一步完成；ID 自动去重。

---

## 不在本轮范围（后续规划备忘）

以下项经评估为高成本或高风险，本轮不做，记录备查：

| 项 | 原因 | 建议时机 |
|---|---|---|
| 多选/批量（树 Ctrl/Shift 多选 + 批量工具栏） | 改动面大，需重做树渲染与交互模型 | 独立一轮 |
| 撤销/重做栈（命令模式或 snapshot 栈） | 即时回写架构下需大改，风险高 | 配合 OptionRow 全量迁移 |
| 右键上下文菜单 | 比"右键=删除"更安全但需新控件 | 配合多选一轮 |
| Tab 焦点循环（PropertyPanel 内 EditBox 间） | MC 原生 EditBox 不自带，需手动接管 | 低优先 |
| UI 状态磁盘持久化（activePropertyTab/previewZoom） | 最近文件已持久化，UI 状态属锦上添花 | 低优先 |
| 树拖拽改父子关系 | 需画拖拽指示线，交互复杂 | 远期 |

---

## 实施顺序与验证

1. 项 A（F1 帮助）→ 项 B（树方向键）→ 项 C（NodePicker 键盘）→ 项 D（空状态）
   → 项 E（列表排序）→ 项 F（复制粘贴）
2. 每项完成后单独编译验证：`./gradlew build` 或 IDE 增量编译。
3. 全部完成后整体回归：打开编辑器 → F1 查看帮助 → 方向键浏览树 → Enter 进属性 →
   节点选择屏键盘操作 → 新建空序列看空状态 → 选项/命令 ▲▼ 排序 → Ctrl+C/V 复制粘贴节点。
4. lang 文件中英文同步。
5. 提交信息：`feat(editor): round 7 UI completeness - help panel, keyboard nav, empty states, list reorder, copy/paste`。
