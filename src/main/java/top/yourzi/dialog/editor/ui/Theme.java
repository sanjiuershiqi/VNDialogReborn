package top.yourzi.dialog.editor.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Single source of colors, metrics and primitive drawing for the editor.
 */
public final class Theme {
    public static final int BG = 0xFF101415;
    public static final int SURFACE = 0xFF171C1E;
    public static final int RAISED = 0xFF20272A;
    public static final int HOVER = 0xFF2A3337;
    public static final int SELECTED = 0xFF233B40;
    public static final int FIELD = 0xFF0C0F10;
    public static final int BORDER = 0xFF343F43;
    public static final int BORDER_STRONG = 0xFF56656A;
    public static final int TEXT = 0xFFE6EBE8;
    public static final int TEXT_DIM = 0xFFAAB6B3;
    public static final int TEXT_MUTED = 0xFF6F7D7B;
    public static final int ACCENT = 0xFFD9BE3A;
    public static final int ACCENT_DIM = 0xFF6E6220;
    public static final int CYAN = 0xFF55C2C5;
    public static final int SUCCESS = 0xFF62D6A5;
    public static final int WARNING = 0xFFF0B35A;
    public static final int DANGER = 0xFFE56B5D;
    public static final int SCRIM = 0xB0000000;
    public static final int SCROLL_TRACK = 0x30000000;
    public static final int SCROLL_THUMB = 0x80A8B4B2;

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
