package top.yourzi.dialog.editor.ui.core;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Frame-scoped services passed to retained nodes without coupling them to Screen. */
public record UiContext(GuiGraphics graphics, Font font, int mouseX, int mouseY, float partialTick,
                       UiStyle style) {
    public UiContext {
        if (style == null) style = UiStyle.defaults();
    }
}
