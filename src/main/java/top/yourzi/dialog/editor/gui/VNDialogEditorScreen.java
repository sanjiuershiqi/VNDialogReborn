package top.yourzi.dialog.editor.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.DialogManager;
import top.yourzi.dialog.editor.gui.property.AppearancePropertyPage;
import top.yourzi.dialog.editor.gui.widget.DialogTreeWidget;
import top.yourzi.dialog.editor.gui.widget.PropertyPanel;
import top.yourzi.dialog.editor.util.EditorConfig;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.model.DialogSequence;
import top.yourzi.dialog.network.NetworkHandler;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * VNDialog 可视化编辑器主屏幕。融合自 visual_mod_edit_vndialog，并适配 NeoForge 1.21.1。
 * 该屏幕集成了对话树、属性面板、标签页管理、文件保存/读取/测试/导入等功能。
 */
public class VNDialogEditorScreen extends Screen {
    private static final int TOOLBAR_HEIGHT = 20;
    private static final int TAB_BAR_HEIGHT = 18;
    private static final int STATUS_HEIGHT = 12;
    private static final int TREE_WIDTH = 150;
    private static final int TAB_AREA_LEFT = 2;
    private static final int TAB_AREA_RIGHT_MARGIN = 56;
    private static final int MAX_TAB_WIDTH = 100;
    private static final Path SESSION_FILE = EditorConfig.CONFIG_ROOT.resolve("editor_sessions.json");
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<DialogSequence> openSequences = new ArrayList<>();
    private int activeSequenceIndex = -1;
    private DialogTreeWidget treeWidget;
    private PropertyPanel propertyPanel;
    private final List<TabButton> tabButtons = new ArrayList<>();
    private Button addTabBtn;
    private Button tabLeftArrow;
    private Button tabRightArrow;
    private int tabScrollOffset = 0;
    private DialogSequence currentSequence;
    private DialogEntry editingEntry;
    public String statusText = "";
    private boolean isInitialized = false;
    private int pendingTabIndex = 0;

    public VNDialogEditorScreen() {
        super(Component.translatable("gui.vn_edit.title"));
    }

    public void setPropertyPanelTab(int index) {
        if (this.propertyPanel != null) {
            this.propertyPanel.setActiveTab(index);
        }
        this.pendingTabIndex = index;
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
            if (this.currentSequence.getAllowClose() == null) {
                this.currentSequence.setAllowClose(true);
            }
            this.treeWidget.setSequence(this.currentSequence);
            this.propertyPanel.setSequence(this.currentSequence);
            if (this.editingEntry != null) {
                this.propertyPanel.bindTo(this.editingEntry);
            } else {
                this.propertyPanel.unbind();
            }
        }
        this.propertyPanel.setActiveTab(this.pendingTabIndex);
        this.propertyPanel.setVisible(this.editingEntry != null);
        this.rebuildTabButtons();
    }

    private void buildWidgets() {
        int btnY = 2;
        int btnWidth = 48;
        int btnHeight = 16;
        int btnX = 2;
        Button newBtn = Button.builder(Component.translatable("gui.vn_edit.new"), b -> this.onNew())
                .bounds(btnX, btnY, btnWidth, btnHeight).build();
        Button saveBtn = Button.builder(Component.translatable("gui.vn_edit.save"), b -> this.onSave())
                .bounds(btnX += btnWidth + 2, btnY, btnWidth, btnHeight).build();
        Button loadBtn = Button.builder(Component.translatable("gui.vn_edit.load"), b -> this.onLoad())
                .bounds(btnX += btnWidth + 2, btnY, btnWidth, btnHeight).build();
        Button testBtn = Button.builder(Component.translatable("gui.vn_edit.test"), b -> this.onTest())
                .bounds(btnX += btnWidth + 2, btnY, btnWidth, btnHeight).build();
        Button importBtn = Button.builder(Component.translatable("gui.vn_edit.import"), b -> this.onImport())
                .bounds(btnX += btnWidth + 2, btnY, btnWidth, btnHeight).build();
        this.addRenderableWidget(newBtn);
        this.addRenderableWidget(saveBtn);
        this.addRenderableWidget(loadBtn);
        this.addRenderableWidget(testBtn);
        this.addRenderableWidget(importBtn);
        this.tabLeftArrow = Button.builder(Component.literal("\u25c0"), b -> this.scrollTabs(-80))
                .bounds(0, 0, 12, 16).build();
        this.tabRightArrow = Button.builder(Component.literal("\u25b6"), b -> this.scrollTabs(80))
                .bounds(0, 0, 12, 16).build();
        this.addTabBtn = Button.builder(Component.literal("+"), b -> this.onNew())
                .bounds(0, 0, 16, 16).build();
        this.addRenderableWidget(this.tabLeftArrow);
        this.addRenderableWidget(this.tabRightArrow);
        this.addRenderableWidget(this.addTabBtn);
        int treeY = 38;
        Button addNodeBtn = Button.builder(Component.translatable("gui.vn_edit.add_node"), b -> this.onAddNode())
                .bounds(0, treeY, TREE_WIDTH, 16).build();
        this.addRenderableWidget(addNodeBtn);
        int treeContentY = treeY + 16;
        int contentHeight = this.height - treeContentY - STATUS_HEIGHT;
        this.treeWidget = new DialogTreeWidget(0, treeContentY, TREE_WIDTH, contentHeight, this.font);
        this.treeWidget.setCallbacks(this::onEntrySelected, this::onEntryDelete, this::onEntryAddChild);
        this.addRenderableWidget(this.treeWidget);
        int propX = TREE_WIDTH + 1;
        int propWidth = this.width - propX;
        this.propertyPanel = new PropertyPanel(propX, treeContentY, propWidth, contentHeight, this.font);
        this.propertyPanel.setOnTabChangeListener(index -> this.pendingTabIndex = index);
        this.addRenderableWidget(this.propertyPanel);
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
        int tabY = 21;
        for (int i = 0; i < this.openSequences.size(); i++) {
            DialogSequence seq = this.openSequences.get(i);
            String title = seq.getId() != null ? seq.getId() : "untitled";
            int rawWidth = Math.max(40, this.font.width(title) + 10);
            int width = Math.min(rawWidth, MAX_TAB_WIDTH);
            String displayTitle = title;
            if (rawWidth > MAX_TAB_WIDTH) {
                displayTitle = this.font.plainSubstrByWidth(title, 90) + "...";
            }
            int index = i;
            TabButton tabBtn = new TabButton(tabX, tabY, width, 16, Component.literal(displayTitle),
                    b -> this.switchToSequence(index), index, this::onCloseTab, this::onRenameTab);
            this.tabButtons.add(tabBtn);
            tabX += width + 2;
        }
        int addBtnX = this.width - TAB_AREA_RIGHT_MARGIN + 20;
        this.addTabBtn.setX(addBtnX);
        this.addTabBtn.setY(tabY);
        int arrowY = 2;
        this.tabLeftArrow.setX(addBtnX - 14);
        this.tabLeftArrow.setY(arrowY);
        this.tabRightArrow.setX(addBtnX + 18);
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
        Minecraft.getInstance().setScreen(new RenameStringDialogScreen(seq.getId(), newId -> {
            if (!newId.isEmpty() && !newId.equals(seq.getId())) {
                String oldId = seq.getId();
                seq.setId(newId);
                Path oldFile = EditorConfig.DIALOG_JSON_DIR.resolve(oldId + ".json");
                Path newFile = EditorConfig.DIALOG_JSON_DIR.resolve(newId + ".json");
                try {
                    if (Files.exists(oldFile)) {
                        Files.move(oldFile, newFile);
                    }
                } catch (IOException e) {
                    Dialog.LOGGER.error("Failed to rename dialog file", e);
                    this.statusText = Component.translatable("gui.vn_edit.rename.failed").getString();
                }
                if (this.activeSequenceIndex == index) {
                    this.currentSequence = seq;
                }
                this.saveSession();
                this.rebuildTabButtons();
                this.statusText = Component.translatable("gui.vn_edit.rename.success", newId).getString();
            }
        }, this));
    }

    private void switchToSequence(int index) {
        if (index < 0 || index >= this.openSequences.size()) {
            return;
        }
        this.activeSequenceIndex = index;
        this.currentSequence = this.openSequences.get(index);
        if (this.currentSequence.getAllowClose() == null) {
            this.currentSequence.setAllowClose(true);
        }
        this.treeWidget.setSequence(this.currentSequence);
        this.propertyPanel.setSequence(this.currentSequence);
        this.editingEntry = null;
        this.propertyPanel.unbind();
        this.propertyPanel.setVisible(false);
        this.statusText = Component.translatable("gui.vn_edit.status.switched", this.currentSequence.getId()).getString();
    }

    private void onNew() {
        Minecraft.getInstance().setScreen(new InputDialogScreen(Component.translatable("gui.vn_edit.new_dialog.title"), "new_dialog", id -> {
            DialogSequence seq = new DialogSequence();
            seq.setId(id);
            seq.setEntries(new DialogEntry[0]);
            seq.setAllowClose(true);
            this.openSequences.add(seq);
            this.activeSequenceIndex = this.openSequences.size() - 1;
            this.currentSequence = seq;
            this.treeWidget.setSequence(this.currentSequence);
            this.propertyPanel.setSequence(this.currentSequence);
            this.editingEntry = null;
            this.propertyPanel.unbind();
            this.propertyPanel.setVisible(false);
            this.rebuildTabButtons();
            this.statusText = Component.translatable("gui.vn_edit.status.new").getString();
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
                this.statusText = Component.translatable("gui.vn_edit.status.id_exists", newId).getString();
                return;
            }
            DialogEntry newEntry = DialogEntry.builder().id(newId).text(new JsonPrimitive("")).build();
            DialogEntry[] entries = this.currentSequence.getEntries();
            ArrayList<DialogEntry> list = entries != null ? new ArrayList<>(List.of(entries)) : new ArrayList<>();
            list.add(newEntry);
            this.currentSequence.setEntries(list.toArray(new DialogEntry[0]));
            this.treeWidget.setSequence(this.currentSequence);
            this.propertyPanel.setSequence(this.currentSequence);
            this.editingEntry = newEntry;
            this.propertyPanel.bindTo(this.editingEntry);
            this.propertyPanel.setVisible(true);
            this.statusText = Component.translatable("gui.vn_edit.status.node_added", newId).getString();
        }, this));
    }

    private void onSave() {
        if (this.currentSequence == null) {
            return;
        }
        this.currentSequence.setAllowClose(true);
        this.saveCurrentSequenceToFile();
        this.saveSession();
        this.statusText = Component.translatable("gui.vn_edit.status.saved", this.currentSequence.getId()).getString();
        if (Minecraft.getInstance().player != null) {
            NetworkHandler.sendExecuteCommandToServer("dialog reload");
        }
    }

    private void saveCurrentSequenceToFile() {
        if (this.currentSequence == null) {
            return;
        }
        String id = this.currentSequence.getId();
        if (id == null || id.isEmpty()) {
            id = "untitled";
        }
        String json = PRETTY_GSON.toJson(this.currentSequence);
        Path path = EditorConfig.DIALOG_JSON_DIR.resolve(id + ".json");
        try {
            Files.createDirectories(EditorConfig.DIALOG_JSON_DIR);
            Files.writeString(path, json);
        } catch (IOException e) {
            Dialog.LOGGER.error("Failed to save dialog {}: {}", id, e.getMessage());
        }
    }

    private void onLoad() {
        FileBrowserScreen.open(EditorConfig.DIALOG_JSON_DIR.toFile(), new String[]{"json"}, path -> {
            try {
                String json = Files.readString(EditorConfig.DIALOG_JSON_DIR.resolve(path));
                DialogSequence seq = DialogManager.GSON.fromJson(json, DialogSequence.class);
                seq.setAllowClose(true);
                this.openSequences.add(seq);
                this.activeSequenceIndex = this.openSequences.size() - 1;
                this.currentSequence = seq;
                this.treeWidget.setSequence(this.currentSequence);
                this.propertyPanel.setSequence(this.currentSequence);
                this.editingEntry = null;
                this.propertyPanel.unbind();
                this.propertyPanel.setVisible(false);
                this.rebuildTabButtons();
                this.statusText = Component.translatable("gui.vn_edit.status.loaded", seq.getId()).getString();
            } catch (Exception e) {
                Dialog.LOGGER.error("Failed to load dialog", e);
                this.statusText = Component.translatable("gui.vn_edit.status.load_failed").getString();
            }
        }, this);
    }

    private void onTest() {
        if (this.currentSequence == null) {
            return;
        }
        this.saveCurrentSequenceToFile();
        this.saveSession();
        String json = DialogManager.GSON.toJson(this.currentSequence);
        DialogManager.getInstance().receiveAndShowPlayerSpecificDialog(this.currentSequence.getId(), json);
    }

    private void onImport() {
        Minecraft.getInstance().setScreen(new DialogImportScreen(this, fileName -> {
            if (fileName == null || fileName.isEmpty()) {
                this.statusText = Component.translatable("gui.vn_edit.import.failed").getString();
            } else {
                Path importedPath = EditorConfig.DIALOG_JSON_DIR.resolve(fileName);
                this.loadImportedDialog(importedPath);
            }
        }));
    }

    private void loadImportedDialog(Path dialogFile) {
        try {
            String json = Files.readString(dialogFile);
            DialogSequence seq = DialogManager.GSON.fromJson(json, DialogSequence.class);
            if (seq != null && seq.getId() != null) {
                seq.setAllowClose(true);
                this.openSequences.add(seq);
                this.activeSequenceIndex = this.openSequences.size() - 1;
                this.currentSequence = seq;
                this.treeWidget.setSequence(this.currentSequence);
                this.propertyPanel.setSequence(this.currentSequence);
                this.editingEntry = null;
                this.propertyPanel.unbind();
                this.propertyPanel.setVisible(false);
                this.rebuildTabButtons();
                this.statusText = Component.translatable("gui.vn_edit.import.success", seq.getId()).getString();
            } else {
                this.statusText = Component.translatable("gui.vn_edit.import.invalid_format").getString();
            }
        } catch (Exception e) {
            Dialog.LOGGER.error("Import failed", e);
            this.statusText = Component.translatable("gui.vn_edit.import.failed").getString();
        }
    }

    private void onEntrySelected(DialogEntry entry) {
        this.editingEntry = entry;
        if (this.propertyPanel != null) {
            if (entry != null) {
                this.propertyPanel.bindTo(entry);
                this.propertyPanel.setVisible(true);
            } else {
                this.propertyPanel.unbind();
                this.propertyPanel.setVisible(false);
            }
        }
    }

    private void onEntryDelete(DialogEntry entry) {
        if (this.currentSequence == null || entry == null) {
            return;
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
                if (confirmed) {
                    this.performDeleteEntry(entry);
                }
                this.minecraft.setScreen(this);
            }, Component.translatable("gui.vn_edit.delete_confirm.title"),
                    Component.translatable("gui.vn_edit.delete_confirm.message", entry.getId())));
        }
    }

    private void performDeleteEntry(DialogEntry entry) {
        DialogEntry[] entries = this.currentSequence.getEntries();
        ArrayList<DialogEntry> list = new ArrayList<>(List.of(entries));
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
        this.statusText = Component.translatable("gui.vn_edit.status.node_deleted", entry.getId()).getString();
    }

    private void onEntryAddChild(DialogEntry parentEntry) {
        // 当前未实现：原模组也为空实现，保留以备未来扩展。
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
                Path dialogFile = EditorConfig.DIALOG_JSON_DIR.resolve(id + ".json");
                if (!Files.exists(dialogFile)) {
                    continue;
                }
                String dialogJson = Files.readString(dialogFile);
                DialogSequence seq = DialogManager.GSON.fromJson(dialogJson, DialogSequence.class);
                if (seq == null) {
                    continue;
                }
                seq.setAllowClose(true);
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
                            if (seq != null && seq.getId() != null) {
                                seq.setAllowClose(true);
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

    private void onCloseTab(int index) {
        if (index < 0 || index >= this.openSequences.size()) {
            return;
        }
        DialogSequence seq = this.openSequences.get(index);
        if (this.minecraft != null) {
            this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
                if (confirmed) {
                    Path file = EditorConfig.DIALOG_JSON_DIR.resolve(seq.getId() + ".json");
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException e) {
                        Dialog.LOGGER.error("Failed to delete file: {}", file);
                    }
                    this.openSequences.remove(index);
                    if (this.openSequences.isEmpty()) {
                        this.activeSequenceIndex = -1;
                        this.currentSequence = null;
                        this.treeWidget.setSequence(null);
                        this.propertyPanel.unbind();
                        this.editingEntry = null;
                        this.propertyPanel.setVisible(false);
                    } else if (this.activeSequenceIndex >= index) {
                        this.activeSequenceIndex = Math.min(this.activeSequenceIndex, this.openSequences.size() - 1);
                        this.switchToSequence(this.activeSequenceIndex);
                    } else {
                        this.rebuildTabButtons();
                    }
                }
                this.minecraft.setScreen(this);
            }, Component.translatable("gui.vn_edit.delete_dialog.title"),
                    Component.translatable("gui.vn_edit.delete_dialog.message", seq.getId())));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        int clipRight = this.width - TAB_AREA_RIGHT_MARGIN;
        // 标签栏（切换分组）背景使用不透明深色，避免半透明叠加导致按钮文字模糊看不清
        graphics.fill(0, 20, this.width, 38, 0xFF2B2B2B);
        graphics.fill(0, this.height - STATUS_HEIGHT, this.width, this.height, -872415232);
        graphics.drawString(this.font, this.statusText, 4, this.height - STATUS_HEIGHT + 2, 0xCCCCCC);
        graphics.enableScissor(TAB_AREA_LEFT, 20, clipRight, 38);
        try {
            for (TabButton btn : this.tabButtons) {
                btn.render(graphics, mouseX, mouseY, partialTick);
                // 激活标签的高亮绘制在按钮之后，作为底部亮线，确保不被按钮自身背景遮挡
                if (btn.index == this.activeSequenceIndex) {
                    graphics.fill(btn.getX(), btn.getY() + btn.getHeight() - 2, btn.getX() + btn.getWidth(), btn.getY() + btn.getHeight(), 0xFFFFFFFF);
                }
            }
        } finally {
            graphics.disableScissor();
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
        if (keyCode == 83 && Screen.hasControlDown()) {
            this.onSave();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        for (DialogSequence seq : this.openSequences) {
            if (seq == null || seq.getId() == null) {
                continue;
            }
            String json = PRETTY_GSON.toJson(seq);
            Path path = EditorConfig.DIALOG_JSON_DIR.resolve(seq.getId() + ".json");
            try {
                Files.createDirectories(EditorConfig.DIALOG_JSON_DIR);
                Files.writeString(path, json);
            } catch (IOException e) {
                Dialog.LOGGER.error("Auto-save failed for {}: {}", seq.getId(), e.getMessage());
            }
        }
        this.saveSession();
        AppearancePropertyPage.releaseTextures();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * 标签页按钮：支持单击切换、双击重命名、右键关闭。
     */
    private static class TabButton extends Button {
        private final int index;
        private final Consumer<Integer> onRightClick;
        private final Consumer<Integer> onDoubleClick;
        private long lastClickTime = 0L;

        TabButton(int x, int y, int width, int height, Component message, Button.OnPress onPress,
                  int index, Consumer<Integer> onRightClick, Consumer<Integer> onDoubleClick) {
            super(x, y, width, height, message, onPress, Button.DEFAULT_NARRATION);
            this.index = index;
            this.onRightClick = onRightClick;
            this.onDoubleClick = onDoubleClick;
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
