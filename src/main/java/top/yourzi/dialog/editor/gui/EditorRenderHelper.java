package top.yourzi.dialog.editor.gui;

import net.minecraft.client.gui.GuiGraphics;
import top.yourzi.dialog.editor.util.EditorTheme;

/**
 * 编辑器渲染辅助工具类（纯静态）。
 * 集中常见渲染动作，消除各控件/屏幕重复手画 1px 边框、滚动条、半透明色的样板代码。
 * 借鉴 Sparkle-Morpher 的 RoulettePanelStyle 思路：渲染动作集中复用。
 */
public final class EditorRenderHelper {

    private EditorRenderHelper() {
    }

    /**
     * 画 1px 边框矩形（4 条线），替代各控件手画 4 次 graphics.fill。
     * 坐标语义与 GuiGraphics.fill 一致：左上 (x,y) 到右下 (x+w, y+h)，不含右下边界像素。
     */
    public static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w < 1 || h < 1) return;
        g.fill(x, y, x + w, y + 1, color);             // top
        g.fill(x, y + h - 1, x + w, y + h, color);     // bottom
        g.fill(x, y, x + 1, y + h, color);             // left
        g.fill(x + w - 1, y, x + w, y + h, color);     // right
    }

    /**
     * 画填充矩形 + 1px 边框的组合（背景面板常用）。
     */
    public static void fillWithBorder(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        if (w < 1 || h < 1) return;
        g.fill(x, y, x + w, y + h, fill);
        drawBorder(g, x, y, w, h, border);
    }

    /**
     * 画垂直滚动条（轨道 + 滑块），统一 4+ 处重复的滚动条绘制公式。
     * 公式：thumbH = max(10, viewH * viewH / totalH)；thumbY = top + scrollOffset / (totalH - viewH) * (viewH - thumbH)。
     *
     * @param trackX       轨道左上 X（通常在容器右侧）
     * @param trackY       轨道左上 Y（容器顶部）
     * @param barW         滚动条宽度
     * @param viewH        可视区高度（用于计算 thumb 比例）
     * @param totalH       内容总高度
     * @param scrollOffset 当前滚动偏移
     * @return true 表示有滚动条被绘制（即 totalH > viewH）
     */
    public static boolean drawVerticalScrollbar(GuiGraphics g, int trackX, int trackY, int barW,
                                                int viewH, int totalH, int scrollOffset) {
        if (totalH <= viewH) return false;
        int thumbH = Math.max(10, viewH * viewH / totalH);
        int maxOffset = totalH - viewH;
        int thumbY = trackY + (maxOffset > 0
                ? (int) ((float) scrollOffset / maxOffset * (viewH - thumbH))
                : 0);
        // 轨道
        g.fill(trackX, trackY, trackX + barW, trackY + viewH, EditorTheme.SCROLLBAR_TRACK);
        // 滑块
        g.fill(trackX, thumbY, trackX + barW, thumbY + thumbH, EditorTheme.SCROLLBAR_THUMB);
        return true;
    }

    /**
     * alpha 混合辅助：把任意 ARGB 颜色的 alpha 通道替换为指定值（0-255）。
     * 消除散落的 0x33FFFFFF / 0xCC000000 / 0x804A9EFF 等半透明魔法数字。
     */
    public static int withAlpha(int argb, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * alpha 混合辅助：按比例（0.0-1.0）设置 alpha 通道。
     * 例：withAlphaRatio(0xFFFFFFFF, 0.2f) → 0x33FFFFFF。
     */
    public static int withAlphaRatio(int argb, float ratio) {
        float r = Math.max(0f, Math.min(1f, ratio));
        return withAlpha(argb, (int) (r * 255f + 0.5f));
    }
}
