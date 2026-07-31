package top.yourzi.dialog.editor.gui.property;

import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.gui.FileBrowserScreen;
import top.yourzi.dialog.editor.gui.InventoryItemPickerScreen;
import top.yourzi.dialog.editor.gui.NodePickerScreen;
import top.yourzi.dialog.editor.gui.OptionEditScreen;
import top.yourzi.dialog.editor.gui.widget.BooleanOptionRow;
import top.yourzi.dialog.editor.gui.widget.EditorButton;
import top.yourzi.dialog.editor.util.AudioPreviewPlayer;
import top.yourzi.dialog.editor.util.EditorConfig;
import top.yourzi.dialog.editor.util.EditorTheme;
import top.yourzi.dialog.editor.util.PageLayout;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.model.DialogSequence;
import top.yourzi.dialog.model.DisplayItemInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * 逻辑属性页：下一节点、结束/跳过、音频、命令、选项。
 * 使用 PageLayout 游标布局处理静态部分，动态部分（命令/物品/选项）基于游标位置动态重排。
 */
public class LogicPropertyPage extends AbstractPropertyPage {
    private static final int LABEL_WIDTH = EditorTheme.LABEL_WIDTH;
    private static final int OPTION_ROW_HEIGHT = EditorTheme.FIELD_HEIGHT;
    private static final int COMMAND_ROW_HEIGHT = EditorTheme.FIELD_HEIGHT;
    private static final int DYNAMIC_SECTION_OVERHEAD = EditorTheme.SECTION_GAP + EditorTheme.SECTION_HDR_H + EditorTheme.ROW_GAP + EditorTheme.FIELD_HEIGHT + EditorTheme.ROW_GAP;

    private EditorButton nextNodeBtn;
    private Option<Boolean> endDialogOption;
    private Option<Boolean> allowSkipOption;
    private BooleanOptionRow endDialogRow;
    private BooleanOptionRow allowSkipRow;
    private EditorButton addCommandBtn;
    private EditorButton addOptionBtn;
    private EditBox audioPathBox;
    private EditorButton audioBrowseBtn;
    private EditorButton audioPlayBtn;
    private EditorButton audioFolderBtn;
    private EditBox visibilityCommandBox;
    private DialogSequence currentSequence;
    private final List<EditBox> commandEdits = new ArrayList<>();
    private final List<EditorButton> commandDeleteBtns = new ArrayList<>();
    private final List<EditorButton> editOptionButtons = new ArrayList<>();
    private final List<EditorButton> deleteOptionButtons = new ArrayList<>();
    private EditorButton addItemBtn;
    private EditorButton pickFromInventoryBtn;
    private final List<EditBox> itemIdEdits = new ArrayList<>();
    private final List<EditBox> itemCountEdits = new ArrayList<>();
    private final List<EditBox> itemNbtEdits = new ArrayList<>();
    private final List<EditorButton> itemDeleteBtns = new ArrayList<>();
    private int optionListStartY;
    private int commandListStartY;
    private int displayItemsStartY;
    private int dynamicStartY;

    // 渲染位置缓存
    private int flowHeaderY;
    private int flowLabelY;
    private int audioHeaderY;
    private int audioLabelY;
    private int visibilityHeaderY;
    private int visibilityLabelY;

    public LogicPropertyPage(Font font) {
        super(font);
    }

    @Override
    public void init(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;

        PageLayout layout = new PageLayout(x, y, width);
        int fieldX = layout.fieldX();
        int fieldW = layout.fieldWidth();

        // ===== 流程分节 =====
        this.flowHeaderY = layout.section();
        int nextY = layout.fieldRow();
        this.flowLabelY = nextY + 4;
        int nextBtnW = Math.max(60, fieldW - 50);
        this.nextNodeBtn = EditorButton.builder(Component.literal("None"), btn -> this.openNodePicker())
                .bounds(fieldX, nextY, nextBtnW, EditorTheme.FIELD_HEIGHT).build();
        int endY = layout.fieldRow();
        this.endDialogOption = new Option<>(
                () -> this.currentEntry != null && this.currentEntry.isEndDialog(),
                v -> { if (this.currentEntry != null) this.currentEntry.setEndDialog(v); },
                () -> { if (this.dirtyListener != null) this.dirtyListener.run(); });
        this.endDialogRow = new BooleanOptionRow(fieldX, endY, fieldW, EditorTheme.FIELD_HEIGHT,
                Component.translatable("gui.vn_edit.end_dialog"), this.endDialogOption, this.font);
        int skipY = layout.fieldRow();
        this.allowSkipOption = new Option<>(
                () -> this.currentEntry != null && this.currentEntry.isSkipAllowed(),
                v -> { if (this.currentEntry != null) this.currentEntry.setAllowSkip(v); },
                () -> { if (this.dirtyListener != null) this.dirtyListener.run(); });
        this.allowSkipRow = new BooleanOptionRow(fieldX, skipY, fieldW, EditorTheme.FIELD_HEIGHT,
                Component.translatable("gui.vn_edit.allow_skip"), this.allowSkipOption, this.font);

        // ===== 音频分节 =====
        this.audioHeaderY = layout.section();
        int audioY = layout.fieldRow();
        this.audioLabelY = audioY + 4;
        // 计算音频路径框和按钮宽度
        int audioBrowseW = 40;
        int audioPlayW = 40;
        int audioFolderW = 25;
        int audioBtnsTotal = audioBrowseW + audioPlayW + audioFolderW + EditorTheme.GAP * 3;
        int audioBoxW = Math.max(50, fieldW - audioBtnsTotal);
        this.audioPathBox = new EditBox(this.font, fieldX, audioY, audioBoxW, EditorTheme.FIELD_HEIGHT, Component.translatable("gui.vn_edit.audio_path"));
        this.audioPathBox.setMaxLength(999999999);
        this.audioPathBox.setResponder(s -> {
            if (this.currentEntry != null) {
                this.currentEntry.setAudioPath(s.isEmpty() ? null : s);
            }
            if (this.dirtyListener != null) this.dirtyListener.run();
        });
        int audioBtnOffset = fieldX + audioBoxW + EditorTheme.GAP;
        this.audioBrowseBtn = EditorButton.builder(Component.translatable("gui.vn_edit.browse"), btn -> this.onAudioBrowse())
                .bounds(audioBtnOffset, audioY, audioBrowseW, EditorTheme.FIELD_HEIGHT).build();
        audioBtnOffset += audioBrowseW + EditorTheme.GAP;
        this.audioPlayBtn = EditorButton.builder(Component.translatable("gui.vn_edit.play"), btn -> {
            File audioFile;
            String pathStr = this.audioPathBox.getValue();
            if (!pathStr.isEmpty() && (audioFile = EditorConfig.SOUNDS_DIR.resolve(pathStr).toFile()).exists()) {
                AudioPreviewPlayer.play(audioFile);
            }
        }).bounds(audioBtnOffset, audioY, audioPlayW, EditorTheme.FIELD_HEIGHT).build();
        audioBtnOffset += audioPlayW + EditorTheme.GAP;
        this.audioFolderBtn = EditorButton.builder(Component.literal("\uD83D\uDCC2"), btn -> EditorConfig.openFolder(EditorConfig.SOUNDS_DIR))
                .bounds(audioBtnOffset, audioY, audioFolderW, EditorTheme.FIELD_HEIGHT).build();

        // ===== 可见性分节 =====
        this.visibilityHeaderY = layout.section();
        int visY = layout.fieldRow();
        this.visibilityLabelY = visY + 4;
        this.visibilityCommandBox = new EditBox(this.font, fieldX, visY, fieldW, EditorTheme.FIELD_HEIGHT, Component.translatable("gui.vn_edit.visibility_command"));
        this.visibilityCommandBox.setMaxLength(999999999);
        this.visibilityCommandBox.setResponder(s -> {
            if (this.currentEntry != null) {
                this.currentEntry.setVisibilityCommand(s.isEmpty() ? null : s);
            }
            if (this.dirtyListener != null) this.dirtyListener.run();
        });

        // 记录动态分节起始 Y
        this.dynamicStartY = layout.currentY();

        // ===== 动态分节（命令/物品/选项）初始位置 =====
        this.commandListStartY = this.dynamicStartY + DYNAMIC_SECTION_OVERHEAD;
        this.addCommandBtn = EditorButton.builder(Component.translatable("gui.vn_edit.add_command"), btn -> this.onAddCommand())
                .bounds(fieldX, this.commandListStartY - EditorTheme.FIELD_HEIGHT - EditorTheme.ROW_GAP, 60, EditorTheme.FIELD_HEIGHT).build();
        int itemHeaderY = this.commandListStartY + DYNAMIC_SECTION_OVERHEAD;
        // 物品分节头部两个按钮并排：[添加物品] [从背包选择]
        int addItemW = 60;
        int pickBtnW = 90;
        int itemBtnY = itemHeaderY - EditorTheme.FIELD_HEIGHT - EditorTheme.ROW_GAP;
        this.addItemBtn = EditorButton.builder(Component.translatable("gui.vn_edit.add_item"), btn -> this.onAddItem())
                .bounds(fieldX, itemBtnY, addItemW, EditorTheme.FIELD_HEIGHT).build();
        this.pickFromInventoryBtn = EditorButton.builder(Component.translatable("gui.vn_edit.pick_from_inventory"), btn -> this.onPickFromInventory())
                .bounds(fieldX + addItemW + EditorTheme.GAP, itemBtnY, pickBtnW, EditorTheme.FIELD_HEIGHT).build();
        this.displayItemsStartY = itemHeaderY;
        int optionHeaderY = this.displayItemsStartY + DYNAMIC_SECTION_OVERHEAD;
        this.addOptionBtn = EditorButton.builder(Component.translatable("gui.vn_edit.add_option"), btn -> this.onAddOption())
                .bounds(fieldX, optionHeaderY - EditorTheme.FIELD_HEIGHT - EditorTheme.ROW_GAP, 60, EditorTheme.FIELD_HEIGHT).build();
        this.optionListStartY = optionHeaderY;
    }

    @Override
    public void bindTo(DialogEntry entry) {
        this.currentEntry = entry;
        this.refreshDisplay();
    }

    @Override
    public void unbind() {
        this.currentEntry = null;
        this.nextNodeBtn.setMessage(Component.literal("None"));
        // 重置 Option 基线（currentEntry 已置 null，getter 防护返回 false），无需重建控件
        this.endDialogOption.snapshot();
        this.allowSkipOption.snapshot();
        this.audioPathBox.setValue("");
        this.visibilityCommandBox.setValue("");
        this.clearCommandWidgets();
        this.clearItemWidgets();
        this.clearOptionWidgets();
    }

    @Override
    public void refreshDisplay() {
        if (this.currentEntry == null) {
            this.unbind();
            return;
        }
        String nextId = this.currentEntry.getNextId();
        this.nextNodeBtn.setMessage(Component.literal(nextId != null && !nextId.isEmpty() ? nextId : "None"));
        // 重置 Option 基线为 entry 当前值，行控件读 option.get() 自动反映新值，无需重建
        this.endDialogOption.snapshot();
        this.allowSkipOption.snapshot();
        this.setBoxSilent(this.audioPathBox, this.currentEntry.getAudioPath() != null ? this.currentEntry.getAudioPath() : "", s -> {
            if (this.currentEntry != null) {
                this.currentEntry.setAudioPath(s.isEmpty() ? null : s);
            }
        });
        this.setBoxSilent(this.visibilityCommandBox, this.currentEntry.getVisibilityCommand() != null ? this.currentEntry.getVisibilityCommand() : "", s -> {
            if (this.currentEntry != null) {
                this.currentEntry.setVisibilityCommand(s.isEmpty() ? null : s);
            }
        });
        this.relayoutSections();
    }

    private void relayoutSections() {
        if (this.currentEntry == null) {
            return;
        }
        int cmdCount = this.getCommandsList().size();
        int itemCount = this.getItemsList().size();
        int fieldX = this.x + LABEL_WIDTH + EditorTheme.GAP;
        int fieldW = Math.max(40, this.width - LABEL_WIDTH - EditorTheme.GAP * 2);

        // Commands section
        int commandHeaderY = this.dynamicStartY;
        this.commandListStartY = commandHeaderY + EditorTheme.SECTION_HDR_H + EditorTheme.ROW_GAP + EditorTheme.FIELD_HEIGHT + EditorTheme.ROW_GAP;
        this.addCommandBtn.setY(commandHeaderY + EditorTheme.SECTION_HDR_H + EditorTheme.ROW_GAP);

        // Items section starts after commands
        int itemsContentH = cmdCount * (COMMAND_ROW_HEIGHT + EditorTheme.ROW_GAP);
        int itemHeaderY = this.commandListStartY + itemsContentH + EditorTheme.SECTION_GAP;
        this.displayItemsStartY = itemHeaderY + EditorTheme.SECTION_HDR_H + EditorTheme.ROW_GAP + EditorTheme.FIELD_HEIGHT + EditorTheme.ROW_GAP;
        this.addItemBtn.setY(itemHeaderY + EditorTheme.SECTION_HDR_H + EditorTheme.ROW_GAP);
        this.pickFromInventoryBtn.setY(itemHeaderY + EditorTheme.SECTION_HDR_H + EditorTheme.ROW_GAP);

        // Options section starts after items
        int optionsContentH = itemCount * (COMMAND_ROW_HEIGHT + EditorTheme.ROW_GAP);
        int optionHeaderY = this.displayItemsStartY + optionsContentH + EditorTheme.SECTION_GAP;
        this.optionListStartY = optionHeaderY + EditorTheme.SECTION_HDR_H + EditorTheme.ROW_GAP + EditorTheme.FIELD_HEIGHT + EditorTheme.ROW_GAP;
        this.addOptionBtn.setY(optionHeaderY + EditorTheme.SECTION_HDR_H + EditorTheme.ROW_GAP);

        // Rebuild all dynamic widgets at new positions
        this.rebuildCommandWidgets();
        this.rebuildItemWidgets();
        this.rebuildOptionButtons();
    }

    private void onAddCommand() {
        if (this.currentEntry == null) {
            return;
        }
        List<String> cmds = this.getCommandsList();
        cmds.add("");
        this.setCommandsList(cmds);
        this.relayoutSections();
    }

    private void deleteCommand(int index) {
        if (this.currentEntry == null) {
            return;
        }
        List<String> cmds = this.getCommandsList();
        cmds.remove(index);
        this.setCommandsList(cmds);
        this.relayoutSections();
    }

    private void updateCommand(int index, String newValue) {
        if (this.currentEntry == null) {
            return;
        }
        List<String> cmds = this.getCommandsList();
        if (index >= 0 && index < cmds.size()) {
            cmds.set(index, newValue);
            this.setCommandsList(cmds);
        }
    }

    private List<String> getCommandsList() {
        if (this.currentEntry.getCommands() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(this.currentEntry.getCommands());
    }

    private void setCommandsList(List<String> cmds) {
        this.currentEntry.setCommands(cmds.isEmpty() ? null : cmds);
    }

    private void clearCommandWidgets() {
        this.commandEdits.clear();
        this.commandDeleteBtns.clear();
    }

    private void rebuildCommandWidgets() {
        this.clearCommandWidgets();
        if (this.currentEntry == null) {
            return;
        }
        List<String> cmds = this.getCommandsList();
        int fieldX = this.x + LABEL_WIDTH + EditorTheme.GAP;
        int fieldW = Math.max(40, this.width - LABEL_WIDTH - EditorTheme.GAP * 2);
        for (int i = 0; i < cmds.size(); i++) {
            int idx = i;
            int rowY = this.commandListStartY + i * (COMMAND_ROW_HEIGHT + EditorTheme.ROW_GAP);
            int boxW = Math.max(40, fieldW - 30);
            EditBox box = new EditBox(this.font, fieldX, rowY, boxW, EditorTheme.FIELD_HEIGHT, Component.translatable("gui.vn_edit.command"));
            box.setMaxLength(999999999);
            box.setValue(cmds.get(i));
            box.setResponder(s -> { this.updateCommand(idx, s); if (this.dirtyListener != null) this.dirtyListener.run(); });
            this.commandEdits.add(box);
            EditorButton delBtn = EditorButton.builder(Component.literal("X"), btn -> this.deleteCommand(idx))
                    .bounds(fieldX + boxW + EditorTheme.GAP, rowY, 20, EditorTheme.FIELD_HEIGHT).build();
            this.commandDeleteBtns.add(delBtn);
        }
    }

    private void onAddOption() {
        if (this.currentEntry == null) {
            return;
        }
        DialogOption newOpt = DialogOption.builder().text(new JsonPrimitive("New Option")).targetId("").build();
        List<DialogOption> options = this.getOptionsList();
        options.add(newOpt);
        this.setOptionsList(options);
        this.relayoutSections();
    }

    private void deleteOption(DialogOption option) {
        if (this.currentEntry == null) {
            return;
        }
        List<DialogOption> options = this.getOptionsList();
        options.remove(option);
        this.setOptionsList(options);
        this.relayoutSections();
    }

    private void updateOption(DialogOption oldOption, DialogOption editedOption) {
        if (this.currentEntry == null) {
            return;
        }
        List<DialogOption> options = this.getOptionsList();
        int idx = options.indexOf(oldOption);
        if (idx >= 0) {
            options.set(idx, editedOption);
            this.setOptionsList(options);
        }
    }

    private List<DialogOption> getOptionsList() {
        DialogOption[] arr = this.currentEntry.getOptions();
        return arr != null ? new ArrayList<>(Arrays.asList(arr)) : new ArrayList<>();
    }

    private void setOptionsList(List<DialogOption> options) {
        this.currentEntry.setOptions(options.isEmpty() ? null : options.toArray(new DialogOption[0]));
    }

    private void clearOptionWidgets() {
        this.editOptionButtons.clear();
        this.deleteOptionButtons.clear();
    }

    private List<DisplayItemInfo> getItemsList() {
        return this.currentEntry.getDisplayItems() != null ? new ArrayList<>(this.currentEntry.getDisplayItems()) : new ArrayList<>();
    }

    private void setItemsList(List<DisplayItemInfo> items) {
        this.currentEntry.setDisplayItems(items.isEmpty() ? null : items);
    }

    private void onAddItem() {
        if (this.currentEntry == null) {
            return;
        }
        List<DisplayItemInfo> items = this.getItemsList();
        items.add(new DisplayItemInfo("minecraft:stone", 1, ""));
        this.setItemsList(items);
        this.relayoutSections();
    }

    /**
     * 打开玩家物品栏选择屏幕，选择后添加对应物品到列表。
     * 物品 ID 取自注册表（如 minecraft:diamond_sword），数量取自该格物品堆叠数；
     * NBT 字段留空（1.21 已迁移到 DataComponents，旧 NBT 字符串格式不再适用）。
     */
    private void onPickFromInventory() {
        if (this.currentEntry == null) {
            return;
        }
        Minecraft.getInstance().setScreen(new InventoryItemPickerScreen(info -> {
            List<DisplayItemInfo> items = this.getItemsList();
            items.add(info);
            this.setItemsList(items);
            this.relayoutSections();
            // 活动标签由 EditorScreenState 在 PropertyPanel 构造时恢复，无需手动 recover
        }, Minecraft.getInstance().screen));
    }

    private void deleteItem(int index) {
        if (this.currentEntry == null) {
            return;
        }
        List<DisplayItemInfo> items = this.getItemsList();
        if (index >= 0 && index < items.size()) {
            items.remove(index);
            this.setItemsList(items);
            this.relayoutSections();
        }
    }

    private void updateItem(int index, String field, String value) {
        if (this.currentEntry == null) {
            return;
        }
        List<DisplayItemInfo> items = this.getItemsList();
        if (index < 0 || index >= items.size()) {
            return;
        }
        DisplayItemInfo item = items.get(index);
        switch (field) {
            case "id" -> item.setItemId(value);
            case "count" -> {
                try {
                    item.setCount(Math.max(1, Integer.parseInt(value.trim())));
                } catch (NumberFormatException ignored) {
                }
            }
            case "nbt" -> item.setNbt(value.isEmpty() ? null : value);
        }
        this.setItemsList(items);
    }

    private void clearItemWidgets() {
        this.itemIdEdits.clear();
        this.itemCountEdits.clear();
        this.itemNbtEdits.clear();
        this.itemDeleteBtns.clear();
    }

    private void rebuildItemWidgets() {
        this.clearItemWidgets();
        if (this.currentEntry == null) {
            return;
        }
        List<DisplayItemInfo> items = this.getItemsList();
        int fieldX = this.x + LABEL_WIDTH + EditorTheme.GAP;
        int fieldW = Math.max(40, this.width - LABEL_WIDTH - EditorTheme.GAP * 2);
        // 计算各列宽度，确保不溢出
        int delBtnW = 20;
        int nbtBoxW = Math.max(30, fieldW / 3);
        int countBoxW = 30;
        int idBoxW = Math.max(40, fieldW - nbtBoxW - countBoxW - delBtnW - EditorTheme.GAP * 3);
        for (int i = 0; i < items.size(); i++) {
            int idx = i;
            DisplayItemInfo item = items.get(i);
            int rowY = this.displayItemsStartY + i * (COMMAND_ROW_HEIGHT + EditorTheme.ROW_GAP);
            EditBox idBox = new EditBox(this.font, fieldX, rowY, idBoxW, EditorTheme.FIELD_HEIGHT, Component.translatable("gui.vn_edit.item_id"));
            idBox.setMaxLength(999999999);
            idBox.setValue(item.getItemId() != null ? item.getItemId() : "");
            idBox.setResponder(s -> { this.updateItem(idx, "id", s); if (this.dirtyListener != null) this.dirtyListener.run(); });
            this.itemIdEdits.add(idBox);
            int xCursor = fieldX + idBoxW + EditorTheme.GAP;
            EditBox countBox = new EditBox(this.font, xCursor, rowY, countBoxW, EditorTheme.FIELD_HEIGHT, Component.translatable("gui.vn_edit.count"));
            countBox.setMaxLength(5);
            countBox.setValue(String.valueOf(item.getCount()));
            countBox.setResponder(s -> { this.updateItem(idx, "count", s); if (this.dirtyListener != null) this.dirtyListener.run(); });
            this.itemCountEdits.add(countBox);
            xCursor += countBoxW + EditorTheme.GAP;
            EditBox nbtBox = new EditBox(this.font, xCursor, rowY, nbtBoxW, EditorTheme.FIELD_HEIGHT, Component.translatable("gui.vn_edit.nbt"));
            nbtBox.setMaxLength(999999999);
            nbtBox.setValue(item.getNbt() != null ? item.getNbt() : "");
            nbtBox.setResponder(s -> { this.updateItem(idx, "nbt", s); if (this.dirtyListener != null) this.dirtyListener.run(); });
            this.itemNbtEdits.add(nbtBox);
            xCursor += nbtBoxW + EditorTheme.GAP;
            EditorButton delBtn = EditorButton.builder(Component.literal("X"), btn -> this.deleteItem(idx))
                    .bounds(xCursor, rowY, delBtnW, EditorTheme.FIELD_HEIGHT).build();
            this.itemDeleteBtns.add(delBtn);
        }
    }

    private void rebuildOptionButtons() {
        this.clearOptionWidgets();
        if (this.currentEntry == null) {
            return;
        }
        List<DialogOption> options = this.getOptionsList();
        int fieldX = this.x + LABEL_WIDTH + EditorTheme.GAP;
        int fieldW = Math.max(40, this.width - LABEL_WIDTH - EditorTheme.GAP * 2);
        // 按钮宽度按比例分配，确保不溢出
        int editBtnW = Math.min(50, fieldW / 3);
        int deleteBtnW = Math.min(30, fieldW / 5);
        for (int i = 0; i < options.size(); i++) {
            DialogOption opt = options.get(i);
            int btnY = this.optionListStartY + i * (OPTION_ROW_HEIGHT + EditorTheme.ROW_GAP);
            int editX = fieldX + fieldW - editBtnW - deleteBtnW - EditorTheme.GAP;
            int deleteX = editX + editBtnW + EditorTheme.GAP;
            EditorButton editBtn = EditorButton.builder(Component.translatable("gui.vn_edit.edit"), btn -> this.openOptionEditor(opt))
                    .bounds(editX, btnY, editBtnW, EditorTheme.FIELD_HEIGHT).build();
            this.editOptionButtons.add(editBtn);
            EditorButton deleteBtn = EditorButton.builder(Component.translatable("gui.vn_edit.delete"), btn -> this.deleteOption(opt))
                    .bounds(deleteX, btnY, deleteBtnW, EditorTheme.FIELD_HEIGHT).build();
            this.deleteOptionButtons.add(deleteBtn);
        }
    }

    private void openOptionEditor(DialogOption option) {
        Consumer<DialogOption> onSave = edited -> {
            this.updateOption(option, edited);
            this.relayoutSections();
            // 活动标签由 EditorScreenState 在 PropertyPanel 构造时恢复，无需手动 recover
        };
        Minecraft.getInstance().setScreen(new OptionEditScreen(option, onSave, Minecraft.getInstance().screen, this.currentSequence));
    }

    private void onAudioBrowse() {
        FileBrowserScreen.open(EditorConfig.SOUNDS_DIR.toFile(), new String[]{"wav", "ogg"}, path -> {
            this.audioPathBox.setValue(path);
            // 活动标签由 EditorScreenState 在 PropertyPanel 构造时恢复，无需手动 recover
        }, Minecraft.getInstance().screen);
    }

    private void openNodePicker() {
        if (this.currentSequence == null) {
            return;
        }
        Minecraft.getInstance().setScreen(new NodePickerScreen(this.currentSequence, selectedId -> {
            this.nextNodeBtn.setMessage(Component.literal(selectedId.isEmpty() ? "None" : selectedId));
            if (this.currentEntry != null) {
                this.currentEntry.setNextId(selectedId.isEmpty() ? null : selectedId);
            }
            if (this.dirtyListener != null) this.dirtyListener.run();
            // 活动标签由 EditorScreenState 在 PropertyPanel 构造时恢复，无需手动 recover
        }, Minecraft.getInstance().screen));
    }

    @Override
    public void setSequence(DialogSequence sequence) {
        this.currentSequence = sequence;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }
        // 静态分节
        EditorTheme.drawSectionHeader(graphics, this.font, this.x, this.flowHeaderY, this.width, Component.translatable("gui.vn_edit.section.flow"));
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.next_id"), this.x + 5, this.flowLabelY, EditorTheme.TEXT_SECONDARY);
        this.nextNodeBtn.render(graphics, mouseX, mouseY, partialTick);
        this.endDialogRow.render(graphics, mouseX, mouseY, partialTick);
        this.allowSkipRow.render(graphics, mouseX, mouseY, partialTick);

        EditorTheme.drawSectionHeader(graphics, this.font, this.x, this.audioHeaderY, this.width, Component.translatable("gui.vn_edit.section.audio"));
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.audio_path"), this.x + 5, this.audioLabelY, EditorTheme.TEXT_SECONDARY);
        this.audioPathBox.render(graphics, mouseX, mouseY, partialTick);
        this.audioBrowseBtn.render(graphics, mouseX, mouseY, partialTick);
        this.audioPlayBtn.render(graphics, mouseX, mouseY, partialTick);
        this.audioFolderBtn.render(graphics, mouseX, mouseY, partialTick);

        EditorTheme.drawSectionHeader(graphics, this.font, this.x, this.visibilityHeaderY, this.width, Component.translatable("gui.vn_edit.section.visibility"));
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.visibility_command"), this.x + 5, this.visibilityLabelY, EditorTheme.TEXT_SECONDARY);
        this.visibilityCommandBox.render(graphics, mouseX, mouseY, partialTick);

        // 动态分节标题
        int commandHeaderY = this.dynamicStartY;
        EditorTheme.drawSectionHeader(graphics, this.font, this.x, commandHeaderY, this.width, Component.translatable("gui.vn_edit.section.commands"));
        this.addCommandBtn.render(graphics, mouseX, mouseY, partialTick);
        for (EditBox box : this.commandEdits) {
            box.render(graphics, mouseX, mouseY, partialTick);
        }
        for (EditorButton btn : this.commandDeleteBtns) {
            btn.render(graphics, mouseX, mouseY, partialTick);
        }

        int cmdCount = this.currentEntry != null ? this.getCommandsList().size() : 0;
        int itemsContentH = cmdCount * (COMMAND_ROW_HEIGHT + EditorTheme.ROW_GAP);
        int itemHeaderY = this.commandListStartY + itemsContentH + EditorTheme.SECTION_GAP;
        EditorTheme.drawSectionHeader(graphics, this.font, this.x, itemHeaderY, this.width, Component.translatable("gui.vn_edit.section.items"));
        this.addItemBtn.render(graphics, mouseX, mouseY, partialTick);
        this.pickFromInventoryBtn.render(graphics, mouseX, mouseY, partialTick);
        for (EditBox box : this.itemIdEdits) {
            box.render(graphics, mouseX, mouseY, partialTick);
        }
        for (EditBox box : this.itemCountEdits) {
            box.render(graphics, mouseX, mouseY, partialTick);
        }
        for (EditBox box : this.itemNbtEdits) {
            box.render(graphics, mouseX, mouseY, partialTick);
        }
        for (EditorButton btn : this.itemDeleteBtns) {
            btn.render(graphics, mouseX, mouseY, partialTick);
        }

        int itemCount = this.currentEntry != null ? this.getItemsList().size() : 0;
        int optionsContentH = itemCount * (COMMAND_ROW_HEIGHT + EditorTheme.ROW_GAP);
        int optionHeaderY = this.displayItemsStartY + optionsContentH + EditorTheme.SECTION_GAP;
        EditorTheme.drawSectionHeader(graphics, this.font, this.x, optionHeaderY, this.width, Component.translatable("gui.vn_edit.section.options"));
        this.addOptionBtn.render(graphics, mouseX, mouseY, partialTick);
        DialogOption[] opts;
        if (this.currentEntry != null && (opts = this.currentEntry.getOptions()) != null) {
            int fieldX = this.x + LABEL_WIDTH + EditorTheme.GAP;
            int fieldW = Math.max(40, this.width - LABEL_WIDTH - EditorTheme.GAP * 2);
            int editBtnW = Math.min(50, fieldW / 3);
            int deleteBtnW = Math.min(30, fieldW / 5);
            int textMaxW = fieldW - editBtnW - deleteBtnW - EditorTheme.GAP * 2 - 10;
            for (int i = 0; i < opts.length; i++) {
                DialogOption opt = opts[i];
                int yPos = this.optionListStartY + i * (OPTION_ROW_HEIGHT + EditorTheme.ROW_GAP);
                String summary = (opt.getText("") != null ? opt.getText("").getString() : "?") + " \u2192 " + (opt.getTargetId() != null ? opt.getTargetId() : "?");
                // 截断过长文本，防止与按钮重叠
                String truncated = this.font.plainSubstrByWidth(summary, textMaxW);
                graphics.drawString(this.font, truncated, fieldX, yPos + 4, EditorTheme.TEXT_PRIMARY);
            }
        }
        for (EditorButton btn : this.editOptionButtons) {
            btn.render(graphics, mouseX, mouseY, partialTick);
        }
        for (EditorButton btn : this.deleteOptionButtons) {
            btn.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public List<? extends GuiEventListener> children() {
        List<GuiEventListener> list = new ArrayList<>(List.of(this.nextNodeBtn, this.endDialogRow, this.allowSkipRow,
                this.addCommandBtn, this.addOptionBtn, this.addItemBtn, this.pickFromInventoryBtn,
                this.audioPathBox, this.audioBrowseBtn, this.audioPlayBtn,
                this.audioFolderBtn, this.visibilityCommandBox));
        list.addAll(this.commandEdits);
        list.addAll(this.commandDeleteBtns);
        list.addAll(this.itemIdEdits);
        list.addAll(this.itemCountEdits);
        list.addAll(this.itemNbtEdits);
        list.addAll(this.itemDeleteBtns);
        list.addAll(this.editOptionButtons);
        list.addAll(this.deleteOptionButtons);
        return list;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
        this.nextNodeBtn.visible = visible;
        this.endDialogRow.visible = visible;
        this.allowSkipRow.visible = visible;
        this.addCommandBtn.visible = visible;
        this.addOptionBtn.visible = visible;
        this.addItemBtn.visible = visible;
        this.pickFromInventoryBtn.visible = visible;
        this.audioPathBox.setVisible(visible);
        this.audioBrowseBtn.visible = visible;
        this.audioPlayBtn.visible = visible;
        this.audioFolderBtn.visible = visible;
        this.visibilityCommandBox.setVisible(visible);
        this.commandEdits.forEach(b -> b.setVisible(visible));
        this.commandDeleteBtns.forEach(b -> b.visible = visible);
        this.itemIdEdits.forEach(b -> b.setVisible(visible));
        this.itemCountEdits.forEach(b -> b.setVisible(visible));
        this.itemNbtEdits.forEach(b -> b.setVisible(visible));
        this.itemDeleteBtns.forEach(b -> b.visible = visible);
        this.editOptionButtons.forEach(b -> b.visible = visible);
        this.deleteOptionButtons.forEach(b -> b.visible = visible);
    }

    @Override
    public int getContentHeight() {
        int cmdCount = this.currentEntry != null ? this.getCommandsList().size() : 0;
        int itemCount = this.currentEntry != null ? this.getItemsList().size() : 0;
        int optCount = this.currentEntry != null ? (this.currentEntry.getOptions() != null ? this.currentEntry.getOptions().length : 0) : 0;
        // 静态部分 + 命令分节 + 物品分节 + 选项分节
        int staticH = this.dynamicStartY - this.y;
        int cmdH = DYNAMIC_SECTION_OVERHEAD + cmdCount * (COMMAND_ROW_HEIGHT + EditorTheme.ROW_GAP);
        int itemH = DYNAMIC_SECTION_OVERHEAD + itemCount * (COMMAND_ROW_HEIGHT + EditorTheme.ROW_GAP);
        int optH = DYNAMIC_SECTION_OVERHEAD + optCount * (OPTION_ROW_HEIGHT + EditorTheme.ROW_GAP);
        return staticH + cmdH + itemH + optH + EditorTheme.PADDING;
    }

    /**
     * 序列保存成功后重置字段 dirty 基线：Option.snapshot 把基线设为当前值，清除 dirty。
     * 保存失败时不调用（由主屏 onSave 在 markClean 后触发），保留字段 dirty 视觉与序列 dirty。
     */
    @Override
    public void onSequenceSaved() {
        if (this.endDialogOption != null) {
            this.endDialogOption.snapshot();
        }
        if (this.allowSkipOption != null) {
            this.allowSkipOption.snapshot();
        }
    }
}
