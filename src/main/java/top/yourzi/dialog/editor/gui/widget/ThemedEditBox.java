package top.yourzi.dialog.editor.gui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.util.EditorTheme;

/**
 * 编辑器统一输入框。Minecraft 原生 EditBox 的行为保持不变，只收口文字与边框色，
 * 让所有编辑子屏使用同一种输入组件视觉。
 */
public class ThemedEditBox extends EditBox {
    public ThemedEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        this.setTextColor(EditorTheme.TEXT_PRIMARY);
        this.setTextColorUneditable(EditorTheme.TEXT_MUTED);
        this.setBordered(false);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int border = this.isFocused() ? EditorTheme.ACCENT : EditorTheme.BORDER;
        graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), EditorTheme.BG_DEEPEST);

        /*
         * The vanilla baseline is a little too high for the compact fields used
         * by this editor (especially with CJK glyphs).  Keep the widget bounds
         * unchanged, but move the complete native layer -- value, hint, caret
         * and selection -- together.  The scissor is deliberately inside the
         * border so tall glyphs can never paint into the 1px frame or adjacent
         * rows.  Moving only the hint would make focused fields disagree with
         * their caret/selection, so all EditBox rendering uses the same scope.
         */
        int clipLeft = this.getX() + 1;
        int clipTop = this.getY() + 1;
        int clipRight = this.getX() + this.getWidth() - 1;
        int clipBottom = this.getY() + this.getHeight() - 1;
        if (clipRight > clipLeft && clipBottom > clipTop) {
            graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 1.0f, 0.0f);
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        graphics.pose().popPose();
        if (clipRight > clipLeft && clipBottom > clipTop) {
            graphics.disableScissor();
        }

        graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + 1, border);
        graphics.fill(this.getX(), this.getY() + this.getHeight() - 1, this.getX() + this.getWidth(), this.getY() + this.getHeight(), border);
        graphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.getHeight(), border);
        graphics.fill(this.getX() + this.getWidth() - 1, this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), border);
    }
}
