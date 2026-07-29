package top.yourzi.dialog.editor.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.gui.widget.EditorButton;
import top.yourzi.dialog.editor.util.EditorTheme;

import java.util.function.Consumer;

/**
 * 编辑器风格确认对话框：替代原版 ConfirmScreen，与编辑器暗色主题统一。
 * 提供标题、消息、确认/取消两个按钮，按钮使用 EditorButton。
 */
public class EditorConfirmScreen extends Screen {
    private final Component message;
    private final Consumer<Boolean> callback;
    private final Screen parent;
    private final Component confirmLabel;
    private final Component cancelLabel;

    public EditorConfirmScreen(Component title, Component message, Consumer<Boolean> callback, Screen parent) {
        this(title, message, callback, parent,
                Component.translatable("gui.vn_edit.confirm"),
                Component.translatable("gui.vn_edit.cancel"));
    }

    public EditorConfirmScreen(Component title, Component message, Consumer<Boolean> callback, Screen parent,
                               Component confirmLabel, Component cancelLabel) {
        super(title);
        this.message = message;
        this.callback = callback;
        this.parent = parent;
        this.confirmLabel = confirmLabel;
        this.cancelLabel = cancelLabel;
    }

    @Override
    protected void init() {
        super.init();
        int btnY = this.height / 2 + 16;
        int btnW = 90;
        int btnH = 18;
        this.addRenderableWidget(EditorButton.builder(this.confirmLabel, btn -> this.finish(true))
                .bounds(this.width / 2 - btnW - 4, btnY, btnW, btnH).build());
        this.addRenderableWidget(EditorButton.builder(this.cancelLabel, btn -> this.finish(false))
                .bounds(this.width / 2 + 4, btnY, btnW, btnH).build());
    }

    private void finish(boolean confirmed) {
        this.callback.accept(confirmed);
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        // 居中面板背景
        int panelW = Math.min(320, this.width - 20);
        int panelH = 70;
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, EditorTheme.BG_SURFACE);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 1, EditorTheme.BORDER_LIGHT);
        graphics.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, EditorTheme.BORDER_LIGHT);
        graphics.fill(panelX, panelY, panelX + 1, panelY + panelH, EditorTheme.BORDER_LIGHT);
        graphics.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, EditorTheme.BORDER_LIGHT);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, panelY + 6, EditorTheme.TEXT_PRIMARY);
        graphics.drawCenteredString(this.font, this.message, this.width / 2, panelY + 22, EditorTheme.TEXT_SECONDARY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, EditorTheme.BG_DEEPEST);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        this.finish(false);
    }
}
