package top.yourzi.dialog.editor.gui;

import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import top.yourzi.dialog.editor.gui.widget.EditorButton;
import top.yourzi.dialog.editor.gui.widget.ThemedEditBox;
import top.yourzi.dialog.editor.gui.widget.BooleanOptionRow;
import top.yourzi.dialog.editor.gui.property.Option;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.util.EditorTheme;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.model.DialogSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 选项编辑屏幕：编辑对话选项的文本、目标节点、可见性、命令。融合自 visual_mod_edit_vndialog。
 */
public class OptionEditScreen extends Screen {
    private static final int COMMAND_ROW_HEIGHT = 16;
    private static final int FIELD_WIDTH = 200;
    private static final int INPUT_HEIGHT = 16;
    private static final int LABEL_GAP = 11;
    private static final int ROW_GAP = 6;

    private final DialogOption option;
    private final Consumer<DialogOption> onSave;
    private final Screen parent;
    private final DialogSequence sequence;
    /** 编辑期间的临时状态，取消时不影响原 option */
    private String draftText;
    private String draftTargetId;
    private String draftVisibilityCommand;
    private List<String> draftCommands;
    private EditBox textBox;
    private EditorButton targetNodeBtn;
    /** 始终可见开关：用编辑器统一风格的 BooleanOptionRow 替代原生 Checkbox（与逻辑属性页视觉一致）。 */
    private BooleanOptionRow alwaysVisibleRow;
    private Option<Boolean> alwaysVisibleOption;
    private EditBox visibilityCommandBox;
    private final List<EditBox> commandBoxes = new ArrayList<>();
    private final List<EditorButton> commandDeleteButtons = new ArrayList<>();
    /** 共享 Y 坐标字段：init() 和 render() 共用，消除双套游标漂移导致的标签/输入框错位。 */
    private int fieldX;
    private int textLabelY;
    private int textBoxY;
    private int targetLabelY;
    private int targetBtnY;
    private int checkboxY;
    private int visibilityLabelY;
    private int visibilityBoxY;
    private int addCommandBtnY;
    private int commandListY;

    public OptionEditScreen(DialogOption option, Consumer<DialogOption> onSave, Screen parent, DialogSequence sequence) {
        super(Component.translatable("gui.vn_edit.option_edit.title"));
        this.option = option;
        this.onSave = onSave;
        this.parent = parent;
        this.sequence = sequence;
        this.draftText = option.getText("") != null ? option.getText("").getString() : "";
        this.draftTargetId = option.getTargetId() != null ? option.getTargetId() : null;
        this.draftVisibilityCommand = option.getVisibilityCommand() != null ? option.getVisibilityCommand() : "";
        this.draftCommands = new ArrayList<>(option.getCommand() != null ? option.getCommand() : new ArrayList<>());
    }

    @Override
    protected void init() {
        super.init();
        this.commandBoxes.clear();
        this.commandDeleteButtons.clear();
        this.fieldX = (this.width - FIELD_WIDTH) / 2;
        int cursorY = 25;
        // 选项文本：标签在输入框上方 LABEL_GAP
        this.textLabelY = cursorY;
        this.textBoxY = cursorY + LABEL_GAP;
        this.textBox = new ThemedEditBox(this.font, fieldX, textBoxY, FIELD_WIDTH, INPUT_HEIGHT, Component.translatable("gui.vn_edit.option_text"));
        this.textBox.setMaxLength(999999999);
        this.textBox.setValue(this.draftText);
        this.addRenderableWidget(this.textBox);
        cursorY = this.textBoxY + INPUT_HEIGHT + ROW_GAP;
        // 目标节点：标签在按钮上方
        this.targetLabelY = cursorY;
        this.targetBtnY = cursorY + LABEL_GAP;
        String currentTarget = this.draftTargetId != null && !this.draftTargetId.isEmpty() ? this.draftTargetId : Component.translatable("gui.vn_edit.none").getString();
        this.targetNodeBtn = EditorButton.builder(Component.literal(currentTarget), btn -> this.openNodePicker())
                .bounds(fieldX, targetBtnY, FIELD_WIDTH, INPUT_HEIGHT).build();
        this.addRenderableWidget(this.targetNodeBtn);
        cursorY = this.targetBtnY + INPUT_HEIGHT + ROW_GAP;
        // 始终可见开关：BooleanOptionRow 与编辑器其他复选行风格统一
        this.checkboxY = cursorY;
        this.alwaysVisibleOption = new Option<>(
                this::isAlwaysVisible,
                v -> { if (Boolean.TRUE.equals(v)) this.draftVisibilityCommand = ""; },
                null);
        this.alwaysVisibleRow = new BooleanOptionRow(fieldX, checkboxY, FIELD_WIDTH, INPUT_HEIGHT,
                Component.translatable("gui.vn_edit.always_visible"), this.alwaysVisibleOption, this.font);
        this.addRenderableWidget(this.alwaysVisibleRow);
        this.alwaysVisibleOption.snapshot();
        cursorY = this.checkboxY + INPUT_HEIGHT + ROW_GAP;
        // 可见性命令：标签在输入框上方
        this.visibilityLabelY = cursorY;
        this.visibilityBoxY = cursorY + LABEL_GAP;
        this.visibilityCommandBox = new ThemedEditBox(this.font, fieldX, visibilityBoxY, FIELD_WIDTH, INPUT_HEIGHT, Component.translatable("gui.vn_edit.visibility_command"));
        this.visibilityCommandBox.setMaxLength(999999999);
        this.visibilityCommandBox.setValue(this.draftVisibilityCommand != null ? this.draftVisibilityCommand : "");
        this.visibilityCommandBox.setVisible(!this.isAlwaysVisible());
        this.addRenderableWidget(this.visibilityCommandBox);
        cursorY = this.visibilityBoxY + INPUT_HEIGHT + ROW_GAP;
        // 添加命令按钮 + 命令列表
        this.addCommandBtnY = cursorY;
        EditorButton addCommandBtn = EditorButton.builder(Component.translatable("gui.vn_edit.add_command"), btn -> this.addCommand(""))
                .bounds(fieldX, addCommandBtnY, 60, 16).build();
        this.addRenderableWidget(addCommandBtn);
        this.commandListY = cursorY + 18;
        for (String cmd : this.draftCommands) {
            this.addCommand(cmd);
        }
        // 底部保存/取消按钮
        int bottomY = this.height - 30;
        EditorButton saveBtn = EditorButton.builder(Component.translatable("gui.vn_edit.save"), btn -> {
            // 保存时才将草稿写回原 option
            this.draftText = this.textBox.getValue();
            this.option.setText(new JsonPrimitive(this.draftText));
            if (this.isAlwaysVisible()) {
                this.option.setVisibilityCommand(null);
            } else {
                String visCmd = this.visibilityCommandBox.getValue().trim();
                this.option.setVisibilityCommand(visCmd.isEmpty() ? null : visCmd);
            }
            this.draftTargetId = this.draftTargetId != null && this.draftTargetId.isEmpty() ? null : this.draftTargetId;
            this.option.setTargetId(this.draftTargetId);
            ArrayList<String> cmds = new ArrayList<>();
            for (EditBox box : this.commandBoxes) {
                String val = box.getValue().trim();
                if (val.isEmpty()) {
                    continue;
                }
                cmds.add(val);
            }
            this.option.setCommand(cmds.isEmpty() ? null : cmds);
            this.onSave.accept(this.option);
            this.onClose();
        }).bounds(this.width / 2 - 55, bottomY, 110, 20).build();
        this.addRenderableWidget(saveBtn);
        EditorButton cancelBtn = EditorButton.builder(Component.translatable("gui.vn_edit.cancel"), btn -> this.onClose())
                .bounds(this.width / 2 + 65, bottomY, 50, 20).build();
        this.addRenderableWidget(cancelBtn);
    }

    /** 当前草稿是否"始终可见"（可见性命令为空）。 */
    private boolean isAlwaysVisible() {
        return this.draftVisibilityCommand == null || this.draftVisibilityCommand.isEmpty();
    }

    private void openNodePicker() {
        // 切屏前把各输入框实时值同步到草稿字段。NodePicker 返回时本实例被复用
        //（setScreen(parent) 只重新调用 init()，不重建实例），init() 会从草稿字段
        // 重建控件，故需先同步以避免输入丢失。
        this.draftText = this.textBox.getValue();
            if (this.isAlwaysVisible()) {
            this.draftVisibilityCommand = "";
        } else {
            this.draftVisibilityCommand = this.visibilityCommandBox.getValue();
        }
        this.draftCommands = new ArrayList<>();
        for (EditBox box : this.commandBoxes) {
            this.draftCommands.add(box.getValue());
        }
        if (this.sequence == null) {
            return;
        }
        Minecraft.getInstance().setScreen(new NodePickerScreen(this.sequence, selectedId -> {
            // 回调在同一实例上执行（返回时 setScreen(parent) 复用本实例），
            // 直接更新草稿字段，init() 重建后 targetNodeBtn 即显示新值（含清空为 None）
            this.draftTargetId = selectedId.isEmpty() ? null : selectedId;
        }, Minecraft.getInstance().screen));
    }

    private void addCommand(String initialValue) {
        int idx = this.commandBoxes.size();
        int rowY = this.commandListY + idx * COMMAND_ROW_HEIGHT;
        EditBox box = new ThemedEditBox(this.font, this.width / 2 - 100, rowY, 170, 16, Component.translatable("gui.vn_edit.command"));
        box.setMaxLength(999999999);
        box.setValue(initialValue);
        this.commandBoxes.add(box);
        this.addRenderableWidget(box);
        EditorButton delBtn = EditorButton.builder(Component.literal("\u2715"), btn -> {
            int i = this.commandDeleteButtons.indexOf(btn);
            if (i >= 0) {
                this.removeCommand(i);
            }
        }).bounds(this.width / 2 + 75, rowY, 20, 16).build();
        this.commandDeleteButtons.add(delBtn);
        this.addRenderableWidget(delBtn);
    }

    private void removeCommand(int index) {
        if (index < 0 || index >= this.commandBoxes.size()) {
            return;
        }
        EditBox box = this.commandBoxes.remove(index);
        EditorButton btn = this.commandDeleteButtons.remove(index);
        this.removeWidget(box);
        this.removeWidget(btn);
        for (int i = index; i < this.commandBoxes.size(); i++) {
            int rowY = this.commandListY + i * COMMAND_ROW_HEIGHT;
            this.commandBoxes.get(i).setY(rowY);
            this.commandDeleteButtons.get(i).setY(rowY);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        int panelW = Math.min(460, this.width - 24);
        EditorTheme.drawPanelHeader(graphics, this.font, (this.width - panelW) / 2, 4, panelW, "OP", this.title);
        // 用 init() 共享的 Y 字段绘制标签，消除双套游标错位
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.option_text"), this.fieldX, this.textLabelY, EditorTheme.TEXT_SECONDARY);
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.option_target"), this.fieldX, this.targetLabelY, EditorTheme.TEXT_SECONDARY);
        if (!this.isAlwaysVisible()) {
            graphics.drawString(this.font, Component.translatable("gui.vn_edit.visibility_command"), this.fieldX, this.visibilityLabelY, EditorTheme.TEXT_SECONDARY);
        }
        this.visibilityCommandBox.setVisible(!this.isAlwaysVisible());
        super.render(graphics, mouseX, mouseY, partialTick);
        EditorRenderHelper.drawFocusedEditBoxBorders(graphics, this.children());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, EditorTheme.BG_DEEPEST);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
