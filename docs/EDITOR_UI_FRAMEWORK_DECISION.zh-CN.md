# 编辑器 UI 框架重构决策

## 结论

编辑器不直接引入 Skija、ImGui 或外部 Minecraft UI 模组作为运行时前置。采用一个随 `dialog` 模组打包的轻量 retained UI 层，默认渲染后端使用 NeoForge 的 `GuiGraphics`，并把渲染、布局、事件、主题和编辑命令分开。

Skija可以作为未来的可选渲染后端，用于高质量文本、矢量图形和抗锯齿效果，但不承担组件树、布局、焦点、滚动和数据绑定。

## 当前代码审计

编辑器 UI 目录目前约 9376 行，主要问题不是控件数量，而是职责边界：

| 区域 | 当前问题 | 重构目标 |
| --- | --- | --- |
| `VNDialogEditorScreen` | 同时管理窗口布局、标签、序列切换、保存、快捷键和子控件 | 只负责工作区生命周期和命令分发 |
| `PortraitListScreen` | 1167 行，舞台预览、列表、表单、资源浏览、输入路由混在一起 | 拆成舞台视图、资源列表、检查器和弹窗 |
| `LogicPropertyPage` | 845 行，动态命令、物品、选项和下拉框各自维护布局 | 使用统一的表单行、列表行和字段绑定 |
| `TextPropertyPage` | 文本输入、翻译、格式按钮和颜色网格同时控制位置 | 使用统一的 Section、Field、Toolbar 和滚动容器 |
| `PropertyPanel` | 手工转发 children 的鼠标、滚轮、键盘事件 | 交由统一事件捕获链处理 |
| `EditorButton` / `DropdownWidget` / `MultiLineEditBox` | 各自绘制背景、边框、焦点和 hover | 由主题和基础组件绘制协议统一控制 |
| 多个 `*Screen` | 每个屏幕重复背景、标题、按钮和输入框逻辑 | 继承 `EditorSubScreen`，共享标题栏、遮罩和按钮区 |

## 外部方案比较

### Skija / HumbleUI Skija

Skija是Skia的Java绑定，提供Canvas、文字、路径、渐变和GPU后端。HumbleUI文档要求按平台加入 `skija-windows-x64`、`skija-linux-x64`、`skija-macos-x64` 等原生依赖；Maven Central上的平台包还依赖 `skija-shared`。项目自身标注为 Public alpha，且Skia API变化较快。

优点：文字和矢量渲染质量高，适合做终末地风格的线条、切角、纹理和高 DPI 文本。

问题：它没有现成的 Minecraft Screen、布局树、焦点管理、滚动容器、键盘导航或表单绑定；需要把 Minecraft 当前 OpenGL framebuffer 包装成 Skia Surface，还要处理窗口重建、渲染状态恢复和多平台原生库加载。把原生库塞进模组还会遇到 Windows/Linux/macOS/架构包体积、类加载器和其他模组的 LWJGL 状态冲突。

判断：不适合作为编辑器 UI 框架；可以在未来作为 `SkijaRenderer` 后端接入，默认保留 `GuiGraphicsRenderer`。

参考：

- [HumbleUI/Skija](https://github.com/HumbleUI/Skija)
- [Skija Getting Started](https://github.com/HumbleUI/Skija/blob/master/docs/Getting%20Started.md)
- [Skija Windows x64 Maven artifact](https://central.sonatype.com/artifact/io.github.humbleui/skija-windows-x64)

### ModernUI-MC

ModernUI-MC为Minecraft提供 View、Widget、文本布局和渲染扩展，并列出了 NeoForge 1.21~1.21.1 的兼容版本。项目说明中提到其通用 jar 会 shadow ModernUI framework 和扩展，因此理论上可以把依赖打进自己的模组。

优点：有成熟的 View 树、文本引擎、滚动、字体和部分控件；对 Minecraft 的集成比 Skija直接接OpenGL更完整。

问题：它本身是一个大型框架和生态，不只是几个可复制的类；需要处理 ModernUI 的启动、资源、渲染和版本绑定。若直接依赖 ModernUI-MC，用户需要额外安装前置；若把整个框架 shadow 进本模组，会增加包体、类加载和许可证维护成本，也可能与用户已安装的 ModernUI产生重复类。

判断：适合新项目或愿意把 ModernUI作为明确运行时基础的项目；不适合作为本仓库当前阶段的无前置直接替换。

参考：

- [ModernUI-MC](https://github.com/BloCamLimb/ModernUI-MC)
- [Modern UI 1.21.1 file](https://www.curseforge.com/minecraft/mc-mods/modern-ui/files/8206075)

### ModularUI

ModularUI的核心思路与本项目需要的方向很接近：面板树、动态布局、主题 JSON、滚动、组件和数据同步。它能解决所有坐标都手写的问题。

问题在于版本、加载器和运行时集成需要跟项目当前 NeoForge 版本逐项核对；它通常作为库或生态依赖使用，并不是一个可以只复制单个 jar 就完全拥有的无前置组件包。引入后还要把现有 Screen、Widget、事件和数据绑定全部迁移到它的生命周期。

判断：设计思想值得借鉴，直接作为本项目运行时依赖暂不采用。

参考：

- [CleanroomMC/ModularUI](https://github.com/CleanroomMC/ModularUI)
- [ModularUI documentation](https://cleanroommc.com/wiki/modularui/introduction)

### ImGui Java

ImGui Java提供 Dear ImGui 的Java绑定和LWJGL3后端，适合调试器、工具窗口和开发者面板。发布包需要 binding、LWJGL3 backend 和按平台的 native libraries；`all` 包虽然便于分发，仍需把原生库和 GLFW/OpenGL 生命周期接入宿主。

优点：控件和调试工具丰富，快速做工具界面很方便。

问题：它是 immediate mode，界面每帧由代码重建，不适合本项目需要的大量长文本、可持续焦点、复杂滚动表单和 Minecraft Screen 事件路由；外观需要重新绘制才能接近终末地风格，且原生库集成风险与 Skija类似。

判断：不作为正式编辑器 UI；可以作为开发期诊断窗口的独立工具。

参考：

- [SpaiR/imgui-java](https://github.com/SpaiR/imgui-java)
- [imgui-java-lwjgl3 Maven artifact](https://central.sonatype.com/artifact/io.github.spair/imgui-java-lwjgl3)

### NanoVG / 低层绘图库

NanoVG和类似方案只提供绘图能力，不提供完整组件树和布局；仍然需要自行解决字体、输入、焦点、滚动、裁剪和原生库分发。它们不符合减少 UI 维护成本的目标。

## 推荐架构

### 1. UI 核心

```text
editor/ui/
  core/
    UiNode.java          // 父子关系、可见性、bounds、生命周期
    UiRect.java          // 屏幕坐标和命中测试
    UiStyle.java         // 颜色、边框、间距、字体和状态
    UiEvent.java         // pointer/key/scroll/focus
    UiEventRouter.java   // capture -> target -> bubble
    UiContext.java       // Font、GuiGraphics、当前缩放和主题
  layout/
    DockLayout.java      // 左/中/右停靠
    StackLayout.java     // 垂直内容流
    RowLayout.java       // 工具栏和字段行
    ScrollLayout.java    // 滚动和裁剪
  widgets/
    UiPanel.java
    UiButton.java
    UiTextField.java
    UiTextArea.java
    UiSelect.java
    UiList.java
    UiTabs.java
    UiModal.java
  theme/
    EditorTheme.java
    EditorRenderer.java
  screens/
    EditorWorkspaceScreen.java
    EditorSubScreen.java
```

### 2. 编辑状态

控件不直接到处写 `DialogEntry` 字段。所有修改统一经过：

```text
UiEvent -> EditorCommand -> EditorDocument -> validation -> dirty/history -> view refresh
```

这样可以让文本输入、选项编辑、拖拽排序、撤销重做和验证使用同一条路径。

### 3. 工作区布局

`EditorWorkspaceScreen`只保留一个布局树：

```text
Workspace
├── TopBar       文件、编辑、验证、试玩
├── SequenceTabs 当前打开序列
├── DockLayout
│   ├── StructurePanel  左侧节点导航
│   ├── FlowPanel       中间流程阅读与编辑
│   └── InspectorPanel  右侧属性检查器
└── StatusBar
```

所有尺寸由 `DockLayout` 根据最小宽度和当前窗口计算，组件不再自己猜坐标。小窗口下变为单栏抽屉，而不是挤压三列到不可读。

## 迁移顺序

1. 先建立 `UiNode`、`UiEventRouter`、`DockLayout`、`EditorRenderer`，不改变业务数据。
2. 将按钮、输入框、下拉框、滚动条迁移到统一 widgets；旧类保留适配器，避免一次性重写所有页面。
3. 把 `VNDialogEditorScreen` 改成只创建工作区布局和绑定命令。
4. 将 `PropertyPanel`、`DialogTreeWidget`、`FlowViewWidget` 改为新组件的内容提供者。
5. 将 `PortraitListScreen`、`OptionEditScreen`、`SequencePropertiesScreen`、选择器和验证屏迁移到 `EditorSubScreen`。
6. 最后再考虑 Skija：只实现 `UiRenderer` 的第二个后端，不能让业务组件直接依赖 Skija API。

## 最终选择

当前最合适的选择是：

> 随模组打包的轻量 retained UI 核心 + NeoForge `GuiGraphics` 默认渲染。

它满足无前置、跨平台、Minecraft 输入兼容和可维护性要求；同时保留未来接入 Skija 文本/矢量渲染的接口，不把整个编辑器锁死在某个原生图形库上。
