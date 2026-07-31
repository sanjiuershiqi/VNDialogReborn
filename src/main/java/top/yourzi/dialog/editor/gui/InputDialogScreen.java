package top.yourzi.dialog.editor.gui;

import net.minecraft.client.gui.GuiGraphics;
import top.yourzi.dialog.editor.gui.widget.EditorButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.util.EditorTheme;

import java.util.function.Consumer;

/**
 * 通用文本输入对话框。融合自 visual_mod_edit_vndialog。
 */
public class InputDialogScreen extends Screen {
    private final String initialValue;
    private final Consumer<String> onConfirm;
    private final Screen parent;
    private EditBox inputBox;

    public InputDialogScreen(Component title, String initialValue, Consumer<String> onConfirm, Screen parent) {
        super(title);
        this.initialValue = initialValue;
        this.onConfirm = onConfirm;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int fieldWidth = 200;
        int fieldX = (this.width - fieldWidth) / 2;
        int y = 40;
        this.inputBox = new EditBox(this.font, fieldX, y, fieldWidth, 20, Component.empty());
        this.inputBox.setMaxLength(999999999);
        this.inputBox.setValue(this.initialValue);
        this.addRenderableWidget(this.inputBox);
        y += 30;
        this.addRenderableWidget(EditorButton.builder(Component.translatable("gui.vn_edit.save"), btn -> {
            String result = this.inputBox.getValue().trim();
            if (!result.isEmpty()) {
                this.onConfirm.accept(result);
            }
            this.onClose();
        }).bounds(this.width / 2 - 55, y, 110, 20).build());
        this.addRenderableWidget(EditorButton.builder(Component.translatable("gui.vn_edit.cancel"), btn -> this.onClose())
                .bounds(this.width / 2 + 65, y, 50, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, EditorTheme.TEXT_PRIMARY);
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
