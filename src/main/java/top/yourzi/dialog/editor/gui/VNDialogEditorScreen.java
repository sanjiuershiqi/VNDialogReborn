package top.yourzi.dialog.editor.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.DialogManager;
import top.yourzi.dialog.editor.gui.property.AppearancePropertyPage;
import top.yourzi.dialog.editor.gui.widget.DialogTreeWidget;
import top.yourzi.dialog.editor.gui.widget.EditorButton;
import top.yourzi.dialog.editor.gui.widget.FlowViewWidget;
import top.yourzi.dialog.editor.gui.widget.PropertyPanel;
import top.yourzi.dialog.editor.util.EditorConfig;
import top.yourzi.dialog.editor.util.EditorHistory;
import top.yourzi.dialog.editor.util.EditorTheme;
import top.yourzi.dialog.editor.util.TextureCacheService;
import top.yourzi.dialog.editor.validation.DialogValidator;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.model.DialogSequence;
import top.yourzi.dialog.network.NetworkHandler;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.lwjgl.glfw.GLFW;

/**
 * VNDialog 可视化编辑器主屏幕。融合自 visual_mod_edit_vndialog，并适配 NeoForge 1.21.1。
 * 该屏幕集成了对话树、属性面板、标签页管理、文件保存/读取/测试/导入等功能。
 */
public class VNDialogEditorScreen extends Screen {
    private static final int TOOLBAR_HEIGHT = EditorTheme.TOOLBAR_H;
    private static final int TAB_BAR_HEIGHT = EditorTheme.TAB_BAR_H;
    private static final int STATUS_HEIGHT = EditorTheme.STATUS_H;
    /** 当前窗口的导航栏宽度；按窗口宽度计算，避免固定像素在不同 GUI Scale 下失衡。 */
    private int sidebarWidth = EditorTheme.TREE_WIDTH;
    /** 当前窗口的检查器宽度；停靠时保留中心工作区的最小可用宽度。 */
    private int inspectorWidth = 300;
    private static final int TAB_AREA_LEFT = 2;
    private static final int TAB_AREA_RIGHT_MARGIN = 56;
    private static final int MAX_TAB_WIDTH = 100;
    private static final Path SESSION_FILE = EditorConfig.CONFIG_ROOT.resolve("editor_sessions.json");
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<DialogSequence> openSequences = new ArrayList<>();
    private int activeSequenceIndex = -1;
    private DialogTreeWidget treeWidget;
    /** 对话树搜索框：实时过滤节点，文本由 EditorScreenState 持久化跨重建回填。 */
    private EditBox treeSearchBox;
    private PropertyPanel propertyPanel;
    private final List<TabButton> tabButtons = new ArrayList<>();
    private EditorButton addTabBtn;
    private EditorButton addNodeBtn;
    private EditorButton tabLeftArrow;
    private EditorButton tabRightArrow;
    /** 顶部工具栏按钮引用，用于 tooltip 检测（顺序：新建/保存/读取/测试/导入/序列属性/验证） */
    private final List<EditorButton> toolbarButtons = new ArrayList<>();
    private int tabScrollOffset = 0;
    private DialogSequence currentSequence;
    private DialogEntry editingEntry;
    private FlowViewWidget flowWidget;
    /** 当前序列的结构级撤销/重做历史（JSON 快照式，借鉴 MainGraph HistoryManager）。 */
    private final EditorHistory history = new EditorHistory();
    /** 跟踪有未保存修改的对话序列 ID */
    private final java.util.Set<String> dirtySequences = new java.util.HashSet<>();
    public String statusText = "";
    /** 当前状态消息的语义级别，决定状态栏文字颜色。 */
    private StatusLevel statusLevel = StatusLevel.NEUTRAL;
    /** 状态消息自动清除时间（纳秒时间戳），0 表示常驻（错误消息）不自动清除。 */
    private long statusClearTime = 0L;
    private boolean isInitialized = false;
    /** F1 帮助浮层是否展开。展开时拦截除 F1/Esc 外的按键与所有鼠标点击（点击遮罩关闭）。 */
    private boolean showHelpOverlay = false;
    /** 节点剪贴板（静态，跨屏保持）。Ctrl+C 复制选中节点的深拷贝，Ctrl+V 粘贴为新 ID 节点。 */
    private static DialogEntry clipboard = null;

    /**
     * 状态消息语义级别。借鉴 Sparkle setStatus(Component, ChatFormatting) 的分色思路，
     * 用 EditorTheme 语义色而非 ChatFormatting，与暗色主题统一。
     * - NEUTRAL：中性信息（如切换序列），4 秒后消失
     * - SUCCESS：操作成功（如保存成功），4 秒后消失
     * - WARNING：警告（如悬空引用），4 秒后消失
     * - ERROR：错误（如保存失败），常驻直到下次操作，确保用户看到
     */
    private enum StatusLevel {
        NEUTRAL,
        SUCCESS,
        WARNING,
        ERROR
    }

    /**
     * 设置状态消息并按语义级别决定颜色与消失时机。
     * 成功/警告/中性消息 4 秒后自动清除，错误消息常驻直到下次 setStatus 覆盖。
     */
    private void setStatus(String text, StatusLevel level) {
        this.statusText = text;
        this.statusLevel = level;
        // ERROR 常驻（0），其余 4 秒后清除
        this.statusClearTime = level == StatusLevel.ERROR ? 0L : System.nanoTime() + 4_000_000_000L;
    }

    public VNDialogEditorScreen() {
        super(Component.translatable("gui.vn_edit.title"));
    }

    @Override
    protected void init() {
        super.init();
        // 与原模组一致：构建前先清理旧控件，防止 GUI 缩放变化时控件残留/重复
        this.clearWidgets();
        this.buildWidgets();
        if (!this.isInitialized) {
            if (this.activeSequenceIndex < 0) {
                this.loadSession();
                if (this.openSequences.isEmpty()) {
                    this.autoLoadAllDialogs();
                }
            }
            this.isInitialized = true;
        }
        if (this.activeSequenceIndex >= 0 && this.activeSequenceIndex < this.openSequences.size()) {
            this.currentSequence = this.openSequences.get(this.activeSequenceIndex);
            this.treeWidget.setSequence(this.currentSequence);
            this.propertyPanel.setSequence(this.currentSequence);
            this.syncFlowSequence();
            if (this.editingEntry != null) {
                this.propertyPanel.bindTo(this.editingEntry);
            } else {
                this.propertyPanel.unbind();
            }
        }
        this.propertyPanel.setActiveTab(EditorScreenState.get().getActivePropertyTab());
        this.propertyPanel.setVisible(this.editingEntry != null);
        this.rebuildTabButtons();
        this.configureWorkspace();
    }

    private void buildWidgets() {
        this.sidebarWidth = Mth.clamp(this.width * 22 / 100, 180, 250);
        this.inspectorWidth = Mth.clamp(this.width * 28 / 100, 260, 360);
        int btnY = 2;
        int btnHeight = 20;
        int btnX = 2;
        // 按钮宽度自适应：根据屏幕宽度和按钮数量计算，确保不溢出
        int btnCount = 7;
        int totalGap = (btnCount - 1) * EditorTheme.GAP;
        // 画布入口已移除，工具栏可使用完整宽度，避免右侧留下无意义空白。
        int maxBtnWidth = (this.width - totalGap - 4) / btnCount;
        int btnWidth = Math.min(EditorTheme.BTN_WIDTH, Math.max(36, maxBtnWidth));
        EditorButton newBtn = EditorButton.builder(Component.translatable("gui.vn_edit.new"), b -> this.onNew())
                .bounds(btnX, btnY, btnWidth, btnHeight).build();
        EditorButton saveBtn = EditorButton.builder(Component.translatable("gui.vn_edit.save"), b -> this.onSave())
                .bounds(btnX += btnWidth + EditorTheme.GAP, btnY, btnWidth, btnHeight)
                .tone(EditorButton.Tone.PRIMARY).build();
        EditorButton loadBtn = EditorButton.builder(Component.translatable("gui.vn_edit.load"), b -> this.onLoad())
                .bounds(btnX += btnWidth + EditorTheme.GAP, btnY, btnWidth, btnHeight).build();
        EditorButton testBtn = EditorButton.builder(Component.translatable("gui.vn_edit.test"), b -> this.onTest())
                .bounds(btnX += btnWidth + EditorTheme.GAP, btnY, btnWidth, btnHeight)
                .tone(EditorButton.Tone.NORMAL).build();
        EditorButton importBtn = EditorButton.builder(Component.translatable("gui.vn_edit.import"), b -> this.onImport())
                .bounds(btnX += btnWidth + EditorTheme.GAP, btnY, btnWidth, btnHeight).build();
        EditorButton propsBtn = EditorButton.builder(Component.translatable("gui.vn_edit.sequence_props"), b -> this.onSequenceProps())
                .bounds(btnX += btnWidth + EditorTheme.GAP, btnY, btnWidth, btnHeight).build();
        EditorButton validateBtn = EditorButton.builder(Component.translatable("gui.vn_edit.validation.button"), b -> this.onValidate())
                .bounds(btnX += btnWidth + EditorTheme.GAP, btnY, btnWidth, btnHeight)
                .tone(EditorButton.Tone.LIGHT).build();
        this.addRenderableWidget(newBtn);
        this.addRenderableWidget(saveBtn);
        this.addRenderableWidget(loadBtn);
        this.addRenderableWidget(testBtn);
        this.addRenderableWidget(importBtn);
        this.addRenderableWidget(propsBtn);
        this.addRenderableWidget(validateBtn);
        // 维护工具栏按钮引用，用于 tooltip 检测
        this.toolbarButtons.clear();
        this.toolbarButtons.add(newBtn);
        this.toolbarButtons.add(saveBtn);
        this.toolbarButtons.add(loadBtn);
        this.toolbarButtons.add(testBtn);
        this.toolbarButtons.add(importBtn);
        this.toolbarButtons.add(propsBtn);
        this.toolbarButtons.add(validateBtn);
        this.tabLeftArrow = EditorButton.builder(Component.literal("\u25c0"), b -> this.scrollTabs(-80))
                .bounds(0, 0, 14, 18).build();
        this.tabRightArrow = EditorButton.builder(Component.literal("\u25b6"), b -> this.scrollTabs(80))
                .bounds(0, 0, 14, 18).build();
        this.addTabBtn = EditorButton.builder(Component.literal("+"), b -> this.onNew())
                .bounds(0, 0, 18, 18).build();
        this.addRenderableWidget(this.tabLeftArrow);
        this.addRenderableWidget(this.tabRightArrow);
        this.addRenderableWidget(this.addTabBtn);
        int treeY = TOOLBAR_HEIGHT + TAB_BAR_HEIGHT;
        // 保留原有的明确文字按钮，避免新建节点入口变成难以发现的图标。
        this.addNodeBtn = EditorButton.builder(Component.literal("01  ").append(Component.translatable("gui.vn_edit.add_node")), b -> this.onAddNode())
                .bounds(0, treeY, this.sidebarWidth, EditorTheme.PANEL_HEADER_H)
                .tone(EditorButton.Tone.LIGHT).build();
        this.addRenderableWidget(this.addNodeBtn);
        int treeContentY = treeY;
        int contentHeight = this.height - treeY - STATUS_HEIGHT;
        // 搜索框单独占一行，保留原有的输入节奏与可读性。
        this.treeSearchBox = new EditBox(this.font, 0, treeY + EditorTheme.PANEL_HEADER_H + 2,
                this.sidebarWidth, 18, Component.translatable("gui.vn_edit.search"));
        this.treeSearchBox.setMaxLength(999999999);
        this.treeSearchBox.setHint(Component.translatable("gui.vn_edit.search_hint"));
        java.util.function.Consumer<String> searchResponder = text -> {
            EditorScreenState.get().setTreeSearchText(text);
            this.treeWidget.setSearchText(text);
            if (this.flowWidget != null) {
                this.flowWidget.setSearchText(text);
            }
        };
        this.treeSearchBox.setResponder(searchResponder);
        // silent 回填初值，避免触发 responder（setSequence 会权威回填搜索态）
        this.treeSearchBox.setResponder(null);
        this.treeSearchBox.setValue(EditorScreenState.get().getTreeSearchText());
        this.treeSearchBox.setResponder(searchResponder);
        this.addRenderableWidget(this.treeSearchBox);
        int treeWidgetY = treeY + EditorTheme.PANEL_HEADER_H + 22;
        int treeWidgetH = contentHeight - EditorTheme.PANEL_HEADER_H - 22;
        this.treeWidget = new DialogTreeWidget(0, treeWidgetY, this.sidebarWidth, treeWidgetH, this.font);
        this.treeWidget.setCallbacks(this::onEntrySelected, this::onEntryDelete, this::onEntryAddChild);
        this.addRenderableWidget(this.treeWidget);
        this.flowWidget = new FlowViewWidget(this.sidebarWidth + 1, treeContentY,
                Math.max(1, this.width - this.sidebarWidth - 1), contentHeight, this.font);
        this.flowWidget.setCallbacks(this::onEntrySelected, this::onEntryDelete, this::onEntryAddChild);
        this.addRenderableWidget(this.flowWidget);
        int propX = this.sidebarWidth + 1;
        int propWidth = this.width - propX;
        this.propertyPanel = new PropertyPanel(propX, treeContentY, propWidth, contentHeight, this.font);
        this.addRenderableWidget(this.propertyPanel);
        // 注入字段变脏回调：属性页内 Option 变脏时标记当前序列为未保存，使字段编辑联动标签页 * 标记
        this.propertyPanel.setDirtyListener(() -> this.markDirty(this.currentSequence));
    }

    /** 将编辑器统一配置为导航、流程与检查器三栏工作区。 */
    private void configureWorkspace() {
        this.treeSearchBox.setVisible(true);
        this.addNodeBtn.visible = true;
        this.treeWidget.visible = true;
        this.flowWidget.visible = true;
        this.treeWidget.setSequence(this.currentSequence);
        this.flowWidget.setSequence(this.currentSequence);
        this.flowWidget.setSearchText(EditorScreenState.get().getTreeSearchText());
        this.applyPanelLayout();
    }

    /**
     * 同步流程视图序列绑定（序列切换/加载后调用）。
     */
    private void syncWorkspaceSequence() {
        this.syncFlowSequence();
        this.configureWorkspace();
    }

    /** 按面板显隐停靠检查器，并为中央流程视图保留稳定的最小宽度。 */
    private void applyPanelLayout() {
        if (this.flowWidget == null || this.propertyPanel == null) {
            return;
        }
        boolean panelDocked = this.propertyPanel.visible && this.editingEntry != null;
        int panelX;
        int panelW;
        if (panelDocked) {
            int minCenterWidth = Math.min(320, Math.max(1, this.width - this.sidebarWidth - 2));
            panelX = Math.max(this.sidebarWidth + 1,
                    Math.min(this.width - this.inspectorWidth, this.width - minCenterWidth));
            panelW = this.width - panelX;
        } else {
            panelX = this.width;
            panelW = this.width - panelX;
        }
        if (this.propertyPanel.getX() != panelX || this.propertyPanel.getWidth() != panelW) {
            this.propertyPanel.setPosition(panelX, this.propertyPanel.getY());
            this.propertyPanel.setWidth(panelW);
            this.propertyPanel.relayout();
        }
        int flowX = this.sidebarWidth + 1;
        this.flowWidget.setPosition(flowX, this.flowWidget.getY());
        this.flowWidget.setWidth(Math.max(1, panelX - flowX - 1));
    }

    /** 当前导航选中的节点，供 Delete/F2/Ctrl+C/D 等快捷键定位目标。 */
    private DialogEntry getActiveSelectedEntry() {
        return this.treeWidget != null ? this.treeWidget.getSelectedEntry() : null;
    }

    private void scrollTabs(int delta) {
        this.tabScrollOffset = Mth.clamp(this.tabScrollOffset + delta, 0, Math.max(0, this.getTotalTabsWidth() - this.getVisibleTabWidth()));
        this.rebuildTabButtons();
    }

    private int getTotalTabsWidth() {
        int total = 0;
        for (TabButton btn : this.tabButtons) {
            total += btn.getWidth() + 2;
        }
        return total;
    }

    private int getVisibleTabWidth() {
        return this.width - TAB_AREA_LEFT - TAB_AREA_RIGHT_MARGIN;
    }

    private void rebuildTabButtons() {
        this.tabButtons.clear();
        int tabX = TAB_AREA_LEFT - this.tabScrollOffset;
        int tabY = TOOLBAR_HEIGHT + 3;
        for (int i = 0; i < this.openSequences.size(); i++) {
            DialogSequence seq = this.openSequences.get(i);
            String title = seq.getId() != null ? seq.getId() : "untitled";
            // 有未保存修改的标签显示 * 前缀
            boolean dirty = seq.getId() != null && this.dirtySequences.contains(seq.getId());
            String displayTitle = (dirty ? "* " : "") + title;
            int rawWidth = Math.max(40, this.font.width(displayTitle) + 10);
            int width = Math.min(rawWidth, MAX_TAB_WIDTH);
            if (rawWidth > MAX_TAB_WIDTH) {
                displayTitle = this.font.plainSubstrByWidth(displayTitle, 90) + "...";
            }
            int index = i;
            TabButton tabBtn = new TabButton(tabX, tabY, width, 18, Component.literal(displayTitle),
                    b -> this.switchToSequence(index), index, this::onTabRightClick, this::onRenameTab);
            this.tabButtons.add(tabBtn);
            tabX += width + 2;
        }
        int addBtnX = this.width - TAB_AREA_RIGHT_MARGIN + 20;
        this.addTabBtn.setX(addBtnX);
        this.addTabBtn.setY(tabY);
        int arrowY = tabY;
        this.tabLeftArrow.setX(addBtnX - 16);
        this.tabLeftArrow.setY(arrowY);
        this.tabRightArrow.setX(addBtnX + 20);
        this.tabRightArrow.setY(arrowY);
        int totalWidth = this.getTotalTabsWidth();
        int visibleWidth = this.getVisibleTabWidth();
        this.tabLeftArrow.visible = this.tabScrollOffset > 0;
        this.tabRightArrow.visible = totalWidth > visibleWidth && this.tabScrollOffset < totalWidth - visibleWidth;
    }

    private void onRenameTab(int index) {
        if (index < 0 || index >= this.openSequences.size()) {
            return;
        }
        DialogSequence seq = this.openSequences.get(index);
        if (seq == null) {
            return;
        }
        Minecraft.getInstance().setScreen(new InputDialogScreen(Component.translatable("gui.vn_edit.rename.title"), seq.getId(), newId -> {
            if (!newId.isEmpty() && !newId.equals(seq.getId())) {
                if (!isSafeDocumentId(newId) || hasSequenceId(newId, seq)) {
                    this.setStatus(Component.translatable("gui.vn_edit.rename.failed").getString(), StatusLevel.ERROR);
                    return;
                }
                String oldId = seq.getId();
                Path oldFile = EditorConfig.DIALOG_JSON_DIR.resolve(oldId + ".json");
                Path newFile = EditorConfig.DIALOG_JSON_DIR.resolve(newId + ".json");
                try {
                    if (Files.exists(newFile)) {
                        this.setStatus(Component.translatable("gui.vn_edit.rename.failed").getString(), StatusLevel.ERROR);
                        return;
                    }
                    if (Files.exists(oldFile)) {
                        try {
                            Files.move(oldFile, newFile, StandardCopyOption.ATOMIC_MOVE);
                        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                            Files.move(oldFile, newFile);
                        }
                    }
                } catch (IOException e) {
                    Dialog.LOGGER.error("Failed to rename dialog file", e);
                    this.setStatus(Component.translatable("gui.vn_edit.rename.failed").getString(), StatusLevel.ERROR);
                    return;
                }
                seq.setId(newId);
                if (this.activeSequenceIndex == index) {
                    this.currentSequence = seq;
                }
                this.dirtySequences.remove(oldId);
                this.dirtySequences.add(newId);
                this.saveSession();
                this.rebuildTabButtons();
                this.setStatus(Component.translatable("gui.vn_edit.rename.success", newId).getString(), StatusLevel.SUCCESS);
            }
        }, this));
    }

    private void switchToSequence(int index) {
        if (index < 0 || index >= this.openSequences.size()) {
            return;
        }
        this.activeSequenceIndex = index;
        this.currentSequence = this.openSequences.get(index);
        // 历史栈跟随序列：切换即清空（快照属于旧序列，跨序列还原会错乱）
        this.history.clear();
        // 切换到不同对话序列时清空节点选中与树滚动状态：旧 ID 在新序列中无意义
        EditorScreenState.get().setSelectedNodeId(null);
        EditorScreenState.get().setTreeScrollOffset(0);
        this.treeWidget.setSequence(this.currentSequence);
        this.propertyPanel.setSequence(this.currentSequence);
        this.syncFlowSequence();
        this.syncWorkspaceSequence();
        this.editingEntry = null;
        this.propertyPanel.unbind();
        this.propertyPanel.setVisible(false);
        this.applyPanelLayout();
        this.setStatus(Component.translatable("gui.vn_edit.status.switched", this.currentSequence.getId()).getString(), StatusLevel.NEUTRAL);
    }

    private void onNew() {
        Minecraft.getInstance().setScreen(new InputDialogScreen(Component.translatable("gui.vn_edit.new_dialog.title"), "new_dialog", id -> {
            if (id == null || id.isBlank()) {
                id = "new_dialog";
            }
            if (!isSafeDocumentId(id) || hasSequenceId(id)) {
                this.setStatus(Component.translatable("gui.vn_edit.status.id_exists", id).getString(), StatusLevel.ERROR);
                return;
            }
            DialogSequence seq = new DialogSequence();
            seq.setId(id);
            seq.setEntries(new DialogEntry[0]);
            this.openSequences.add(seq);
            this.activeSequenceIndex = this.openSequences.size() - 1;
            this.currentSequence = seq;
            this.treeWidget.setSequence(this.currentSequence);
            this.propertyPanel.setSequence(this.currentSequence);
            this.syncFlowSequence();
        this.syncWorkspaceSequence();
        this.editingEntry = null;
            this.propertyPanel.unbind();
            this.propertyPanel.setVisible(false);
            this.rebuildTabButtons();
            this.setStatus(Component.translatable("gui.vn_edit.status.new").getString(), StatusLevel.NEUTRAL);
        }, this));
    }

    private void onAddNode() {
        if (this.currentSequence == null) {
            return;
        }
        Minecraft.getInstance().setScreen(new InputDialogScreen(Component.translatable("gui.vn_edit.new_node.title"), "", newId -> {
            if (newId.isEmpty()) {
                newId = "node_" + System.currentTimeMillis();
            }
            if (this.currentSequence.findEntryById(newId) != null) {
                this.setStatus(Component.translatable("gui.vn_edit.status.id_exists", newId).getString(), StatusLevel.ERROR);
                return;
            }
            DialogEntry newEntry = DialogEntry.builder().id(newId).text(new JsonPrimitive("")).build();
            DialogEntry[] entries = this.currentSequence.getEntries();
            ArrayList<DialogEntry> list = entries != null ? new ArrayList<>(List.of(entries)) : new ArrayList<>();
            this.pushHistory();
            list.add(newEntry);
            this.currentSequence.setEntries(list.toArray(new DialogEntry[0]));
            this.treeWidget.setSequence(this.currentSequence);
            this.propertyPanel.setSequence(this.currentSequence);
            this.onEntrySelected(newEntry);
            EditorScreenState.get().setSelectedNodeId(newId);
            this.markDirty(this.currentSequence);
            this.setStatus(Component.translatable("gui.vn_edit.status.node_added", newId).getString(), StatusLevel.SUCCESS);
        }, this));
    }

    private void onSave() {
        if (this.currentSequence == null) {
            return;
        }
        // 保存前验证：检测悬空引用（nextId / option.targetId 指向不存在的节点）
        List<DialogValidator.Issue> validationIssues = DialogValidator.validate(this.currentSequence);
        boolean ok = this.saveCurrentSequenceToFile();
        if (!ok) {
            // 保存失败：保留 dirty 标记（*），显示错误（常驻），不 reload，避免用户误以为已保存导致数据丢失
            this.setStatus(Component.translatable("gui.vn_edit.status.save_failed", this.currentSequence.getId()).getString(), StatusLevel.ERROR);
            return;
        }
        this.saveSession();
        this.markClean(this.currentSequence);
        // 保存成功后重置属性页字段 dirty 基线，清除行级 dirty 视觉
        this.propertyPanel.onSequenceSaved();
        long errorCount = validationIssues.stream().filter(i -> i.severity() == DialogValidator.Severity.ERROR).count();
        long warningCount = validationIssues.stream().filter(i -> i.severity() == DialogValidator.Severity.WARNING).count();
        if (errorCount == 0 && warningCount == 0) {
            this.setStatus(Component.translatable("gui.vn_edit.status.saved", this.currentSequence.getId()).getString(), StatusLevel.SUCCESS);
        } else {
            // 保存成功但存在悬空引用，附加警告（黄色，4 秒消失）
            this.setStatus(Component.translatable("gui.vn_edit.status.saved_with_warnings",
                    this.currentSequence.getId(), errorCount + warningCount).getString(), StatusLevel.WARNING);
        }
        if (Minecraft.getInstance().player != null) {
            NetworkHandler.sendExecuteCommandToServer("dialog reload");
        }
    }

    /**
     * 扫描序列中所有 nextId 和选项 targetId，返回指向不存在节点的引用列表。
     * 用于保存前向用户提示潜在的流程断裂问题。
     */
    private List<String> findDanglingReferences(DialogSequence seq) {
        List<String> result = new ArrayList<>();
        DialogEntry[] entries = seq.getEntries();
        if (entries == null) {
            return result;
        }
        // 收集所有已存在的节点 ID
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (DialogEntry e : entries) {
            if (e.getId() != null) {
                ids.add(e.getId());
            }
        }
        for (DialogEntry e : entries) {
            String nextId = e.getNextId();
            if (nextId != null && !nextId.isEmpty() && !ids.contains(nextId)) {
                result.add(e.getId() + " -> next:" + nextId);
            }
            DialogOption[] opts = e.getOptions();
            if (opts == null) {
                continue;
            }
            for (DialogOption o : opts) {
                String t = o.getTargetId();
                if (t != null && !t.isEmpty() && !ids.contains(t)) {
                    result.add(e.getId() + " -> option:" + t);
                }
            }
        }
        return result;
    }

    /** 标记序列为有未保存修改 */
    public void markDirty(DialogSequence seq) {
        if (seq != null && seq.getId() != null && this.dirtySequences.add(seq.getId())) {
            // 状态变化时刷新标签 * 显示（TabButton 为自绘列表，重建开销小）
            this.rebuildTabButtons();
        }
    }

    /** 标记序列为已保存 */
    private void markClean(DialogSequence seq) {
        if (seq != null && seq.getId() != null && this.dirtySequences.remove(seq.getId())) {
            this.rebuildTabButtons();
        }
    }

    /** 是否有未保存的修改 */
    private boolean hasUnsavedChanges() {
        return !this.dirtySequences.isEmpty();
    }

    /**
     * 保存当前序列到 JSON 文件。
     * @return true 保存成功；false 保存失败（IOException），调用方据此决定是否 markClean 及反馈
     */
    private boolean saveCurrentSequenceToFile() {
        if (this.currentSequence == null) {
            return false;
        }
        String id = this.currentSequence.getId();
        if (id == null || id.isEmpty()) {
            id = "untitled";
        }
        if (!isSafeDocumentId(id)) {
            Dialog.LOGGER.warn("Refusing to save dialog with unsafe ID: {}", id);
            return false;
        }
        String json = PRETTY_GSON.toJson(this.currentSequence);
        Path path = EditorConfig.DIALOG_JSON_DIR.resolve(id + ".json");
        Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(EditorConfig.DIALOG_JSON_DIR);
            Files.writeString(tempPath, json);
            try {
                Files.move(tempPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            Dialog.LOGGER.error("Failed to save dialog {}: {}", id, e.getMessage());
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException cleanupError) {
                Dialog.LOGGER.warn("Failed to clean temporary dialog file {}", tempPath);
            }
            return false;
        }
    }

    private void onLoad() {
        FileBrowserScreen.open(EditorConfig.DIALOG_JSON_DIR.toFile(), new String[]{"json"}, path -> {
            try {
                if (path == null || !path.endsWith(".json") || path.contains("..")) {
                    throw new IOException("Unsafe dialog file path");
                }
                String json = Files.readString(EditorConfig.DIALOG_JSON_DIR.resolve(path).normalize());
                DialogSequence seq = DialogManager.GSON.fromJson(json, DialogSequence.class);
                if (seq == null || seq.getId() == null || !isSafeDocumentId(seq.getId()) || hasSequenceId(seq.getId())) {
                    throw new IOException("Duplicate or unsafe dialog ID");
                }
                this.openSequences.add(seq);
                this.activeSequenceIndex = this.openSequences.size() - 1;
                this.currentSequence = seq;
                this.treeWidget.setSequence(this.currentSequence);
                this.propertyPanel.setSequence(this.currentSequence);
                this.syncFlowSequence();
                this.editingEntry = null;
                this.propertyPanel.unbind();
                this.propertyPanel.setVisible(false);
                this.rebuildTabButtons();
                this.setStatus(Component.translatable("gui.vn_edit.status.loaded", seq.getId()).getString(), StatusLevel.SUCCESS);
            } catch (Exception e) {
                Dialog.LOGGER.error("Failed to load dialog", e);
                this.setStatus(Component.translatable("gui.vn_edit.status.load_failed").getString(), StatusLevel.ERROR);
            }
        }, this);
    }

    private void onTest() {
        if (this.currentSequence == null) {
            return;
        }
        List<DialogValidator.Issue> validationIssues = DialogValidator.validate(this.currentSequence);
        long validationErrors = validationIssues.stream()
                .filter(i -> i.severity() == DialogValidator.Severity.ERROR).count();
        if (validationErrors > 0) {
            this.setStatus(Component.translatable("gui.vn_edit.validation.blocked_test", validationErrors).getString(), StatusLevel.ERROR);
            Minecraft.getInstance().setScreen(new DialogValidationScreen(this.currentSequence, validationIssues,
                    this::onValidationIssueSelected, this));
            return;
        }
        if (!this.saveCurrentSequenceToFile()) {
            // 保存失败：不进入测试，避免测试的是旧数据。错误消息常驻提示用户先修复保存问题。
            this.setStatus(Component.translatable("gui.vn_edit.status.save_failed", this.currentSequence.getId()).getString(), StatusLevel.ERROR);
            return;
        }
        this.saveSession();
        // 设置测试返回屏幕：对话关闭后回到编辑器界面，无需重新打开
        DialogManager.getInstance().setTestReturnScreen(this);
        DialogSequence previewSequence = DialogManager.GSON.fromJson(
                DialogManager.GSON.toJson(this.currentSequence), DialogSequence.class);
        // 仅对试玩副本允许 Esc 关闭，不污染正式序列的 allowClose 运行时语义。
        previewSequence.setAllowClose(true);
        String json = DialogManager.GSON.toJson(previewSequence);
        DialogManager.getInstance().receiveAndShowPlayerSpecificDialog(this.currentSequence.getId(), json);
    }

    private void onImport() {
        Minecraft.getInstance().setScreen(new DialogImportScreen(this, fileName -> {
            if (fileName == null || fileName.isEmpty()) {
                this.setStatus(Component.translatable("gui.vn_edit.import.failed").getString(), StatusLevel.ERROR);
            } else {
                Path importedPath = EditorConfig.DIALOG_JSON_DIR.resolve(fileName).normalize();
                this.loadImportedDialog(importedPath);
            }
        }));
    }

    private void onSequenceProps() {
        if (this.currentSequence == null) {
            return;
        }
        SequencePropertiesScreen propsScreen = new SequencePropertiesScreen(this.currentSequence, seq -> {
            this.markDirty(seq);
            this.treeWidget.setSequence(seq);
            this.syncWorkspaceSequence();
            this.rebuildTabButtons();
            this.setStatus(Component.translatable("gui.vn_edit.status.props_saved").getString(), StatusLevel.SUCCESS);
        }, this);
        // 注入删除回调：序列属性屏"删除"按钮触发，定位到当前序列索引执行删除文件
        propsScreen.setOnDelete(seq -> {
            int idx = this.openSequences.indexOf(seq);
            if (idx >= 0) {
                this.onDeleteTab(idx);
            }
        });
        Minecraft.getInstance().setScreen(propsScreen);
    }

    private void loadImportedDialog(Path dialogFile) {
        try {
            Path base = EditorConfig.DIALOG_JSON_DIR.toAbsolutePath().normalize();
            Path normalized = dialogFile.toAbsolutePath().normalize();
            if (!normalized.startsWith(base) || !normalized.getFileName().toString().endsWith(".json")) {
                throw new IOException("Unsafe import path");
            }
            String json = Files.readString(normalized);
            DialogSequence seq = DialogManager.GSON.fromJson(json, DialogSequence.class);
            if (seq != null && seq.getId() != null) {
                if (!isSafeDocumentId(seq.getId()) || hasSequenceId(seq.getId())) {
                    this.setStatus(Component.translatable("gui.vn_edit.status.id_exists", seq.getId()).getString(), StatusLevel.ERROR);
                    return;
                }
                this.openSequences.add(seq);
                this.activeSequenceIndex = this.openSequences.size() - 1;
                this.currentSequence = seq;
                this.treeWidget.setSequence(this.currentSequence);
                this.propertyPanel.setSequence(this.currentSequence);
                this.syncFlowSequence();
                this.editingEntry = null;
                this.propertyPanel.unbind();
                this.propertyPanel.setVisible(false);
                this.rebuildTabButtons();
                this.setStatus(Component.translatable("gui.vn_edit.import.success", seq.getId()).getString(), StatusLevel.SUCCESS);
            } else {
                this.setStatus(Component.translatable("gui.vn_edit.import.invalid_format").getString(), StatusLevel.ERROR);
            }
        } catch (Exception e) {
            Dialog.LOGGER.error("Import failed", e);
            this.setStatus(Component.translatable("gui.vn_edit.import.failed").getString(), StatusLevel.ERROR);
        }
    }

    // ===== 撤销/重做（JSON 快照式，借鉴 MainGraph HistoryManager） =====

    /** 序列结构快照：entries 数组 JSON（DialogManager.GSON 与存盘同配置，可无损还原）。 */
    private String snapshotSequence() {
        if (this.currentSequence == null || this.currentSequence.getEntries() == null) {
            return null;
        }
        return DialogManager.GSON.toJson(this.currentSequence.getEntries());
    }

    /** 结构变更前压入历史（栈顶去重，push 会清空 redo 栈）。 */
    private void pushHistory() {
        String snapshot = this.snapshotSequence();
        if (snapshot != null) {
            this.history.push(snapshot);
        }
    }

    /** 撤销：还原上一个快照。返回 false 表示无可撤销。 */
    private boolean performUndo() {
        if (this.currentSequence == null) {
            return false;
        }
        String snapshot = this.history.undo(this.snapshotSequence());
        if (snapshot == null) {
            return false;
        }
        this.applyHistorySnapshot(snapshot);
        this.setStatus(Component.translatable("gui.vn_edit.status.undone").getString(), StatusLevel.NEUTRAL);
        return true;
    }

    /** 重做：还原下一个快照。返回 false 表示无可重做。 */
    private boolean performRedo() {
        if (this.currentSequence == null) {
            return false;
        }
        String snapshot = this.history.redo(this.snapshotSequence());
        if (snapshot == null) {
            return false;
        }
        this.applyHistorySnapshot(snapshot);
        this.setStatus(Component.translatable("gui.vn_edit.status.redone").getString(), StatusLevel.NEUTRAL);
        return true;
    }

    /** 应用历史快照：还原 entries 并联动刷新导航/流程/属性面板（选中节点按 ID 重新定位）。 */
    private void applyHistorySnapshot(String snapshot) {
        DialogEntry[] entries = DialogManager.GSON.fromJson(snapshot, DialogEntry[].class);
        if (entries == null) {
            return;
        }
        this.currentSequence.setEntries(entries);
        this.markDirty(this.currentSequence);
        this.treeWidget.setSequence(this.currentSequence);
        this.propertyPanel.setSequence(this.currentSequence);
        // 属性面板绑定节点可能已被删除/重建：按 ID 重新定位，找不到则收起
        String editingId = this.editingEntry != null ? this.editingEntry.getId() : null;
        DialogEntry rebound = editingId != null ? this.currentSequence.findEntryById(editingId) : null;
        if (rebound != null) {
            this.editingEntry = rebound;
            this.propertyPanel.bindTo(rebound);
        } else if (this.propertyPanel.visible) {
            this.editingEntry = null;
            this.propertyPanel.unbind();
            this.propertyPanel.setVisible(false);
            this.applyPanelLayout();
        }
    }

    private void onEntrySelected(DialogEntry entry) {
        this.editingEntry = entry;
        if (this.flowWidget != null) {
            this.flowWidget.selectEntryById(entry == null ? null : entry.getId());
        }
        if (this.propertyPanel != null) {
            if (entry != null) {
                this.propertyPanel.bindTo(entry);
                this.propertyPanel.setVisible(true);
            } else {
                this.propertyPanel.unbind();
                this.propertyPanel.setVisible(false);
            }
        }
        // 面板显隐/停靠变化 → 重新计算检查器停靠位置
        this.applyPanelLayout();
    }

    private void onEntryDelete(DialogEntry entry) {
        if (this.currentSequence == null || entry == null) {
            return;
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(new EditorConfirmScreen(
                    Component.translatable("gui.vn_edit.delete_confirm.title"),
                    Component.translatable("gui.vn_edit.delete_confirm.message", entry.getId()),
                    confirmed -> {
                        if (confirmed) {
                            this.performDeleteEntry(entry);
                        }
                    }, this));
        }
    }

    private void performDeleteEntry(DialogEntry entry) {
        DialogEntry[] entries = this.currentSequence.getEntries();
        ArrayList<DialogEntry> list = new ArrayList<>(List.of(entries));
        this.pushHistory();
        list.remove(entry);
        for (DialogEntry e : list) {
            DialogOption[] options;
            if (entry.getId().equals(e.getNextId())) {
                e.setNextId(null);
            }
            if ((options = e.getOptions()) == null) {
                continue;
            }
            ArrayList<DialogOption> validOptions = new ArrayList<>();
            for (DialogOption opt : options) {
                if (entry.getId().equals(opt.getTargetId())) {
                    opt.setTargetId(null);
                }
                if (opt.getTargetId() != null && !opt.getTargetId().isEmpty()) {
                    validOptions.add(opt);
                }
            }
            e.setOptions(validOptions.isEmpty() ? null : validOptions.toArray(new DialogOption[0]));
        }
        this.currentSequence.setEntries(list.toArray(new DialogEntry[0]));
        this.treeWidget.setSequence(this.currentSequence);
        this.propertyPanel.setSequence(this.currentSequence);
        if (this.editingEntry == entry) {
            this.editingEntry = this.currentSequence.getFirstEntry();
            if (this.editingEntry != null) {
                this.propertyPanel.bindTo(this.editingEntry);
                this.propertyPanel.setVisible(true);
            } else {
                this.propertyPanel.unbind();
                this.propertyPanel.setVisible(false);
            }
        }
        this.markDirty(this.currentSequence);
        this.applyPanelLayout();
        this.setStatus(Component.translatable("gui.vn_edit.status.node_deleted", entry.getId()).getString(), StatusLevel.NEUTRAL);
    }

    /**
     * 添加后继节点：新建节点插入父节点数组位置之后，并在父节点无分支时建立显式 next 边
     * （节点菜单"添加后继节点"入口；父节点已有选项分支时不自动连线，避免改变分支语义）。
     */
    private void onEntryAddChild(DialogEntry parentEntry) {
        if (this.currentSequence == null || parentEntry == null) {
            return;
        }
        String newId = this.generateUniqueNodeId(parentEntry.getId() + "_next");
        DialogEntry child = DialogEntry.builder().id(newId).text(new JsonPrimitive("")).build();
        DialogEntry[] entries = this.currentSequence.getEntries();
        ArrayList<DialogEntry> list = entries != null ? new ArrayList<>(List.of(entries)) : new ArrayList<>();
        this.pushHistory();
        // 插到父节点之后：无显式引用时运行时按数组顺序回退，隐式顺序边天然成立
        int insertAt = list.indexOf(parentEntry);
        list.add(insertAt >= 0 ? insertAt + 1 : list.size(), child);
        this.currentSequence.setEntries(list.toArray(new DialogEntry[0]));
        boolean parentHasBranch = parentEntry.getOptions() != null && parentEntry.getOptions().length > 0;
        if (!parentHasBranch && (parentEntry.getNextId() == null || parentEntry.getNextId().isEmpty())) {
            parentEntry.setNextId(newId);
        }
        this.treeWidget.setSequence(this.currentSequence);
        this.propertyPanel.setSequence(this.currentSequence);
        this.onEntrySelected(child);
        EditorScreenState.get().setSelectedNodeId(newId);
        this.markDirty(this.currentSequence);
        this.setStatus(Component.translatable("gui.vn_edit.status.node_added", newId).getString(), StatusLevel.SUCCESS);
    }

    private void saveSession() {
        if (this.openSequences.isEmpty()) {
            return;
        }
        ArrayList<String> ids = new ArrayList<>();
        for (DialogSequence seq : this.openSequences) {
            ids.add(seq.getId());
        }
        try {
            Files.createDirectories(EditorConfig.CONFIG_ROOT);
            Files.writeString(SESSION_FILE, DialogManager.GSON.toJson(ids));
        } catch (IOException e) {
            Dialog.LOGGER.error("Failed to save editor session", e);
        }
    }

    /** 打开可操作的验证面板；保存/试玩前的诊断也复用同一验证器。 */
    private void onValidate() {
        if (this.currentSequence == null) {
            this.setStatus(Component.translatable("gui.vn_edit.validation.no_sequence").getString(), StatusLevel.WARNING);
            return;
        }
        List<DialogValidator.Issue> issues = DialogValidator.validate(this.currentSequence);
        Minecraft.getInstance().setScreen(new DialogValidationScreen(this.currentSequence, issues,
                this::onValidationIssueSelected, this));
    }

    private void onValidationIssueSelected(DialogValidator.Issue issue) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this);
        }
        if (issue == null) {
            return;
        }
        String nodeId = issue.nodeId();
        DialogEntry entry = nodeId == null || this.currentSequence == null
                ? null : this.currentSequence.findEntryById(nodeId);
        if (entry != null) {
            this.onEntrySelected(entry);
            EditorScreenState.get().setSelectedNodeId(nodeId);
            int tab = switch (issue.code()) {
                case "EMPTY_TEXT" -> 0;
                case "DANGLING_NEXT", "DANGLING_OPTION_TARGET", "OPTION_WITHOUT_TARGET", "CYCLE", "INVALID_START" -> 2;
                default -> 0;
            };
            this.propertyPanel.setActiveTab(tab);
        }
        this.setStatus(issue.code() + (nodeId == null ? "" : " · " + nodeId),
                issue.severity() == DialogValidator.Severity.ERROR ? StatusLevel.ERROR : StatusLevel.WARNING);
    }

    private void syncFlowSequence() {
        if (this.flowWidget != null) {
            this.flowWidget.setSequence(this.currentSequence);
            this.flowWidget.setSearchText(EditorScreenState.get().getTreeSearchText());
        }
    }

    /** 文档 ID 既是运行时键也是文件名，必须限制为安全的单层路径。 */
    private static boolean isSafeDocumentId(String id) {
        return id != null && !id.isBlank() && !id.equals(".") && !id.equals("..")
                && id.matches("[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*");
    }

    private boolean hasSequenceId(String id) {
        return hasSequenceId(id, null);
    }

    private boolean hasSequenceId(String id, DialogSequence exclude) {
        if (id == null) {
            return false;
        }
        for (DialogSequence sequence : this.openSequences) {
            if (sequence != exclude && id.equals(sequence.getId())) {
                return true;
            }
        }
        return false;
    }

    private void loadSession() {
        if (!Files.exists(SESSION_FILE)) {
            return;
        }
        try {
            String json = Files.readString(SESSION_FILE);
            Type listType = new TypeToken<List<String>>(){}.getType();
            List<String> ids = DialogManager.GSON.fromJson(json, listType);
            if (ids == null || ids.isEmpty()) {
                return;
            }
            for (String id : ids) {
                if (!isSafeDocumentId(id)) {
                    continue;
                }
                Path dialogFile = EditorConfig.DIALOG_JSON_DIR.resolve(id + ".json").normalize();
                if (!Files.exists(dialogFile)) {
                    continue;
                }
                String dialogJson = Files.readString(dialogFile);
                DialogSequence seq = DialogManager.GSON.fromJson(dialogJson, DialogSequence.class);
                if (seq == null) {
                    continue;
                }
                if (seq.getId() == null || !isSafeDocumentId(seq.getId()) || hasSequenceId(seq.getId())) {
                    continue;
                }
                this.openSequences.add(seq);
            }
            if (!this.openSequences.isEmpty()) {
                this.activeSequenceIndex = 0;
                this.currentSequence = this.openSequences.get(0);
                this.treeWidget.setSequence(this.currentSequence);
                this.propertyPanel.setSequence(this.currentSequence);
                this.editingEntry = null;
                this.propertyPanel.unbind();
                this.propertyPanel.setVisible(false);
                this.rebuildTabButtons();
            }
        } catch (Exception e) {
            Dialog.LOGGER.error("Failed to load editor session", e);
        }
    }

    private void autoLoadAllDialogs() {
        try {
            Files.createDirectories(EditorConfig.DIALOG_JSON_DIR);
            Files.list(EditorConfig.DIALOG_JSON_DIR)
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> {
                        try {
                            String json = Files.readString(path);
                            DialogSequence seq = DialogManager.GSON.fromJson(json, DialogSequence.class);
                            if (seq != null && seq.getId() != null && isSafeDocumentId(seq.getId())
                                    && !hasSequenceId(seq.getId())) {
                                this.openSequences.add(seq);
                            }
                        } catch (IOException e) {
                            Dialog.LOGGER.error("Auto-load failed: {}", path, e);
                        }
                    });
            if (!this.openSequences.isEmpty()) {
                this.activeSequenceIndex = 0;
                this.currentSequence = this.openSequences.get(0);
                this.treeWidget.setSequence(this.currentSequence);
                this.propertyPanel.setSequence(this.currentSequence);
                this.editingEntry = null;
                this.propertyPanel.unbind();
                this.propertyPanel.setVisible(false);
                this.rebuildTabButtons();
            }
        } catch (IOException e) {
            Dialog.LOGGER.error("Failed to list dialogs", e);
        }
    }

    /**
     * 右键标签入口：弹出"关闭标签"确认。
     * 关闭标签仅从编辑器移除，不删除对话文件（与 onDeleteTab 删除文件语义分离）。
     * 脏序列关闭时提示未保存修改将丢失。
     */
    private void onTabRightClick(int index) {
        if (index < 0 || index >= this.openSequences.size()) {
            return;
        }
        DialogSequence seq = this.openSequences.get(index);
        boolean dirty = seq.getId() != null && this.dirtySequences.contains(seq.getId());
        Component message = dirty
                ? Component.translatable("gui.vn_edit.close_tab.dirty_message", seq.getId())
                : Component.translatable("gui.vn_edit.close_tab.message", seq.getId());
        if (this.minecraft != null) {
            this.minecraft.setScreen(new EditorConfirmScreen(
                    Component.translatable("gui.vn_edit.close_tab.title"),
                    message,
                    confirmed -> {
                        if (confirmed) {
                            this.closeTab(index);
                        }
                    }, this));
        }
    }

    /**
     * 关闭标签：仅从 openSequences 移除并调整状态，不删除对话文件。
     * 文件仍保留在磁盘，可通过读取/导入重新打开。
     */
    private void closeTab(int index) {
        if (index < 0 || index >= this.openSequences.size()) {
            return;
        }
        DialogSequence seq = this.openSequences.get(index);
        if (seq.getId() != null) {
            this.dirtySequences.remove(seq.getId());
        }
        this.openSequences.remove(index);
        if (this.openSequences.isEmpty()) {
            this.activeSequenceIndex = -1;
            this.currentSequence = null;
            this.treeWidget.setSequence(null);
            this.propertyPanel.unbind();
            this.editingEntry = null;
            this.propertyPanel.setVisible(false);
            this.syncWorkspaceSequence();
            this.applyPanelLayout();
        } else if (this.activeSequenceIndex >= index) {
            this.activeSequenceIndex = Math.min(this.activeSequenceIndex, this.openSequences.size() - 1);
            this.switchToSequence(this.activeSequenceIndex);
        } else {
            this.rebuildTabButtons();
        }
        this.saveSession();
    }

    /**
     * 删除对话文件（危险操作）：从磁盘删除 JSON 并从编辑器移除。
     * 入口在序列属性屏的"删除"按钮，避免与"关闭标签"混淆。
     */
    private void onDeleteTab(int index) {
        if (index < 0 || index >= this.openSequences.size()) {
            return;
        }
        DialogSequence seq = this.openSequences.get(index);
        if (this.minecraft != null) {
            this.minecraft.setScreen(new EditorConfirmScreen(
                    Component.translatable("gui.vn_edit.delete_dialog.title"),
                    Component.translatable("gui.vn_edit.delete_dialog.message", seq.getId()),
                    confirmed -> {
                        if (confirmed) {
                            Path file = EditorConfig.DIALOG_JSON_DIR.resolve(seq.getId() + ".json");
                            try {
                                Files.deleteIfExists(file);
                            } catch (IOException e) {
                                Dialog.LOGGER.error("Failed to delete file: {}", file);
                            }
                            this.closeTab(index);
                        }
                    }, this));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        int contentTop = TOOLBAR_HEIGHT + TAB_BAR_HEIGHT;
        int contentBottom = this.height - STATUS_HEIGHT;
        // 顶部工具栏、标签栏和工作区使用连续的色带与分隔线，避免控件像漂浮在黑底上。
        graphics.fill(0, 0, this.width, TOOLBAR_HEIGHT, EditorTheme.BG_SURFACE);
        graphics.fill(0, 0, 5, TOOLBAR_HEIGHT, EditorTheme.ACCENT);
        EditorTheme.drawHatch(graphics, Math.max(0, this.width - 100), 0, 100, TOOLBAR_HEIGHT,
                EditorTheme.ACCENT_TINT);
        graphics.drawString(this.font, "VN // DIALOG EDITOR", Math.max(8, this.width - 148), 11,
                EditorTheme.TEXT_MUTED, false);
        graphics.fill(0, TOOLBAR_HEIGHT - 1, this.width, TOOLBAR_HEIGHT, EditorTheme.BORDER);
        int clipRight = this.width - TAB_AREA_RIGHT_MARGIN;
        int tabBarTop = TOOLBAR_HEIGHT;
        int tabBarBottom = TOOLBAR_HEIGHT + TAB_BAR_HEIGHT;
        // 标签栏背景使用不透明深色
        graphics.fill(0, tabBarTop, this.width, tabBarBottom, EditorTheme.BG_ELEVATED);
        graphics.fill(0, tabBarBottom - 1, this.width, tabBarBottom, EditorTheme.BORDER);
        graphics.fill(0, this.height - STATUS_HEIGHT, this.width, this.height, EditorTheme.BG_SURFACE);
        graphics.fill(0, this.height - STATUS_HEIGHT, this.width, this.height - STATUS_HEIGHT + 1, EditorTheme.ACCENT_DIM);
        // 三段式工作区的固定分隔线：导航 / 中央工作区 / 检查器。
        graphics.fill(this.sidebarWidth, contentTop, this.sidebarWidth + 1, contentBottom, EditorTheme.BORDER);
        if (this.propertyPanel != null && this.propertyPanel.visible) {
            int inspectorX = this.propertyPanel.getX();
            if (inspectorX > this.sidebarWidth + 1 && inspectorX < this.width) {
                graphics.fill(inspectorX - 1, contentTop, inspectorX, contentBottom, EditorTheme.BORDER);
            }
        }
        // 状态栏：按 statusLevel 选语义色；非错误消息到时自动清空（错误常驻）
        if (this.statusClearTime != 0L && System.nanoTime() > this.statusClearTime) {
            this.statusText = "";
            this.statusClearTime = 0L;
        }
        int statusColor = switch (this.statusLevel) {
            case SUCCESS -> EditorTheme.STATUS_SUCCESS;
            case WARNING -> EditorTheme.STATUS_WARNING;
            case ERROR -> EditorTheme.STATUS_ERROR;
            default -> EditorTheme.TEXT_SECONDARY;
        };
        graphics.drawString(this.font, this.statusText, 4, this.height - STATUS_HEIGHT + 2, statusColor);
        graphics.enableScissor(TAB_AREA_LEFT, tabBarTop, clipRight, tabBarBottom);
        try {
            for (TabButton btn : this.tabButtons) {
                btn.setActiveTab(btn.index == this.activeSequenceIndex);
                btn.render(graphics, mouseX, mouseY, partialTick);
            }
        } finally {
            graphics.disableScissor();
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        EditorRenderHelper.drawFocusedEditBoxBorders(graphics, this.children());
        // 工具栏按钮 tooltip：悬停时显示功能说明
        this.renderToolbarTooltips(graphics, mouseX, mouseY);
        // F1 帮助浮层：画在所有内容之上，拦截交互由 keyPressed/mouseClicked 处理
        if (this.showHelpOverlay) {
            this.renderHelpOverlay(graphics);
        }
    }

    /**
     * 渲染 F1 帮助浮层：半透明遮罩 + 居中面板，分组列出全部快捷键。
     * 借鉴 Sparkle SecondaryPanel 的遮罩+玻璃面板思路，但内容为静态快捷键说明。
     * 面板尺寸根据内容动态计算，确保不溢出屏幕。
     */
    private void renderHelpOverlay(GuiGraphics graphics) {
        // 全屏半透明遮罩
        graphics.fill(0, 0, this.width, this.height, EditorRenderHelper.withAlphaRatio(EditorTheme.BG_DEEPEST, 0.6f));
        // 快捷键分组定义：{组标题, {快捷键, 说明}}
        String[][][] groups = {
            {{"gui.vn_edit.help.group_file"}, {"Ctrl+S", "gui.vn_edit.help.save"}, {"Ctrl+N", "gui.vn_edit.help.new"}},
            {{"gui.vn_edit.help.group_node"}, {"Insert", "gui.vn_edit.help.add_node"}, {"Delete", "gui.vn_edit.help.delete"}, {"F2", "gui.vn_edit.help.rename"}, {"Ctrl+C", "gui.vn_edit.help.copy"}, {"Ctrl+V", "gui.vn_edit.help.paste"}, {"Ctrl+D", "gui.vn_edit.help.duplicate"}},
            {{"gui.vn_edit.help.group_sequence"}, {"Ctrl+Tab", "gui.vn_edit.help.next_seq"}, {"Ctrl+Shift+Tab", "gui.vn_edit.help.prev_seq"}},
            {{"gui.vn_edit.help.group_test"}, {"Ctrl+Enter", "gui.vn_edit.help.test"}},
            {{"gui.vn_edit.help.group_nav"}, {"\u2191/\u2193", "gui.vn_edit.help.tree_nav"}, {"\u2190/\u2192", "gui.vn_edit.help.tree_fold"}, {"Enter", "gui.vn_edit.help.tree_open"}},
            {{"gui.vn_edit.help.group_help"}, {"F1", "gui.vn_edit.help.toggle"}, {"Esc", "gui.vn_edit.help.close"}}
        };
        // 计算面板尺寸
        int panelPad = 10;
        int keyWidth = 90;
        int descGap = 12;
        int rowHeight = 12;
        int groupGap = 6;
        int titleHeight = 14;
        int panelWidth = 300;
        int contentHeight = titleHeight + 8;
        for (String[][] group : groups) {
            contentHeight += titleHeight + (group.length - 1) * rowHeight + groupGap;
        }
        int panelHeight = contentHeight + panelPad * 2;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;
        // 面板背景 + 边框
        // 第八轮美化：浮层投影（项 5），制造悬浮感
        EditorRenderHelper.fillWithShadow(graphics, panelX, panelY, panelWidth, panelHeight, EditorTheme.BG_ELEVATED, EditorTheme.SHADOW_DROP);
        // ACCENT 边框保留（fillWithShadow 已画 BORDER 边框，这里叠加 ACCENT 外层强调浮层焦点）
        EditorRenderHelper.drawBorder(graphics, panelX, panelY, panelWidth, panelHeight, EditorTheme.ACCENT);
        // 标题
        graphics.drawCenteredString(this.font, Component.translatable("gui.vn_edit.help.title"), panelX + panelWidth / 2, panelY + panelPad, EditorTheme.TEXT_PRIMARY);
        int y = panelY + panelPad + titleHeight + 8;
        int keyX = panelX + panelPad;
        int descX = keyX + keyWidth + descGap;
        for (String[][] group : groups) {
            // 组标题
            graphics.drawString(this.font, Component.translatable(group[0][0]), keyX, y, EditorTheme.ACCENT);
            y += titleHeight;
            // 组内条目
            for (int i = 1; i < group.length; i++) {
                graphics.drawString(this.font, group[i][0], keyX, y, EditorTheme.TEXT_SECONDARY);
                graphics.drawString(this.font, Component.translatable(group[i][1]), descX, y, EditorTheme.TEXT_PRIMARY);
                y += rowHeight;
            }
            y += groupGap;
        }
        // 底部提示
        graphics.drawCenteredString(this.font, Component.translatable("gui.vn_edit.help.footer"), panelX + panelWidth / 2, panelY + panelHeight - panelPad - 4, EditorTheme.TEXT_MUTED);
    }

    /**
     * 为顶部工具栏按钮渲染 tooltip。
     * 按钮顺序与 buildWidgets() 中创建顺序一致：新建/保存/读取/测试/导入/序列属性。
     * 另覆盖添加节点/新增标签/标签滚动箭头/标签页等高频元素，提升可发现性。
     */
    private void renderToolbarTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < this.toolbarButtons.size(); i++) {
            EditorButton btn = this.toolbarButtons.get(i);
            if (btn.isMouseOver(mouseX, mouseY)) {
                Component tip = switch (i) {
                    case 0 -> Component.translatable("gui.vn_edit.tooltip.new");
                    case 1 -> Component.translatable("gui.vn_edit.tooltip.save");
                    case 2 -> Component.translatable("gui.vn_edit.tooltip.load");
                    case 3 -> Component.translatable("gui.vn_edit.tooltip.test");
                    case 4 -> Component.translatable("gui.vn_edit.tooltip.import");
                    case 5 -> Component.translatable("gui.vn_edit.tooltip.sequence_props");
                    case 6 -> Component.translatable("gui.vn_edit.validation.tooltip");
                    default -> null;
                };
                if (tip != null) {
                    graphics.renderTooltip(this.font, tip, mouseX, mouseY);
                }
                return;
            }
        }
        // 添加节点按钮
        if (this.addNodeBtn != null && this.addNodeBtn.isMouseOver(mouseX, mouseY)) {
            graphics.renderTooltip(this.font, Component.translatable("gui.vn_edit.tooltip.add_node"), mouseX, mouseY);
            return;
        }
        // 新增标签按钮（+）
        if (this.addTabBtn != null && this.addTabBtn.isMouseOver(mouseX, mouseY)) {
            graphics.renderTooltip(this.font, Component.translatable("gui.vn_edit.tooltip.add_tab"), mouseX, mouseY);
            return;
        }
        // 标签滚动箭头
        if (this.tabLeftArrow != null && this.tabLeftArrow.isMouseOver(mouseX, mouseY)) {
            graphics.renderTooltip(this.font, Component.translatable("gui.vn_edit.tooltip.tab_left"), mouseX, mouseY);
            return;
        }
        if (this.tabRightArrow != null && this.tabRightArrow.isMouseOver(mouseX, mouseY)) {
            graphics.renderTooltip(this.font, Component.translatable("gui.vn_edit.tooltip.tab_right"), mouseX, mouseY);
            return;
        }
        // 标签页：提示左键切换/双击重命名/右键关闭
        for (TabButton tabBtn : this.tabButtons) {
            if (tabBtn.isMouseOver(mouseX, mouseY)) {
                graphics.renderTooltip(this.font, Component.translatable("gui.vn_edit.tooltip.tab"), mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 帮助浮层展开时，任意点击关闭浮层并消费事件，不穿透到下层控件
        if (this.showHelpOverlay) {
            this.showHelpOverlay = false;
            return true;
        }
        if (this.tabLeftArrow.isMouseOver(mouseX, mouseY)) {
            this.tabLeftArrow.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (this.tabRightArrow.isMouseOver(mouseX, mouseY)) {
            this.tabRightArrow.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        for (TabButton btn : this.tabButtons) {
            if (!btn.isMouseOver(mouseX, mouseY)) {
                continue;
            }
            btn.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // F1 切换帮助浮层：任何情况都拦截（即便 EditBox 聚焦也响应，作为快捷键可发现性入口）
        if (keyCode == GLFW.GLFW_KEY_F1) {
            this.showHelpOverlay = !this.showHelpOverlay;
            return true;
        }
        // 帮助浮层展开时，仅放行 F1（上面已处理）与 Esc，其余按键一律拦截避免误操作
        if (this.showHelpOverlay) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.showHelpOverlay = false;
                return true;
            }
            return true;
        }
        // Ctrl 组合键任何情况都拦截（Ctrl+S/N/Enter/Tab 不用于文本编辑）
        if (Screen.hasControlDown()) {
            if (keyCode == GLFW.GLFW_KEY_S) { this.onSave(); return true; }
            if (keyCode == GLFW.GLFW_KEY_N) { this.onNew(); return true; }
            if (keyCode == GLFW.GLFW_KEY_ENTER) { this.onTest(); return true; }
            if (keyCode == GLFW.GLFW_KEY_TAB) {
                if (this.openSequences.isEmpty()) return true;
                int dir = Screen.hasShiftDown() ? -1 : 1;
                int next = this.activeSequenceIndex + dir;
                if (next < 0) next = this.openSequences.size() - 1;
                if (next >= this.openSequences.size()) next = 0;
                this.switchToSequence(next);
                return true;
            }
        }
        // 功能键仅在 EditBox 未聚焦时拦截，避免与文本编辑冲突
        if (!(this.getFocused() instanceof EditBox)) {
            // Ctrl+C/V/D 复制/粘贴/复制并粘贴节点（EditBox 聚焦时让文本编辑优先）
            if (Screen.hasControlDown()) {
                if (keyCode == GLFW.GLFW_KEY_C) { this.copySelectedNode(); return true; }
                if (keyCode == GLFW.GLFW_KEY_V) { this.pasteNode(); return true; }
                if (keyCode == GLFW.GLFW_KEY_D) { this.duplicateSelectedNode(); return true; }
                // Ctrl+Z/Ctrl+Shift+Z/Ctrl+Y 撤销重做（EditBox 聚焦时不拦截，避免打字误触整图回滚）
                if (keyCode == GLFW.GLFW_KEY_Z) {
                    if (Screen.hasShiftDown()) { this.performRedo(); } else { this.performUndo(); }
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_Y) { this.performRedo(); return true; }
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                DialogEntry sel = this.getActiveSelectedEntry();
                if (sel != null) { this.onEntryDelete(sel); return true; }
            }
            if (keyCode == GLFW.GLFW_KEY_F2) {
                DialogEntry sel = this.getActiveSelectedEntry();
                if (sel != null) { this.startRenameEntry(sel); return true; }
            }
            if (keyCode == GLFW.GLFW_KEY_INSERT) {
                this.onAddNode();
                return true;
            }
            // 树键盘导航：方向键移动选中、左右折叠展开、Enter 打开属性面板
            if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN
                    || keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT
                    || keyCode == GLFW.GLFW_KEY_ENTER) {
                if (this.treeWidget != null && this.treeWidget.keyPressed(keyCode)) {
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Ctrl+C：复制选中节点的深拷贝到静态剪贴板。 */
    private void copySelectedNode() {
        DialogEntry sel = this.getActiveSelectedEntry();
        if (sel == null) {
            this.setStatus(Component.translatable("gui.vn_edit.status.copy_no_selection").getString(), StatusLevel.WARNING);
            return;
        }
        clipboard = sel.deepCopy();
        this.setStatus(Component.translatable("gui.vn_edit.status.node_copied", sel.getId()).getString(), StatusLevel.SUCCESS);
    }

    /** Ctrl+V：粘贴剪贴板节点为新 ID 节点，选中新节点并打开属性面板。 */
    private void pasteNode() {
        if (this.currentSequence == null) {
            this.setStatus(Component.translatable("gui.vn_edit.status.copy_no_selection").getString(), StatusLevel.WARNING);
            return;
        }
        if (clipboard == null) {
            this.setStatus(Component.translatable("gui.vn_edit.status.paste_empty").getString(), StatusLevel.WARNING);
            return;
        }
        DialogEntry copy = clipboard.deepCopy();
        String newId = this.generateUniqueNodeId(clipboard.getId());
        copy.setId(newId);
        DialogEntry[] entries = this.currentSequence.getEntries();
        ArrayList<DialogEntry> list = entries != null ? new ArrayList<>(List.of(entries)) : new ArrayList<>();
        this.pushHistory();
        list.add(copy);
        this.currentSequence.setEntries(list.toArray(new DialogEntry[0]));
        this.treeWidget.setSequence(this.currentSequence);
        this.propertyPanel.setSequence(this.currentSequence);
        this.markDirty(this.currentSequence);
        // selectEntryById 内部会触发 onEntrySelected 回调（设 editingEntry + bindTo）并定位/滚动到可见
        this.treeWidget.selectEntryById(newId);
        this.setStatus(Component.translatable("gui.vn_edit.status.node_pasted", newId).getString(), StatusLevel.SUCCESS);
    }

    /** Ctrl+D：复制并立即粘贴选中节点（一步完成）。 */
    private void duplicateSelectedNode() {
        DialogEntry sel = this.getActiveSelectedEntry();
        if (sel == null) {
            this.setStatus(Component.translatable("gui.vn_edit.status.copy_no_selection").getString(), StatusLevel.WARNING);
            return;
        }
        clipboard = sel.deepCopy();
        this.pasteNode();
    }

    /**
     * 生成不冲突的副本 ID：原 ID → 原 ID_copy → 原 ID_copy2 → 原 ID_copy3 ...
     * 用于粘贴节点时避免 ID 冲突。
     */
    private String generateUniqueNodeId(String baseId) {
        if (baseId == null || baseId.isEmpty()) {
            baseId = "node";
        }
        if (this.currentSequence.findEntryById(baseId) == null) {
            return baseId;
        }
        String prefix = baseId + "_copy";
        if (this.currentSequence.findEntryById(prefix) == null) {
            return prefix;
        }
        int n = 2;
        while (this.currentSequence.findEntryById(prefix + n) != null) {
            n++;
        }
        return prefix + n;
    }

    /**
     * 重命名指定节点（F2 / 节点菜单入口）：弹出 InputDialogScreen，确认后直接改数据模型
     * 并同步更新全部 nextId/option.targetId 引用，随后刷新导航与流程视图。
     * 失败（ID 冲突）时显示状态栏警告。
     */
    private void startRenameEntry(DialogEntry entry) {
        Minecraft.getInstance().setScreen(new InputDialogScreen(
                Component.translatable("gui.vn_edit.rename.title"), entry.getId(), newId -> {
            if (newId.isEmpty() || newId.equals(entry.getId())) {
                return;
            }
            if (!this.renameEntry(entry, newId)) {
                this.setStatus(Component.translatable("gui.vn_edit.rename.failed").getString(), StatusLevel.WARNING);
            } else {
                this.setStatus(Component.translatable("gui.vn_edit.rename.success", newId).getString(), StatusLevel.SUCCESS);
                this.markDirty(this.currentSequence);
            }
        }, this));
    }

    /**
     * 重命名节点并更新引用：不依赖导航视图的选中状态。
     * @return false 表示新 ID 已存在（冲突）
     */
    private boolean renameEntry(DialogEntry entry, String newId) {
        if (this.currentSequence == null || this.currentSequence.findEntryById(newId) != null) {
            return false;
        }
        String oldId = entry.getId();
        this.pushHistory();
        entry.setId(newId);
        for (DialogEntry e : this.currentSequence.getEntries()) {
            if (oldId.equals(e.getNextId())) {
                e.setNextId(newId);
            }
            DialogOption[] options = e.getOptions();
            if (options == null) {
                continue;
            }
            for (DialogOption opt : options) {
                if (oldId.equals(opt.getTargetId())) {
                    opt.setTargetId(newId);
                }
            }
        }
        EditorScreenState.get().setSelectedNodeId(newId);
        this.treeWidget.setSequence(this.currentSequence);
        this.treeWidget.selectEntryById(newId);
        return true;
    }

    @Override
    public void onClose() {
        if (this.hasUnsavedChanges()) {
            // 有未保存的修改时，弹出确认对话框
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new EditorConfirmScreen(
                    Component.translatable("gui.vn_edit.unsaved.title"),
                    Component.translatable("gui.vn_edit.unsaved.message"),
                    confirmed -> {
                        if (confirmed) {
                            this.doSaveAllAndClose();
                        } else {
                            // 丢弃修改直接关闭
                            this.dirtySequences.clear();
                            this.doSaveAllAndClose();
                        }
                    }, this,
                    Component.translatable("gui.vn_edit.save_all"),
                    Component.translatable("gui.vn_edit.discard")));
        } else {
            this.doSaveAllAndClose();
        }
    }

    private void doSaveAllAndClose() {
        int failCount = 0;
        for (DialogSequence seq : this.openSequences) {
            if (seq == null || seq.getId() == null) {
                continue;
            }
            // 只保存有修改的序列，避免覆盖未修改的文件
            if (!this.dirtySequences.contains(seq.getId())) {
                continue;
            }
            String json = PRETTY_GSON.toJson(seq);
            Path path = EditorConfig.DIALOG_JSON_DIR.resolve(seq.getId() + ".json");
            try {
                Files.createDirectories(EditorConfig.DIALOG_JSON_DIR);
                Files.writeString(path, json);
            } catch (IOException e) {
                Dialog.LOGGER.error("Auto-save failed for {}: {}", seq.getId(), e.getMessage());
                failCount++;
            }
        }
        // 批量保存失败统计：仅当有失败时提示（关闭流程仍继续，因用户已确认关闭）
        if (failCount > 0) {
            Dialog.LOGGER.error("doSaveAllAndClose: {} sequence(s) failed to save", failCount);
        }
        this.dirtySequences.clear();
        this.saveSession();
        // 预览纹理由 TextureCacheService 统一缓存管理，编辑器关闭时统一释放避免显存泄漏。
        TextureCacheService.releaseAll();
        // 清空跨屏 UI 状态单例，下次打开编辑器为初始状态（活动标签/选中节点/树滚动归零）。
        EditorScreenState.get().reset();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, EditorTheme.BG_DEEPEST);
    }

    /**
     * 标签页按钮：支持单击切换、双击重命名、右键弹关闭确认（不删文件）。
     * 继承 EditorButton 以统一编辑器风格。
     */
    private static class TabButton extends EditorButton {
        private final int index;
        private final Consumer<Integer> onRightClick;
        private final Consumer<Integer> onDoubleClick;
        private boolean activeTab = false;
        private long lastClickTime = 0L;

        TabButton(int x, int y, int width, int height, Component message, EditorButton.OnPress onPress,
                  int index, Consumer<Integer> onRightClick, Consumer<Integer> onDoubleClick) {
            super(x, y, width, height, message, onPress);
            this.index = index;
            this.onRightClick = onRightClick;
            this.onDoubleClick = onDoubleClick;
        }

        public void setActiveTab(boolean active) {
            this.activeTab = active;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();
            // 活动标签使用面板色，非活动使用高亮色
            int bgColor = this.activeTab ? EditorTheme.BG_SURFACE : (this.isHoveredOrFocused() ? EditorTheme.BG_HOVER : EditorTheme.BG_ELEVATED);
            graphics.fill(x, y, x + w, y + h, bgColor);
            // 活动标签底部强调线
            if (this.activeTab) {
                graphics.fill(x, y + h - 2, x + w, y + h, EditorTheme.ACCENT);
            } else {
                graphics.fill(x, y + h - 1, x + w, y + h, EditorTheme.BORDER);
            }
            // 边框
            int borderColor = this.isHoveredOrFocused() ? EditorTheme.ACCENT : EditorTheme.BORDER;
            graphics.fill(x, y, x + w, y + 1, borderColor);
            graphics.fill(x, y, x + 1, y + h, borderColor);
            graphics.fill(x + w - 1, y, x + w, y + h, borderColor);
            // 文字：标签文字在 rebuildTabButtons 中已预截断，直接居中渲染
            int textColor = this.activeTab ? EditorTheme.TEXT_PRIMARY : (this.isHoveredOrFocused() ? EditorTheme.TEXT_PRIMARY : EditorTheme.TEXT_SECONDARY);
            graphics.drawCenteredString(Minecraft.getInstance().font, this.getMessage(), x + w / 2, y + (h - 8) / 2, textColor);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                long now = System.currentTimeMillis();
                if (now - this.lastClickTime < 500L) {
                    if (this.onDoubleClick != null) {
                        this.onDoubleClick.accept(this.index);
                    }
                    this.lastClickTime = 0L;
                    return true;
                }
                this.lastClickTime = now;
            } else if (button == 1 && this.isMouseOver(mouseX, mouseY)) {
                if (this.onRightClick != null) {
                    this.onRightClick.accept(this.index);
                }
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }
}
