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
         * EditBox's vanilla baseline is calculated from an 8px glyph box.  That
         * leaves the 16px fields used by compact property rows looking one pixel
         * too high compared with the 18px fields.  Keep the widget bounds (and
         * therefore hit testing/layout) unchanged, but nudge the native text,
         * caret and selection together inside compact fields.  Rendering the
         * whole native layer in one translated scope is important: moving only
         * the hint would make the hint/caret disagree when the field receives
         * focus.
         */
        int baselineNudge = this.getHeight() <= 16 ? 1 : 0;
        if (baselineNudge != 0) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0f, baselineNudge, 0.0f);
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            graphics.pose().popPose();
        } else {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
        }

        graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + 1, border);
        graphics.fill(this.getX(), this.getY() + this.getHeight() - 1, this.getX() + this.getWidth(), this.getY() + this.getHeight(), border);
        graphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.getHeight(), border);
        graphics.fill(this.getX() + this.getWidth() - 1, this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), border);
    }
}
