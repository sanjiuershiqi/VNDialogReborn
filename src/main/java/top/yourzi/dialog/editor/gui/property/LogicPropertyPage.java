package top.yourzi.dialog.editor.gui.property;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.gui.FileBrowserScreen;
import top.yourzi.dialog.editor.gui.NodePickerScreen;
import top.yourzi.dialog.editor.gui.OptionEditScreen;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.editor.gui.VNDialogEditorScreen;
import top.yourzi.dialog.editor.util.AudioPreviewPlayer;
import top.yourzi.dialog.editor.util.EditorConfig;
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
 * 逻辑属性页：下一节点、结束/跳过、音频、命令、选项。融合自 visual_mod_edit_vndialog。
 */
public class LogicPropertyPage implements PropertyPage {
    private static final int LABEL_WIDTH = 60;
    private static final int OPTION_ROW_HEIGHT = 16;
    private static final int COMMAND_ROW_HEIGHT = 16;

    private final Font font;
    private Button nextNodeBtn;
    private Checkbox endDialogCheck;
    private Checkbox allowSkipCheck;
    private Button addCommandBtn;
    private Button addOptionBtn;
    private EditBox audioPathBox;
    private Button audioBrowseBtn;
    private Button audioPlayBtn;
    private Button audioFolderBtn;
    private EditBox visibilityCommandBox;
    private DialogSequence currentSequence;
    private DialogEntry currentEntry;
    private final List<EditBox> commandEdits = new ArrayList<>();
    private final List<Button> commandDeleteBtns = new ArrayList<>();
    private final List<Button> editOptionButtons = new ArrayList<>();
    private final List<Button> deleteOptionButtons = new ArrayList<>();
    private Button addItemBtn;
    private final List<EditBox> itemIdEdits = new ArrayList<>();
    private final List<EditBox> itemCountEdits = new ArrayList<>();
    private final List<EditBox> itemNbtEdits = new ArrayList<>();
    private final List<Button> itemDeleteBtns = new ArrayList<>();
    private int optionListStartY;
    private int commandListStartY;
    private int displayItemsStartY;
    private boolean visible = true;
    private int x;
    private int y;
    private int width;
    private int height;

    public LogicPropertyPage(Font font) {
        this.font = font;
    }

    @Override
    public void init(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        int fieldX = x + LABEL_WIDTH + 5;
        int fieldWidth = width - LABEL_WIDTH - 15;
        this.nextNodeBtn = Button.builder(Component.literal("None"), btn -> this.openNodePicker())
                .bounds(fieldX, y + 5, fieldWidth - 50, 16).build();
        this.endDialogCheck = Checkbox.builder(Component.translatable("gui.vn_edit.end_dialog"), this.font)
                .pos(fieldX, y + 25)
                .maxWidth(fieldWidth)
                .selected(false)
                .onValueChange((checkbox, value) -> {
                    if (this.currentEntry != null) {
                        this.currentEntry.setEndDialog(value);
                    }
                })
                .build();
        this.allowSkipCheck = Checkbox.builder(Component.translatable("gui.vn_edit.allow_skip"), this.font)
                .pos(fieldX, y + 45)
                .maxWidth(fieldWidth)
                .selected(true)
                .onValueChange((checkbox, value) -> {
                    if (this.currentEntry != null) {
                        this.currentEntry.setAllowSkip(value);
                    }
                })
                .build();
        int audioY = y + 70;
        this.audioPathBox = new EditBox(this.font, fieldX, audioY, fieldWidth - 125, 16, Component.translatable("gui.vn_edit.audio_path"));
        this.audioPathBox.setMaxLength(999999999);
        this.audioPathBox.setResponder(s -> {
            if (this.currentEntry != null) {
                this.currentEntry.setAudioPath(s.isEmpty() ? null : s);
            }
        });
        this.audioBrowseBtn = Button.builder(Component.translatable("gui.vn_edit.browse"), btn -> this.onAudioBrowse())
                .bounds(fieldX + fieldWidth - 120, audioY, 40, 16).build();
        this.audioPlayBtn = Button.builder(Component.translatable("gui.vn_edit.play"), btn -> {
            File audioFile;
            String pathStr = this.audioPathBox.getValue();
            if (!pathStr.isEmpty() && (audioFile = EditorConfig.SOUNDS_DIR.resolve(pathStr).toFile()).exists()) {
                AudioPreviewPlayer.play(audioFile);
            }
        }).bounds(fieldX + fieldWidth - 75, audioY, 40, 16).build();
        this.audioFolderBtn = Button.builder(Component.literal("\uD83D\uDCC2"), btn -> EditorConfig.openFolder(EditorConfig.SOUNDS_DIR))
                .bounds(fieldX + fieldWidth - 30, audioY, 25, 16).build();
        int visY = audioY + 22;
        this.visibilityCommandBox = new EditBox(this.font, fieldX, visY, fieldWidth, 16, Component.translatable("gui.vn_edit.visibility_command"));
        this.visibilityCommandBox.setMaxLength(999999999);
        this.visibilityCommandBox.setResponder(s -> {
            if (this.currentEntry != null) {
                this.currentEntry.setVisibilityCommand(s.isEmpty() ? null : s);
            }
        });
        int commandHeaderY = visY + 22;
        this.addCommandBtn = Button.builder(Component.translatable("gui.vn_edit.add_command"), btn -> this.onAddCommand())
                .bounds(fieldX, commandHeaderY, 60, 16).build();
        this.commandListStartY = commandHeaderY + 16 + 4;
        int itemHeaderY = commandHeaderY + 30;
        this.addItemBtn = Button.builder(Component.translatable("gui.vn_edit.add_item"), btn -> this.onAddItem())
                .bounds(fieldX, itemHeaderY, 60, 16).build();
        this.displayItemsStartY = itemHeaderY + 16 + 4;
        int optionHeaderY = itemHeaderY + 30;
        this.addOptionBtn = Button.builder(Component.translatable("gui.vn_edit.add_option"), btn -> this.onAddOption())
                .bounds(fieldX, optionHeaderY, 60, 16).build();
        this.optionListStartY = optionHeaderY + 20;
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
        setCheckboxSelectedSilent(this.endDialogCheck, false);
        setCheckboxSelectedSilent(this.allowSkipCheck, true);
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
        setCheckboxSelectedSilent(this.endDialogCheck, this.currentEntry.isEndDialog());
        setCheckboxSelectedSilent(this.allowSkipCheck, this.currentEntry.isSkipAllowed());
        this.audioPathBox.setResponder(null);
        this.audioPathBox.setValue(this.currentEntry.getAudioPath() != null ? this.currentEntry.getAudioPath() : "");
        this.audioPathBox.setResponder(s -> {
            if (this.currentEntry != null) {
                this.currentEntry.setAudioPath(s.isEmpty() ? null : s);
            }
        });
        this.visibilityCommandBox.setResponder(null);
        this.visibilityCommandBox.setValue(this.currentEntry.getVisibilityCommand() != null ? this.currentEntry.getVisibilityCommand() : "");
        this.visibilityCommandBox.setResponder(s -> {
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

        // Commands section
        this.addCommandBtn.setY(this.commandListStartY - 20);

        // Items section starts after commands
        int itemHeaderY = this.commandListStartY + cmdCount * COMMAND_ROW_HEIGHT + 8;
        this.addItemBtn.setY(itemHeaderY);
        this.displayItemsStartY = itemHeaderY + 20;

        // Options section starts after items
        int optionHeaderY = this.displayItemsStartY + itemCount * COMMAND_ROW_HEIGHT + 8;
        this.addOptionBtn.setY(optionHeaderY);
        this.optionListStartY = optionHeaderY + 20;

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
        for (int i = 0; i < cmds.size(); i++) {
            int idx = i;
            int rowY = this.commandListStartY + i * COMMAND_ROW_HEIGHT;
            EditBox box = new EditBox(this.font, this.x + LABEL_WIDTH + 5, rowY, this.width - LABEL_WIDTH - 50, 16, Component.translatable("gui.vn_edit.command"));
            box.setMaxLength(999999999);
            box.setValue(cmds.get(i));
            box.setResponder(s -> this.updateCommand(idx, s));
            this.commandEdits.add(box);
            Button delBtn = Button.builder(Component.literal("X"), btn -> this.deleteCommand(idx))
                    .bounds(this.x + LABEL_WIDTH + 5 + this.width - LABEL_WIDTH - 45, rowY, 20, 16).build();
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
        int fieldX = this.x + LABEL_WIDTH + 5;
        for (int i = 0; i < items.size(); i++) {
            int idx = i;
            DisplayItemInfo item = items.get(i);
            int rowY = this.displayItemsStartY + i * COMMAND_ROW_HEIGHT;
            EditBox idBox = new EditBox(this.font, fieldX, rowY, this.width - LABEL_WIDTH - 145, 16, Component.translatable("gui.vn_edit.item_id"));
            idBox.setMaxLength(999999999);
            idBox.setValue(item.getItemId() != null ? item.getItemId() : "");
            idBox.setResponder(s -> this.updateItem(idx, "id", s));
            this.itemIdEdits.add(idBox);
            EditBox countBox = new EditBox(this.font, fieldX + this.width - LABEL_WIDTH - 140, rowY, 30, 16, Component.translatable("gui.vn_edit.count"));
            countBox.setMaxLength(5);
            countBox.setValue(String.valueOf(item.getCount()));
            countBox.setResponder(s -> this.updateItem(idx, "count", s));
            this.itemCountEdits.add(countBox);
            EditBox nbtBox = new EditBox(this.font, fieldX + this.width - LABEL_WIDTH - 105, rowY, 80, 16, Component.translatable("gui.vn_edit.nbt"));
            nbtBox.setMaxLength(999999999);
            nbtBox.setValue(item.getNbt() != null ? item.getNbt() : "");
            nbtBox.setResponder(s -> this.updateItem(idx, "nbt", s));
            this.itemNbtEdits.add(nbtBox);
            Button delBtn = Button.builder(Component.literal("X"), btn -> this.deleteItem(idx))
                    .bounds(fieldX + this.width - LABEL_WIDTH - 20, rowY, 16, 16).build();
            this.itemDeleteBtns.add(delBtn);
        }
    }

    private void rebuildOptionButtons() {
        this.clearOptionWidgets();
        if (this.currentEntry == null) {
            return;
        }
        List<DialogOption> options = this.getOptionsList();
        for (int i = 0; i < options.size(); i++) {
            DialogOption opt = options.get(i);
            int btnY = this.optionListStartY + i * OPTION_ROW_HEIGHT;
            int editX = this.x + LABEL_WIDTH + 5 + 220;
            int deleteX = editX + 52;
            Button editBtn = Button.builder(Component.translatable("gui.vn_edit.edit"), btn -> this.openOptionEditor(opt))
                    .bounds(editX, btnY, 50, 16).build();
            this.editOptionButtons.add(editBtn);
            Button deleteBtn = Button.builder(Component.translatable("gui.vn_edit.delete"), btn -> this.deleteOption(opt))
                    .bounds(deleteX, btnY, 30, 16).build();
            this.deleteOptionButtons.add(deleteBtn);
        }
    }

    private void openOptionEditor(DialogOption option) {
        Consumer<DialogOption> onSave = edited -> {
            this.updateOption(option, edited);
            this.relayoutSections();
            this.recoverLogicTab();
        };
        Minecraft.getInstance().setScreen(new OptionEditScreen(option, onSave, Minecraft.getInstance().screen, this.currentSequence));
    }

    private void onAudioBrowse() {
        FileBrowserScreen.open(EditorConfig.SOUNDS_DIR.toFile(), new String[]{"wav", "ogg"}, path -> {
            this.audioPathBox.setValue(path);
            this.recoverLogicTab();
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
            this.recoverLogicTab();
        }, Minecraft.getInstance().screen));
    }

    private void recoverLogicTab() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof VNDialogEditorScreen editor) {
            editor.setPropertyPanelTab(2);
        }
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
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.next_id"), this.x + 5, this.y + 9, 0xCCCCCC);
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.audio_path"), this.x + 5, this.y + 74, 0xCCCCCC);
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.visibility_command"), this.x + 5, this.y + 96, 0xCCCCCC);
        this.nextNodeBtn.render(graphics, mouseX, mouseY, partialTick);
        this.endDialogCheck.render(graphics, mouseX, mouseY, partialTick);
        this.allowSkipCheck.render(graphics, mouseX, mouseY, partialTick);
        this.addCommandBtn.render(graphics, mouseX, mouseY, partialTick);
        this.addOptionBtn.render(graphics, mouseX, mouseY, partialTick);
        this.addItemBtn.render(graphics, mouseX, mouseY, partialTick);
        this.audioPathBox.render(graphics, mouseX, mouseY, partialTick);
        this.audioBrowseBtn.render(graphics, mouseX, mouseY, partialTick);
        this.audioPlayBtn.render(graphics, mouseX, mouseY, partialTick);
        this.audioFolderBtn.render(graphics, mouseX, mouseY, partialTick);
        this.visibilityCommandBox.render(graphics, mouseX, mouseY, partialTick);
        for (EditBox box : this.commandEdits) {
            box.render(graphics, mouseX, mouseY, partialTick);
        }
        for (Button btn : this.commandDeleteBtns) {
            btn.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.commands"), this.x + 5, this.commandListStartY - 12, 0xCCCCCC);
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.display_items"), this.x + 5, this.displayItemsStartY - 12, 0xCCCCCC);
        for (EditBox box : this.itemIdEdits) {
            box.render(graphics, mouseX, mouseY, partialTick);
        }
        for (EditBox box : this.itemCountEdits) {
            box.render(graphics, mouseX, mouseY, partialTick);
        }
        for (EditBox box : this.itemNbtEdits) {
            box.render(graphics, mouseX, mouseY, partialTick);
        }
        for (Button btn : this.itemDeleteBtns) {
            btn.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.options"), this.x + 5, this.optionListStartY - 12, 0xCCCCCC);
        DialogOption[] opts;
        if (this.currentEntry != null && (opts = this.currentEntry.getOptions()) != null) {
            for (int i = 0; i < opts.length; i++) {
                DialogOption opt = opts[i];
                int yPos = this.optionListStartY + i * OPTION_ROW_HEIGHT;
                String summary = (opt.getText("") != null ? opt.getText("").getString() : "?") + " \u2192 " + (opt.getTargetId() != null ? opt.getTargetId() : "?");
                graphics.drawString(this.font, summary, this.x + LABEL_WIDTH + 5, yPos, 0xFFFFFF);
            }
        }
        for (Button btn : this.editOptionButtons) {
            btn.render(graphics, mouseX, mouseY, partialTick);
        }
        for (Button btn : this.deleteOptionButtons) {
            btn.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public List<? extends GuiEventListener> children() {
        List<GuiEventListener> list = new ArrayList<>(List.of(this.nextNodeBtn, this.endDialogCheck, this.allowSkipCheck,
                this.addCommandBtn, this.addOptionBtn, this.addItemBtn, this.audioPathBox, this.audioBrowseBtn, this.audioPlayBtn,
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
        this.endDialogCheck.visible = visible;
        this.allowSkipCheck.visible = visible;
        this.addCommandBtn.visible = visible;
        this.addOptionBtn.visible = visible;
        this.addItemBtn.visible = visible;
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
        // Static section (114) + commands + items + options + padding
        return 114 + cmdCount * COMMAND_ROW_HEIGHT + 8 + 20 + itemCount * COMMAND_ROW_HEIGHT + 8 + 20 + optCount * OPTION_ROW_HEIGHT + 20;
    }

    /**
     * 静默设置 Checkbox 的选中状态，不触发 OnValueChange 回调。
     * 1.21.1 的 Checkbox 构造器非 public，无法继承；selected 字段为 private，
     * 故通过反射设置。反射失败时静默忽略（仅影响 UI 显示，不影响数据）。
     */
    private static void setCheckboxSelectedSilent(Checkbox checkbox, boolean selected) {
        if (checkbox == null || checkbox.selected() == selected) {
            return;
        }
        try {
            java.lang.reflect.Field f = Checkbox.class.getDeclaredField("selected");
            f.setAccessible(true);
            f.setBoolean(checkbox, selected);
        } catch (ReflectiveOperationException e) {
            Dialog.LOGGER.warn("Failed to silently set checkbox state via reflection", e);
        }
    }
}
