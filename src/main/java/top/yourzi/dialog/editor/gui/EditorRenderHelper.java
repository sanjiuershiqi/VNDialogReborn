package top.yourzi.dialog.editor.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.util.Mth;
import top.yourzi.dialog.editor.util.EditorTheme;

import java.util.List;

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
     * 统一所有原生 EditBox 的边框语义：未聚焦使用金属灰，聚焦使用终末地黄。
     * 这样原生输入框也会遵守编辑器组件契约，不再出现旧黑框与新模块头混用。
     */
    public static void drawFocusedEditBoxBorders(GuiGraphics g, List<? extends GuiEventListener> children) {
        for (GuiEventListener child : children) {
            if (child instanceof EditBox box && box.isFocused() && box.isVisible()) {
                drawBorder(g, box.getX(), box.getY(), box.getWidth(), box.getHeight(), EditorTheme.ACCENT);
            } else if (child instanceof EditBox box && box.isVisible()) {
                drawBorder(g, box.getX(), box.getY(), box.getWidth(), box.getHeight(), EditorTheme.BORDER);
            }
        }
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

    /**
     * 滚动条交互状态（拖拽标志 + 平滑显示值）。建议作为宿主控件的字段持有。
     * 借鉴 Sparkle-Morpher OptionScreen 的 draggingXxxScrollbar + scrollDisplay lerp 方案，
     * 封装为可复用状态对象，避免 PropertyPanel/DialogTreeWidget/PortraitListScreen 三处重复。
     */
    public static final class ScrollState {
        /** 当前是否正在拖拽滚动条滑块。 */
        public boolean dragging;
        /** 平滑显示值（浮点），每帧向目标 offset 逼近。 */
        public float display;

        public ScrollState() {
        }

        /**
         * 每帧推进 display 向 offset 逼近，差值<0.5 吸附。
         * lerp 系数 1 - exp(-dt*18) 与 Sparkle 一致，dt 越大逼近越快。
         *
         * @param offset 目标滚动偏移（整型，由滚轮/拖拽产生）
         * @param dt     距上一帧的秒数（首帧传 0 直接吸附）
         * @return round 后的显示偏移，供 translate 与鼠标补偿使用
         */
        public int tick(float offset, float dt) {
            if (dt <= 0) {
                display = offset;
                return Math.round(offset);
            }
            float lerp = 1.0f - (float) Math.exp(-dt * 18.0f);
            display += (offset - display) * lerp;
            if (Math.abs(offset - display) < 0.5f) display = offset;
            return Math.round(display);
        }

        /** 重置 display 到指定 offset（重置滚动时同步，避免首帧跳变）。 */
        public void reset(float offset) {
            display = offset;
        }
    }

    /**
     * 命中检测：鼠标是否在垂直滚动条轨道上。
     */
    public static boolean isOnVerticalScrollbar(double mx, double my, int trackX, int trackY, int trackW, int trackH) {
        return mx >= trackX && mx < trackX + trackW && my >= trackY && my < trackY + trackH;
    }

    /**
     * 按鼠标 Y 在轨道中的比例映射到 scrollOffset，返回 clamp 后的目标 offset。
     */
    public static int offsetFromMouseY(double mouseY, int trackTop, int trackBottom, int maxScroll) {
        double t = Mth.clamp((mouseY - trackTop) / Math.max(1, trackBottom - trackTop), 0.0, 1.0);
        return (int) (t * maxScroll);
    }

    // ===== 第八轮视觉美化工具（借鉴 Sparkle-Morpher） =====

    /**
     * 圆角矩形填充：用四角补背景色小块遮角的方式近似圆角（radius 建议 2，MC GUI 缩放下不宜过大）。
     * 比 GLSL 圆角 shader 简单，无需 shader 也能让控件边缘柔和。
     * 实现思路：先整块 fill 目标色，再用背景色（或透明）覆盖四角的小方块——但 GUI 无"挖空"能力，
     * 故改为：先 fill 中心十字 + 中段两条边，四角不画（露出底层背景），达到圆角视觉。
     */
    public static void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (w < 1 || h < 1) return;
        int r = Math.max(0, Math.min(Math.min(radius, w / 2), h / 2));
        if (r == 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        // 顶边（不含左右角各 r 像素）
        g.fill(x + r, y, x + w - r, y + r, color);
        // 底边
        g.fill(x + r, y + h - r, x + w - r, y + h, color);
        // 中段（满宽）
        g.fill(x, y + r, x + w, y + h - r, color);
    }

    /**
     * 带投影的填充：先在矩形四周外扩 2px 画半透明阴影（向下偏移 1px 模拟光源在上），
     * 再画实色填充 + 1px 边框。用于浮层/弹窗，制造悬浮感。
     * 借鉴 Sparkle-Morpher 浮层的阴影纵深思路。
     */
    public static void fillWithShadow(GuiGraphics g, int x, int y, int w, int h, int fill, int shadowColor) {
        if (w < 1 || h < 1) return;
        // 投影：四周外扩 2px，下方多 1px（光源在上）
        g.fill(x - 1, y - 1, x + w + 1, y + 1, shadowColor);             // 上
        g.fill(x - 1, y + h, x + w + 2, y + h + 2, shadowColor);         // 下（多 1px）
        g.fill(x - 1, y - 1, x, y + h + 1, shadowColor);                 // 左
        g.fill(x + w, y - 1, x + w + 1, y + h + 1, shadowColor);         // 右
        // 实色填充 + 边框
        g.fill(x, y, x + w, y + h, fill);
        drawBorder(g, x, y, w, h, EditorTheme.BORDER);
    }

    /**
     * 玻璃面板：半透明玻璃底 + 半透明亮蓝边框。无 GLSL 模糊也能有磨砂观感，
     * 借鉴 Sparkle-Morpher RoulettePanelStyle.glassPanel（去 blur 版本）。
     */
    public static void fillGlassPanel(GuiGraphics g, int x, int y, int w, int h) {
        if (w < 1 || h < 1) return;
        g.fill(x, y, x + w, y + h, EditorTheme.PANEL_GLASS);
        drawBorder(g, x, y, w, h, EditorTheme.PANEL_GLASS_BORDER);
    }

    /**
     * 颜色线性插值：按 t (0~1) 在 from/to 间按通道插值 alpha/R/G/B。
     * 供 hover 渐变使用，避免布尔硬切换的视觉跳变。
     */
    public static int lerpColor(int from, int to, float t) {
        float tc = Mth.clamp(t, 0f, 1f);
        int a = (int) (((from >> 24) & 0xFF) + (((to >> 24) & 0xFF) - ((from >> 24) & 0xFF)) * tc);
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * tc);
        int gg = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * tc);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * tc);
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    /**
     * 提亮颜色：RGB 各 +amount（clamp 0~255），alpha 不变。
     * 复制 Sparkle OptionRow.blendBg 逻辑，用于 hover 提亮而非硬换色。
     */
    public static int brighten(int argb, int amount) {
        int a = (argb >> 24) & 0xFF;
        int r = Math.max(0, Math.min(255, ((argb >> 16) & 0xFF) + amount));
        int gg = Math.max(0, Math.min(255, ((argb >> 8) & 0xFF) + amount));
        int b = Math.max(0, Math.min(255, (argb & 0xFF) + amount));
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    /**
     * 浮点进度 lerp 推进：用与 ScrollState.tick 一致的 1-exp(-dt*18) 系数逼近 target。
     * 供控件 hoverProgress 等动画状态使用，统一 lerp 节奏。
     * @param current 当前值
     * @param target  目标值（如 hover=1，非 hover=0）
     * @param dt      距上一帧秒数（首帧传 0 直接吸附）
     * @return 推进后的值
     */
    public static float tickProgress(float current, float target, float dt) {
        if (dt <= 0) {
            return target;
        }
        float lerp = 1.0f - (float) Math.exp(-dt * 18.0f);
        current += (target - current) * lerp;
        if (Math.abs(target - current) < 0.01f) current = target;
        return current;
    }
}
