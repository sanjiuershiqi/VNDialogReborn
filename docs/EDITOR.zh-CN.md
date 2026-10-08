# VNDialog 编辑器（3.0）

`/dialog editor` 打开。编辑器只读写 `config/vndialog_editor/` 下的文件，保存后自动执行 `/dialog reload`。

## 界面

```
┌ 文件 ▾ │ 结构 剧本 关系图 舞台 检查 属性 ………… 撤销 重做 保存 ▶试玩 ? ┐
├────────────┬──────────────────────────────┬──────────────────┤
│ 节点        │ 当前视图                      │ 属性              │
│ 搜索        │ （剧本 / 关系图 / 舞台 / 检查） │ 台词 走向 演出 逻辑 │
│ start …     │                              │                  │
│ line_2 …    │                              │                  │
├────────────┴──────────────────────────────┴──────────────────┤
│ 状态消息                               story_a · 12 个节点 · 未保存 │
└────────────────────────────────────────────────────────────────┘
```

- **文件菜单（▾）**：切换已打开的文件，新建 / 打开 / 导入，文件设置，界面缩放。
- **对话设置（`F9`）**：玩家在对话里的按键和已读显示，见下文。
- **节点列表**：按文件顺序列出全部节点，搜索覆盖 ID、台词、选项、命令、素材路径；永远播不到的节点标「未连接」。
- **剧本**：一节点一卡片，文件顺序就是默认走向。卡片底部显示接下来去哪里，右键打开节点菜单。
- **关系图**：节点和连线。拖节点摆放，从右侧圆点拖出连线，双击空白新建，右键更多；「所有文件」模式显示文件之间的 `dialog show` 调用。
- **舞台**：按运行时规则（立绘高 = 屏幕 68% × 大小，侧边距 20，底部对齐，偏移按屏幕比例）预览当前节点；拖动移动、滚轮缩放、方向键微调。
- **检查**：错误 / 警告列表，点击定位到节点并打开对应属性页。存在错误时不能试玩。
- **属性**四页：台词（说话者、台词、格式色板、翻译键模式）、走向（顺序继续 / 跳转 / 选项）、演出（背景、立绘、语音）、逻辑（显示条件、命令、展示物品）。

界面缩放：编辑器不直接沿用游戏的 GUI 缩放，而是选一个整数倍，尽量留出约 1000×560 的工作区；也可以在文件菜单里手动选 1x–4x。宽度仍不足时（< 760）节点列表收进视图栏，< 520 时属性也收进视图栏。

## 走向规则

编辑器对「接下来去哪」的判断与游戏一致，统一在 `NodeGraph.successors`：

1. 节点有选项：只按选项走；选项没有目标表示选择后结束对话。
2. 否则，设了「在此结束」就结束。
3. 否则，有显式跳转就跳转；没有就按文件顺序到下一个节点，没有下一个则结束。

节点列表的「未连接」、关系图的连线和检查都基于这一条规则。

## 文件

```
config/vndialog_editor/
  dialog_json/     对话文件
  portraits/ backgrounds/ sounds/   素材
  lang/            翻译键模式写入的语言文件（zh_cn.json、en_us.json）
  layout/          关系图里手动摆放的位置，每个对话一个文件
  editor_sessions.json   上次打开的文件和界面缩放
```

- 翻译键台词存为 `{"translate": 键, "fallback": 原文}`；语言文件里没有这个键时，编辑器和游戏都显示 `fallback`。
- 自定义展示物品存为物品 `nbt` 里的 `{components:{...}}`。

## 对话设置

「对话设置」改的是玩家怎么读对话，不是对话内容，所以存在 `config/vndialog_play.json`，不进对话文件：数字键选项、空格/回车推进、滚轮上下、右键/`H` 隐藏、已读记录及其四项显示开关、已读颜色、清除已读记录。改动立即保存，下次打开对话生效。

## 代码结构

```
editor/
  EditorConfig, EditorStore      目录与文件读写（原子写入、ID 校验、会话）
  EditorDocument                 打开的文件：快照式撤销/重做，按快照判断是否未保存
  LayoutStore                    关系图位置
  TextCodec                      组件 JSON ⇄ 带 § 的可编辑文本；翻译键与语言文件
  DialogValidator, AssetService  检查；素材解析与预览绘制
  ui/                            保留式 UI 内核（无外部依赖）
    UiNode, UiHost, Layer        节点树、布局刷新、命中测试 + 冒泡、焦点、指针捕获、浮层
    Column, Row, ScrollView, Panel
    Button, Toggle, TextBox, TextArea, Select, ItemList, ContextMenu, Modal, Sheets
    Theme, FormatCodes, Lines, Icons   颜色与裁剪、格式代码、连线绘制、物品图标
  screen/                        编辑器应用层
    DialogEditorScreen           唯一的 Screen：布局、命令、快捷键、界面缩放
    OutlinePanel, FlowPanel, StageView, ValidationPanel, InspectorPanel(+4 个 Tab)
    GraphPanel, GraphModel       关系图的交互与绘制；节点、连线、自动排列
    PickerSheet, ItemPickerSheet, ItemEditSheet, SequenceSheet, HelpSheet   浮层
    NodeGraph                    走向规则和所有改结构的操作（插入/移动/删除/重命名会同步引用）
```

约定：

1. 面板只通过 `EditorContext` 读写当前文件：改字段后调用 `touch(merge)`，改结构后调用 `touchStructure()`。面板不碰磁盘、不互相调用。
2. 所有选择器、确认框、帮助都是同一棵 UI 树上的浮层，不再切换 Screen；关闭浮层不会重建编辑器。
3. 浮层独占输入，弹出菜单点外部关闭且不穿透。
4. 文本输入逐字写回模型，连续输入在撤销历史中合并为一步。
5. 裁剪用 `Theme.clip` / `Theme.unclip`，不要直接调用 `enableScissor`，否则在编辑器自己的缩放下位置会错。
