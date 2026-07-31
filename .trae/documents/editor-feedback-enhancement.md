# 编辑器反馈增强计划（第四轮）

## 摘要

延续前三轮改造，本轮聚焦**修复操作反馈缺陷 + 增强视觉反馈**，不引入预览区/OptionRow/搜索等大功能。对照 Sparkle-Morpher 的反馈体系（statusText 分色、字段说明栏、选中描边、tooltip 全覆盖），识别出 VNDialog 编辑器在"操作反馈"层的 4 项差距。

最严重的是**数据安全缺陷**：`saveCurrentSequenceToFile` 吞掉 IOException，磁盘写失败仍显示"已保存"并清除 dirty 标记，用户误以为保存成功导致数据丢失。其次是 statusText 无分色无消失、选中项视觉弱、tooltip 覆盖率低。

**不做**：对话/立绘预览区（大型新功能，用户确认留后续）、OptionRow 值模型重构（留后续）、字段说明栏（需补大量 i18n 文案，规模大，留后续）、dirty 行级视觉（依赖 OptionRow）、图标体系升级（工作量大）。

## 当前状态分析（差距定位）

### 缺陷 1：保存失败无反馈（数据安全）

[saveCurrentSequenceToFile](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java#L390-L406) 的 catch 块仅 `Dialog.LOGGER.error`，不更新 statusText，不抛出失败信号。[onSave](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java#L313-L333) 调用它后**无条件** `markClean`（L322）+ 显示"已保存"（L324）。结果：磁盘写失败时 UI 仍显示"已保存"并清除 dirty 标记，用户关闭编辑器后数据丢失。

对比：[onLoad](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java#L424-L427) 读取失败有 `load_failed` 提示，[onImport](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java#L446) 导入失败有提示，唯独保存失败被吞掉。

### 缺陷 2：statusText 无分色无消失

[statusText 字段](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java#L64) 是裸 `String`，[L748-749](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java#L748-L749) 固定用 `TEXT_SECONDARY`（灰）渲染。成功/失败/警告视觉无差异，且一旦赋值常驻不消失，旧消息堆积误导用户。

对比 Sparkle `setStatus(Component, ChatFormatting)`：GRAY/GREEN/YELLOW/RED 四色分语义。

### 差距 3：选中项视觉弱

[DialogTreeWidget 选中](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/widget/DialogTreeWidget.java#L237-L239) 仅 `BG_SELECTED`（蓝调背景）填充，无描边，文字色恒为 `TEXT_SECONDARY` 不变。对比 Sparkle 列表项选中用 `RED` 红色描边（强视觉锚点）+ dirty 项文字纯白。VNDialog 选中态在 hover 背景之上仅靠色调区分，不够醒目。

### 差距 4：tooltip 覆盖率低

[renderToolbarTooltips](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java#L768-L787) 仅覆盖 6 个工具栏按钮。以下高频元素无 tooltip：
- `addNodeBtn`（添加节点按钮，[L148](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java#L148)）
- `addTabBtn`（+ 新增标签，[L145](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java#L145)）
- `tabLeftArrow`/`tabRightArrow`（标签滚动箭头，[L163-L166](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java#L163-L166)）
- TabButton（标签页，双击重命名/右键关闭等操作无提示）

对比 Sparkle：所有 hit 区域（图标按钮/文本按钮/行/chip/grid cell）均注册 tooltip。

## 借鉴的 Sparkle-Morpher 模式

- **statusText 分色**：`setStatus(Component, ChatFormatting)`，GRAY(中性)/GREEN(成功)/YELLOW(警告)/RED(错误)。VNDialog 借鉴此模式但用 EditorTheme 语义色（SUCCESS/WARNING/DANGER）而非 ChatFormatting，与现有主题统一。
- **状态消息消失**：Sparkle 是常驻不消失（靠下次覆盖）。VNDialog 改进为"成功/警告消息 N 秒后淡出，错误消息常驻直到下次操作"，比 Sparkle 更优。
- **选中描边**：Sparkle 列表项 `border(g, x, y, w, h, RED)`。VNDialog 树节点借鉴，用 ACCENT 描边（与现有主题一致，不用红色避免与 DANGER 混淆）。
- **tooltip 全覆盖**：Sparkle 用 `hit()` 注册表统一管理。VNDialog 用最小方案：扩展 `renderToolbarTooltips` 覆盖更多按钮，不引入 hit 注册表。

## 改造项（4 项，按依赖顺序）

### 项 1：修复保存失败无反馈（数据安全缺陷）

**目标**：`saveCurrentSequenceToFile` 返回成功/失败，`onSave`/`onTest`/`doSaveAllAndClose` 根据结果决定是否 markClean 和显示正确状态。

**文件**：[VNDialogEditorScreen.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java)

**改动**：
- `saveCurrentSequenceToFile()` 改为返回 `boolean`（true=成功）：try 块末尾 `return true`，catch 块 `Dialog.LOGGER.error` 后 `return false`。
- `onSave`（L313-L333）：接收返回值。失败时**不 markClean**，statusText 显示 `gui.vn_edit.status.save_failed`（红色），不触发 `dialog reload`。成功时维持原逻辑。悬空引用警告（`saved_with_warnings`）维持，但用警告色。
- `onTest`（L431-L441）：调用 `saveCurrentSequenceToFile()` 后检查返回值，失败时显示 `save_failed` 并 return（不进入测试）。
- `doSaveAllAndClose`（L858 附近）：遍历保存时累计失败数，失败时显示汇总错误。

**语言文件**：新增 `gui.vn_edit.status.save_failed`（zh_cn/en_us），文案明确"保存失败，请检查文件权限"。

**改动范围**：1 Java 文件 + 2 语言文件。核心是让保存失败路径可见，避免数据丢失。低风险（仅改返回值与分支判断）。

### 项 2：statusText 分色 + 自动消失

**目标**：statusText 支持 4 色分语义（成功/警告/错误/中性），成功与警告消息 4 秒后淡出，错误消息常驻直到下次操作。

**文件 1**：[EditorTheme.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/util/EditorTheme.java)

**改动**：新增 3 个语义色常量：
- `STATUS_SUCCESS = 0xFF6AC46A`（绿）
- `STATUS_WARNING = 0xFFE0A040`（黄/橙）
- `STATUS_ERROR = 0xFFE05555`（红，复用 DANGER 同值但语义独立命名）

中性色复用现有 `TEXT_SECONDARY`。

**文件 2**：[VNDialogEditorScreen.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java)

**改动**：
- 新增 `StatusLevel` 枚举（NEUTRAL/SUCCESS/WARNING/ERROR）和 `statusLevel` 字段、`statusClearTime` 字段（纳秒时间戳，0=常驻）。
- 新增 `setStatus(String text, StatusLevel level)` 方法：设置 text + level，SUCCESS/WARNING 时设 `statusClearTime = now + 4_000_000_000L`（4秒），ERROR 时 `statusClearTime = 0`（常驻），NEUTRAL 时 4 秒消失。
- 替换所有 `this.statusText = ...` 赋值为 `this.setStatus(...)` 调用，按语义传 level：
  - `saved` → SUCCESS，`saved_with_warnings` → WARNING，`save_failed` → ERROR，`loaded` → SUCCESS，`load_failed` → ERROR，`import.failed` → ERROR，`props_saved` → SUCCESS 等。grep `this.statusText =` 定位全部点位（约 8-10 处）。
- `render`（L748-749）：根据 `statusLevel` 选颜色渲染。每帧检查 `statusClearTime`，若非 0 且 `System.nanoTime() > statusClearTime`，清空 statusText。
- 状态栏高度不变（14px），仅颜色与消失逻辑变化。

**改动范围**：2 文件。新增枚举+方法+字段，替换约 8-10 处赋值，render 增加颜色选择与超时检查。中等风险（需覆盖所有 statusText 赋值点，漏改会留下无色消息）。

### 项 3：树节点选中描边 + 文字色强化

**目标**：选中节点加 ACCENT 左侧竖条 + 选中文字提亮，增强视觉锚点。

**文件**：[DialogTreeWidget.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/widget/DialogTreeWidget.java)

**改动**（renderWidget L237-L246 附近）：
- 选中节点：在背景填充后，于左侧画 2px 宽 `EditorTheme.ACCENT` 竖条（`graphics.fill(this.getX(), rowY, this.getX()+2, rowY+ROW_HEIGHT, EditorTheme.ACCENT)`），与 [PortraitListScreen 选中项](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/PortraitListScreen.java) 左侧强调条风格一致（VS Code 活动标签风格，已在 PortraitListScreen 验证过）。
- 选中节点文字色：从 `TEXT_SECONDARY` 改为 `TEXT_PRIMARY`（纯白），hover 时也保持选中文字纯白。
- 调整绘制顺序：先 hover 背景 → 选中背景 → 选中左侧竖条 → 文字，确保竖条不被覆盖。

**改动范围**：1 文件，renderWidget 内约 5 行改动。低风险（纯视觉调整）。

### 项 4：tooltip 覆盖率提升

**目标**：为添加节点按钮、标签栏按钮（+/◀/▶）、TabButton 补充 tooltip。

**文件**：[VNDialogEditorScreen.java](file:///workspace/VNDialogReborn/src/main/java/top/yourzi/dialog/editor/gui/VNDialogEditorScreen.java)

**改动**：
- 扩展 `renderToolbarTooltips`（重命名为 `renderTooltips` 或新增分支）：在工具栏按钮检查后，追加检查：
  - `addNodeBtn` hover → `gui.vn_edit.tooltip.add_node`（"添加对话节点"）
  - `addTabBtn` hover → `gui.vn_edit.tooltip.add_tab`（"新建对话序列"）
  - `tabLeftArrow` hover → `gui.vn_edit.tooltip.tab_left`（"标签左移"）
  - `tabRightArrow` hover → `gui.vn_edit.tooltip.tab_right`（"标签右移"）
  - TabButton hover → `gui.vn_edit.tooltip.tab`（"左键切换 / 双击重命名 / 右键关闭"）
- 每个检查用 `btn.isMouseOver(mouseX, mouseY)` + `graphics.renderTooltip`，与现有工具栏 tooltip 模式一致。

**语言文件**：新增上述 5 个 tooltip key（zh_cn/en_us）。

**改动范围**：1 Java 文件 + 2 语言文件。renderTooltips 增加约 5 个分支。低风险（纯增量，不改现有逻辑）。

## 假设与决策

1. **保存失败修复是最高优先级**：数据安全缺陷必须先修，其余增强才有意义。
2. **statusText 用 EditorTheme 语义色而非 ChatFormatting**：与现有暗色主题统一，红色复用 DANGER 色值但独立命名 STATUS_ERROR 以保持语义清晰。
3. **成功/警告 4 秒消失，错误常驻**：比 Sparkle 的"全常驻"更优，避免旧消息堆积；错误常驻确保用户看到。4 秒是经验值，足够阅读但不过长。
4. **选中描边用 ACCENT 蓝而非红色**：VNDialog 主题是蓝调（ACCENT=蓝），红色留给 DANGER。Sparkle 用红是其主题色，VNDialog 不照搬配色仅借鉴"加描边"思路。与 PortraitListScreen 已有的左侧强调条风格统一。
5. **tooltip 用原生 renderTooltip 不自定义框**：Sparkle 自定义 tooltip 框是为玻璃模糊效果，VNDialog 无模糊体系，用原生 tooltip 更轻量一致。
6. **不引入字段说明栏**：需为每个属性字段补 `.desc` i18n 文案（数十条），规模大，留后续。
7. **不引入 dirty 行级视觉**：当前即时回写无 pending 缓冲，行级 dirty 无数据支撑，需 OptionRow 重构后才有意义。
8. **改动顺序**：项1（缺陷修复，最高优先）→ 项2（反馈机制，项1依赖它显示错误色）→ 项3（视觉）→ 项4（tooltip）。项2 为项1 提供分色显示能力，故项1 实现时可先用项2 的 setStatus 机制。

## 验证步骤

不本地构建，提交 GitHub 由 Actions 构建。逐项验证：

- **项 1**：编译通过；构造保存失败场景（如把 DIALOG_JSON_DIR 设为只读，或在测试中模拟 IOException）→ 点击保存 → statusText 显示"保存失败"且 dirty 标记（`*`）保留；正常保存 → 显示"已保存"且 dirty 清除。测试按钮在保存失败时不进入对话播放。
- **项 2**：编译通过；执行各操作观察状态栏颜色：保存成功=绿，悬空引用警告=黄，保存/读取失败=红，切换序列=中性灰；成功/警告消息约 4 秒后消失，错误消息常驻直到下次操作。
- **项 3**：编译通过；选中树节点 → 左侧出现蓝色竖条 + 文字变纯白；hover 其它节点 → 选中节点仍保持竖条+纯白；切换选中 → 竖条跟随移动。
- **项 4**：编译通过；鼠标悬停添加节点按钮/+/◀/▶/标签 → 分别显示对应 tooltip；工具栏原有 6 按钮 tooltip 不受影响。

整体回归：新建/保存/读取/导入/测试/标签开关/节点 CRUD/属性编辑，确认反馈正确无回归。
