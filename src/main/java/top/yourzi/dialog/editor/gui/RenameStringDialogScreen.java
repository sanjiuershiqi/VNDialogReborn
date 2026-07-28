package top.yourzi.dialog.editor.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * 重命名任意字符串（如对话序列 ID）的对话框。融合自 visual_mod_edit_vndialog。
 */
public class RenameStringDialogScreen extends Screen {
    private final String initialValue;
    private final Consumer<String> onRename;
    private final Screen parent;
    private EditBox idBox;

    public RenameStringDialogScreen(String initialValue, Consumer<String> onRename, Screen parent) {
        super(Component.translatable("gui.vn_edit.rename.title"));
        this.initialValue = initialValue;
        this.onRename = onRename;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.idBox = new EditBox(this.font, this.width / 2 - 100, 40, 200, 20, Component.empty());
        this.idBox.setMaxLength(999999999);
        this.idBox.setValue(this.initialValue);
        this.addRenderableWidget(this.idBox);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.vn_edit.save"), btn -> {
            String newId = this.idBox.getValue().trim();
            if (!newId.isEmpty()) {
                this.onRename.accept(newId);
            }
            this.onClose();
        }).bounds(this.width / 2 - 55, 70, 110, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.vn_edit.cancel"), btn -> this.onClose())
                .bounds(this.width / 2 + 65, 70, 50, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
