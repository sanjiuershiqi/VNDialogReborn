package top.yourzi.dialog.editor.gui;

import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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

    private final DialogOption option;
    private final Consumer<DialogOption> onSave;
    private final Screen parent;
    private final DialogSequence sequence;
    private EditBox textBox;
    private Button targetNodeBtn;
    private Checkbox alwaysVisibleCheck;
    private final List<EditBox> commandBoxes = new ArrayList<>();
    private final List<Button> commandDeleteButtons = new ArrayList<>();
    private int commandListY;

    public OptionEditScreen(DialogOption option, Consumer<DialogOption> onSave, Screen parent, DialogSequence sequence) {
        super(Component.translatable("gui.vn_edit.option_edit.title"));
        this.option = option;
        this.onSave = onSave;
        this.parent = parent;
        this.sequence = sequence;
    }

    @Override
    protected void init() {
        super.init();
        int fieldWidth = 200;
        int fieldX = (this.width - fieldWidth) / 2;
        int y = 25;
        int inputHeight = 16;
        this.textBox = new EditBox(this.font, fieldX, y + 10, fieldWidth, inputHeight, Component.translatable("gui.vn_edit.option_text"));
        this.textBox.setMaxLength(999999999);
        this.textBox.setValue(this.option.getText("") != null ? this.option.getText("").getString() : "");
        this.addRenderableWidget(this.textBox);
        String currentTarget = this.option.getTargetId() != null ? this.option.getTargetId() : "None";
        y += inputHeight + 20;
        this.targetNodeBtn = Button.builder(Component.literal(currentTarget), btn -> this.openNodePicker())
                .bounds(fieldX, y + 10, fieldWidth, inputHeight).build();
        this.addRenderableWidget(this.targetNodeBtn);
        boolean isAlwaysVisible = this.option.getVisibilityCommand() == null || this.option.getVisibilityCommand().isEmpty();
        y += inputHeight + 20;
        this.alwaysVisibleCheck = Checkbox.builder(Component.translatable("gui.vn_edit.always_visible"), this.font)
                .pos(fieldX, y + 10)
                .maxWidth(fieldWidth)
                .selected(isAlwaysVisible)
                .build();
        this.addRenderableWidget(this.alwaysVisibleCheck);
        y += 30;
        Button addCommandBtn = Button.builder(Component.translatable("gui.vn_edit.add_command"), btn -> this.addCommand(""))
                .bounds(fieldX, y, 60, 16).build();
        this.addRenderableWidget(addCommandBtn);
        this.commandListY = y + 18;
        List<String> existingCmds = this.option.getCommand() != null ? this.option.getCommand() : new ArrayList<>();
        for (String cmd : existingCmds) {
            this.addCommand(cmd);
        }
        int bottomY = this.height - 30;
        Button saveBtn = Button.builder(Component.translatable("gui.vn_edit.save"), btn -> {
            this.option.setText(new JsonPrimitive(this.textBox.getValue()));
            if (this.alwaysVisibleCheck.selected()) {
                this.option.setVisibilityCommand(null);
            } else {
                this.option.setVisibilityCommand("execute unless true");
            }
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
        Button cancelBtn = Button.builder(Component.translatable("gui.vn_edit.cancel"), btn -> this.onClose())
                .bounds(this.width / 2 + 65, bottomY, 50, 20).build();
        this.addRenderableWidget(cancelBtn);
    }

    private void openNodePicker() {
        this.option.setText(new JsonPrimitive(this.textBox.getValue()));
        if (this.sequence == null) {
            return;
        }
        Minecraft.getInstance().setScreen(new NodePickerScreen(this.sequence, selectedId -> {
            this.targetNodeBtn.setMessage(Component.literal(selectedId.isEmpty() ? "None" : selectedId));
            this.option.setTargetId(selectedId.isEmpty() ? null : selectedId);
        }, Minecraft.getInstance().screen));
    }

    private void addCommand(String initialValue) {
        int idx = this.commandBoxes.size();
        int rowY = this.commandListY + idx * COMMAND_ROW_HEIGHT;
        EditBox box = new EditBox(this.font, this.width / 2 - 100, rowY, 170, 16, Component.translatable("gui.vn_edit.command"));
        box.setMaxLength(999999999);
        box.setValue(initialValue);
        this.commandBoxes.add(box);
        this.addRenderableWidget(box);
        Button delBtn = Button.builder(Component.literal("X"), btn -> {
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
        Button btn = this.commandDeleteButtons.remove(index);
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
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        int fieldX = (this.width - 200) / 2;
        int y = 25;
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.option_text"), fieldX + 5, y, 0xCCCCCC);
        y += 36;
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.option_target"), fieldX + 5, y, 0xCCCCCC);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
