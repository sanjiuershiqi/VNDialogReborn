package top.yourzi.dialog.editor.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Panel-internal helpers shared by the title bar, status bar and inspector. */
public final class Chrome {
    private Chrome() {
    }

    /** Industrial title rail: accent tab, index label, clipped title and three tick marks. */
    public static void titleRail(GuiGraphics graphics, int x, int y, int width, String index, Component title) {
        if (width < 1) {
            return;
        }
        graphics.enableScissor(x, y, x + width, y + Theme.ROW);
        graphics.fill(x, y, x + width, y + Theme.ROW, Theme.RAISED);
        graphics.fill(x, y, x + 3, y + Theme.ROW, Theme.ACCENT);
        Theme.text(graphics, index, x + 10, y + (Theme.ROW - 8) / 2, Theme.ACCENT);
        Theme.textIn(graphics, title.getString(), x + 30, y, Math.max(1, width - 56), Theme.ROW, Theme.TEXT);
        for (int i = 0; i < 3; i++) {
            graphics.fill(x + width - 11 + i * 3, y + 5, x + width - 10 + i * 3, y + 8, Theme.ACCENT_DIM);
        }
        graphics.disableScissor();
        graphics.fill(x, y + Theme.ROW - 1, x + width, y + Theme.ROW, Theme.BORDER);
    }

    /** Section header inside a scrolling form: accent anchor, warm title and a divider. */
    public static void section(GuiGraphics graphics, int x, int y, int width, Component title) {
        graphics.fill(x, y, x + width, y + 14, Theme.RAISED);
        graphics.fill(x, y + 3, x + 2, y + 11, Theme.ACCENT);
        Theme.textIn(graphics, title.getString(), x + 7, y, width - 12, 14, Theme.TEXT);
        graphics.fill(x, y + 13, x + width, y + 14, Theme.BORDER);
    }

    /** Small caption used as a field label. */
    public static void caption(GuiGraphics graphics, Component text, int x, int y, int width) {
        Theme.textIn(graphics, text.getString(), x, y, width, 10, Theme.TEXT_MUTED);
    }

    public static final int SECTION_H = 14;
    public static final int CAPTION_H = 10;
}
