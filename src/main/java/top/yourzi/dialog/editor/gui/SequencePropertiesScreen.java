package top.yourzi.dialog.editor.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.util.EditorTheme;
import top.yourzi.dialog.model.DialogSequence;

import java.util.function.Consumer;

/**
 * 序列属性编辑屏幕：编辑对话序列的 title/description/effect/start 等序列级字段。
 * 这些字段在 test_dialog.json 中使用，但之前的编辑器没有界面编辑它们。
 */
public class SequencePropertiesScreen extends Screen {
    private static final int FIELD_WIDTH = 240;

    private final DialogSequence sequence;
    private final Consumer<DialogSequence> onSave;
    private final Screen parent;
    private EditBox titleBox;
    private EditBox descriptionBox;
    private EditBox effectBox;
    private EditBox startIdBox;
    private Button startPickerBtn;

    public SequencePropertiesScreen(DialogSequence sequence, Consumer<DialogSequence> onSave, Screen parent) {
        super(Component.translatable("gui.vn_edit.sequence_props.title"));
        this.sequence = sequence;
        this.onSave = onSave;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int fieldX = (this.width - FIELD_WIDTH) / 2;
        int y = 30;
        this.titleBox = new EditBox(this.font, fieldX, y + 12, FIELD_WIDTH, 16, Component.translatable("gui.vn_edit.sequence_title"));
        this.titleBox.setMaxLength(999999999);
        this.titleBox.setValue(this.sequence.getTitle() != null ? this.sequence.getTitle() : "");
        this.addRenderableWidget(this.titleBox);
        y += 38;
        this.descriptionBox = new EditBox(this.font, fieldX, y + 12, FIELD_WIDTH, 16, Component.translatable("gui.vn_edit.sequence_description"));
        this.descriptionBox.setMaxLength(999999999);
        this.descriptionBox.setValue(this.sequence.getDescription() != null ? this.sequence.getDescription() : "");
        this.addRenderableWidget(this.descriptionBox);
        y += 38;
        this.effectBox = new EditBox(this.font, fieldX, y + 12, FIELD_WIDTH, 16, Component.translatable("gui.vn_edit.sequence_effect"));
        this.effectBox.setMaxLength(999999999);
        this.effectBox.setValue(this.sequence.getEffect() != null ? this.sequence.getEffect() : "");
        this.addRenderableWidget(this.effectBox);
        y += 38;
        this.startIdBox = new EditBox(this.font, fieldX, y + 12, FIELD_WIDTH - 60, 16, Component.translatable("gui.vn_edit.sequence_start"));
        this.startIdBox.setMaxLength(999999999);
        this.startIdBox.setValue(this.sequence.getStartId() != null ? this.sequence.getStartId() : "");
        this.startIdBox.setResponder(s -> {});
        this.addRenderableWidget(this.startIdBox);
        this.startPickerBtn = Button.builder(Component.translatable("gui.vn_edit.pick"), btn -> this.openStartNodePicker())
                .bounds(fieldX + FIELD_WIDTH - 55, y + 12, 50, 16).build();
        this.addRenderableWidget(this.startPickerBtn);
        int bottomY = this.height - 30;
        Button saveBtn = Button.builder(Component.translatable("gui.vn_edit.save"), btn -> this.saveAndClose())
                .bounds(this.width / 2 - 55, bottomY, 110, 20).build();
        this.addRenderableWidget(saveBtn);
        Button cancelBtn = Button.builder(Component.translatable("gui.vn_edit.cancel"), btn -> this.onClose())
                .bounds(this.width / 2 + 65, bottomY, 50, 20).build();
        this.addRenderableWidget(cancelBtn);
    }

    private void openStartNodePicker() {
        Minecraft.getInstance().setScreen(new NodePickerScreen(this.sequence, selectedId -> {
            this.startIdBox.setValue(selectedId);
        }, Minecraft.getInstance().screen));
    }

    private void saveAndClose() {
        this.sequence.setTitle(this.titleBox.getValue().isEmpty() ? null : this.titleBox.getValue());
        this.sequence.setDescription(this.descriptionBox.getValue().isEmpty() ? null : this.descriptionBox.getValue());
        this.sequence.setEffect(this.effectBox.getValue().isEmpty() ? null : this.effectBox.getValue());
        this.sequence.setStartId(this.startIdBox.getValue().isEmpty() ? null : this.startIdBox.getValue());
        if (this.onSave != null) {
            this.onSave.accept(this.sequence);
        }
        this.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, EditorTheme.TEXT_PRIMARY);
        int fieldX = (this.width - FIELD_WIDTH) / 2;
        int y = 30;
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.sequence_title"), fieldX, y, EditorTheme.TEXT_SECONDARY);
        y += 38;
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.sequence_description"), fieldX, y, EditorTheme.TEXT_SECONDARY);
        y += 38;
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.sequence_effect"), fieldX, y, EditorTheme.TEXT_SECONDARY);
        y += 38;
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.sequence_start"), fieldX, y, EditorTheme.TEXT_SECONDARY);
        super.render(graphics, mouseX, mouseY, partialTick);
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
