package top.yourzi.dialog.editor.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Single source of colors, metrics and primitive drawing for the editor.
 */
public final class Theme {
    // Neutral dark surfaces, separated by value rather than by outlines.
    public static final int BG = 0xFF1B1C1F;
    public static final int SURFACE = 0xFF222327;
    public static final int RAISED = 0xFF2A2B30;
    public static final int HOVER = 0xFF33353B;
    public static final int SELECTED = 0xFF283553;
    public static final int FIELD = 0xFF17181B;
    public static final int BORDER = 0xFF2F3136;
    public static final int BORDER_STRONG = 0xFF464951;
    public static final int SCRIM = 0xA00E0F11;

    public static final int TEXT = 0xFFE4E5E9;
    public static final int TEXT_DIM = 0xFFA9ACB4;
    public static final int TEXT_MUTED = 0xFF6E727B;

    // One accent for focus and primary actions; the rest carry meaning only.
    public static final int ACCENT = 0xFF6E9BFF;
    public static final int ACCENT_DIM = 0xFF3D5A99;
    public static final int CYAN = 0xFF6CC5B0;
    public static final int SUCCESS = 0xFF8BC77A;
    public static final int WARNING = 0xFFE5B567;
    public static final int DANGER = 0xFFEC7A8C;

    public static final int SCROLL_TRACK = 0x00000000;
    public static final int SCROLL_THUMB = 0x60A9ACB4;
    public static final int ROW = 18;
    public static final int GAP = 4;
    public static final int PAD = 6;
    public static final int SCROLLBAR = 4;
    public static final int LABEL_W = 58;

    private Theme() {
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    public static Component tr(String key, Object... args) {
        return Component.translatable("gui.vn_edit." + key, args);
    }

    public static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static String ellipsize(String text, int maxWidth) {
        Font font = font();
        if (text == null) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        if (maxWidth <= font.width("…")) {
            return "";
        }
        return font.plainSubstrByWidth(text, maxWidth - font.width("…")) + "…";
    }

    public static void text(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(font(), text, x, y, color, false);
    }

    public static void text(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(font(), text, x, y, color, false);
    }

    /** Draws text clipped to {@code maxWidth}, vertically centered in a row of height {@code h}. */
    public static void textIn(GuiGraphics g, String text, int x, int y, int maxWidth, int h, int color) {
        g.drawString(font(), ellipsize(text, maxWidth), x, y + (h - 8) / 2, color, false);
    }

    public static void centered(GuiGraphics g, String text, int cx, int y, int color) {
        g.drawString(font(), text, cx - font().width(text) / 2, y, color, false);
    }

    public static void scrollbar(GuiGraphics g, int x, int y, int h, int viewH, int contentH, double offset, boolean active) {
        if (contentH <= viewH || h <= 0) {
            return;
        }
        int thumbH = Math.max(12, h * viewH / contentH);
        int max = contentH - viewH;
        int thumbY = y + (int) ((h - thumbH) * (offset / max));
        g.fill(x, y, x + SCROLLBAR, y + h, SCROLL_TRACK);
        g.fill(x, thumbY, x + SCROLLBAR, thumbY + thumbH, active ? TEXT : SCROLL_THUMB);
    }

    public static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
