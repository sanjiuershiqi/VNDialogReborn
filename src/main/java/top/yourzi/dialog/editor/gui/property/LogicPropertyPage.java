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
import top.yourzi.dialog.editor.gui.VNDialogEditorScreen;
import top.yourzi.dialog.editor.util.AudioPreviewPlayer;
import top.yourzi.dialog.editor.util.EditorConfig;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.model.DialogSequence;

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
    private DialogSequence currentSequence;
    private DialogEntry currentEntry;
    private final List<EditBox> commandEdits = new ArrayList<>();
    private final List<Button> commandDeleteBtns = new ArrayList<>();
    private final List<Button> editOptionButtons = new ArrayList<>();
    private final List<Button> deleteOptionButtons = new ArrayList<>();
    private int optionListStartY;
    private int commandListStartY;
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
        this.audioPathBox = new EditBox(this.font, fieldX, audioY, fieldWidth - 100, 16, Component.translatable("gui.vn_edit.audio_path"));
        this.audioPathBox.setMaxLength(999999999);
        this.audioPathBox.setResponder(s -> {
            if (this.currentEntry != null) {
                this.currentEntry.setAudioPath(s.isEmpty() ? null : s);
            }
        });
        this.audioBrowseBtn = Button.builder(Component.translatable("gui.vn_edit.browse"), btn -> this.onAudioBrowse())
                .bounds(fieldX + fieldWidth - 95, audioY, 40, 16).build();
        this.audioPlayBtn = Button.builder(Component.translatable("gui.vn_edit.play"), btn -> {
            File audioFile;
            String pathStr = this.audioPathBox.getValue();
            if (!pathStr.isEmpty() && (audioFile = EditorConfig.SOUNDS_DIR.resolve(pathStr).toFile()).exists()) {
                AudioPreviewPlayer.play(audioFile);
            }
        }).bounds(fieldX + fieldWidth - 50, audioY, 48, 16).build();
        int commandHeaderY = audioY + 22;
        this.addCommandBtn = Button.builder(Component.translatable("gui.vn_edit.add_command"), btn -> this.onAddCommand())
                .bounds(fieldX, commandHeaderY, 60, 16).build();
        this.commandListStartY = commandHeaderY + 16 + 4;
        this.addOptionBtn = Button.builder(Component.translatable("gui.vn_edit.add_option"), btn -> this.onAddOption())
                .bounds(fieldX, commandHeaderY + 30, 60, 16).build();
        this.optionListStartY = commandHeaderY + 50;
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
        this.clearCommandWidgets();
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
        this.rebuildCommandWidgets();
        this.rebuildOptionButtons();
    }

    private void onAddCommand() {
        if (this.currentEntry == null) {
            return;
        }
        List<String> cmds = this.getCommandsList();
        cmds.add("");
        this.setCommandsList(cmds);
        this.rebuildCommandWidgets();
    }

    private void deleteCommand(int index) {
        if (this.currentEntry == null) {
            return;
        }
        List<String> cmds = this.getCommandsList();
        cmds.remove(index);
        this.setCommandsList(cmds);
        this.rebuildCommandWidgets();
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
        this.rebuildOptionButtons();
    }

    private void deleteOption(DialogOption option) {
        if (this.currentEntry == null) {
            return;
        }
        List<DialogOption> options = this.getOptionsList();
        options.remove(option);
        this.setOptionsList(options);
        this.rebuildOptionButtons();
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
            this.rebuildOptionButtons();
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
        this.nextNodeBtn.render(graphics, mouseX, mouseY, partialTick);
        this.endDialogCheck.render(graphics, mouseX, mouseY, partialTick);
        this.allowSkipCheck.render(graphics, mouseX, mouseY, partialTick);
        this.addCommandBtn.render(graphics, mouseX, mouseY, partialTick);
        this.addOptionBtn.render(graphics, mouseX, mouseY, partialTick);
        this.audioPathBox.render(graphics, mouseX, mouseY, partialTick);
        this.audioBrowseBtn.render(graphics, mouseX, mouseY, partialTick);
        this.audioPlayBtn.render(graphics, mouseX, mouseY, partialTick);
        for (EditBox box : this.commandEdits) {
            box.render(graphics, mouseX, mouseY, partialTick);
        }
        for (Button btn : this.commandDeleteBtns) {
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
                this.addCommandBtn, this.addOptionBtn, this.audioPathBox, this.audioBrowseBtn, this.audioPlayBtn));
        list.addAll(this.commandEdits);
        list.addAll(this.commandDeleteBtns);
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
        this.audioPathBox.setVisible(visible);
        this.audioBrowseBtn.visible = visible;
        this.audioPlayBtn.visible = visible;
        this.commandEdits.forEach(b -> b.setVisible(visible));
        this.commandDeleteBtns.forEach(b -> b.visible = visible);
        this.editOptionButtons.forEach(b -> b.visible = visible);
        this.deleteOptionButtons.forEach(b -> b.visible = visible);
    }

    @Override
    public int getContentHeight() {
        int count = this.currentEntry != null ? (this.currentEntry.getOptions() != null ? this.currentEntry.getOptions().length : 0) : 0;
        return this.optionListStartY - this.y + count * OPTION_ROW_HEIGHT + 40;
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
            // 反射失败时静默忽略
        }
    }
}
