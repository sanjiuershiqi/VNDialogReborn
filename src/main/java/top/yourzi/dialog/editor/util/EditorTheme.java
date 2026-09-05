package top.yourzi.dialog.editor.util;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 编辑器设计令牌系统：集中管理配色、间距、尺寸常量，消除散落的 magic number。
 * 提供分节标题等通用渲染辅助方法。
 */
public class EditorTheme {
    // ===== 配色 - 精炼暗色主题 =====
    public static final int BG_DEEPEST     = 0xFF0C1011;
    public static final int BG_SURFACE     = 0xFF171D1E;
    public static final int BG_ELEVATED    = 0xFF232A2C;
    public static final int BG_HOVER       = 0xFF2C3638;
    public static final int BG_SELECTED    = 0xFF1E3A3D;
    public static final int BORDER         = 0xFF3B484A;
    public static final int BORDER_LIGHT   = 0xFF5C6A6B;
    public static final int TEXT_PRIMARY   = 0xFFE7ECE8;
    public static final int TEXT_SECONDARY = 0xFFB4C0BD;
    public static final int TEXT_MUTED     = 0xFF788684;
    public static final int ACCENT         = 0xFFD3B936;
    public static final int ACCENT_DIM     = 0xFF766A20;
    public static final int ACCENT_CYAN    = 0xFF55C2C5;
    public static final int DANGER         = 0xFFE56B5D;  // 删除/危险

    // ===== 视觉美化色板（第八轮，借鉴 Sparkle-Morpher RoulettePanelStyle/RouletteTheme） =====
    // 暖米色文字：比纯白柔和耐看，用于标题/重要值（Sparkle 面板主文字色 0xFFEDE1CC）
    public static final int TEXT_WARM      = 0xFFE8E1D0;
    // 浮层投影：30% 黑，浮层/弹窗四周外扩阴影，制造悬浮感（借鉴 Sparkle SLICE_SHADOW 思路）
    public static final int SHADOW_DROP    = 0x4D000000;
    // 内发光：22% 白，面板顶部 1px 叠层模拟顶光立体感（借鉴 Sparkle SLICE_INNER_GLOW）
    public static final int SHADOW_INNER_GLOW = 0x36FFFFFF;
    // 强调发光：25% 蓝，hover 外发光层（比硬描边柔和）
    public static final int GLOW_ACCENT    = 0x22D3B936;
    // 玻璃面板色系：半透明叠层产生磨砂观感（无需 GLSL 模糊也能有玻璃感）
    public static final int PANEL_GLASS       = 0x60405058;  // 玻璃底（借鉴 Sparkle GLASS）
    public static final int PANEL_GLASS_HOVER = 0x66576B76;  // 玻璃 hover（借鉴 Sparkle PANEL_HOVER）
    public static final int PANEL_GLASS_BORDER = 0x6EE4F5FF; // 半透明亮蓝边框（玻璃边缘反光）

    // ===== 状态栏语义色（借鉴 Sparkle setStatus 分色，用主题色而非 ChatFormatting） =====
    public static final int STATUS_SUCCESS = 0xFF62D6A5;  // 成功（青绿）
    public static final int STATUS_WARNING = 0xFFF0B35A;  // 警告（橙）
    public static final int STATUS_ERROR   = 0xFFE56B5D;  // 错误（珊瑚红）

    // ===== 语义化半透明色 =====
    // 收编散落在各 Screen 的 0x33FFFFFF / 0xCC000000 / 0x804A9EFF 等魔法数字，
    // 让半透明叠层也走主题系统。语义命名，便于全局调整。
    public static final int OVERLAY_MASK    = 0xCC000000;  // 模态遮罩（InputDialog/Confirm）
    public static final int HOVER_TINT      = 0x33FFFFFF;  // 悬停提亮叠层
    public static final int ACCENT_TINT     = 0x40E4D83A;
    public static final int DIVIDER         = 0x40FFFFFF;  // 分隔线/网格辅助线
    public static final int SCROLLBAR_TRACK = 0x33000000;  // 滚动条轨道
    public static final int SCROLLBAR_THUMB = 0x80B0B0B0;  // 滚动条滑块
    // 浮层/弹出面板底色：比 BG_SURFACE 更暗一档，与投影配合区分层级（收编 DropdownWidget 硬编码 0xFF181818）
    public static final int POPUP_BG       = 0xFF181818;

    // 终末地风格浅色模块，用于检查器标题、流程卡片高亮等局部区域。
    public static final int PANEL_LIGHT       = BG_ELEVATED;
    public static final int PANEL_LIGHT_TEXT  = 0xFF202426;
    public static final int PANEL_LIGHT_MUTED = TEXT_MUTED;
    public static final int PANEL_DARK_STRIPE  = BG_DEEPEST;

    // ===== 深色预览区域色（立绘预览等非编辑工作区） =====
    public static final int CANVAS_BG      = 0xFF161616;
    /** 选项边调色板：按选项索引取色，与节点端口圆点一一对应。 */
    public static final int[] OPTION_PALETTE = {
            0xFF62D6A5, 0xFFF0B35A, 0xFFE98AAE, 0xFF5CC8D6,
            0xFFA99AF5, 0xFFE56B5D, 0xFF9BD36A, 0xFFD8A858
    };

    // ===== 间距 =====
    public static final int PADDING      = 6;
    public static final int GAP          = 5;
    public static final int GAP_TIGHT    = 3;
    public static final int SECTION_GAP  = 8;
    public static final int ROW_GAP      = 4;   // 行间距
    public static final int SCROLLBAR_W  = 5;

    // ===== 尺寸 =====
    public static final int TOOLBAR_H    = 30;
    public static final int TAB_BAR_H    = 24;
    public static final int STATUS_H     = 18;
    public static final int PANEL_HEADER_H = 26;
    /** 导航侧栏宽度，兼顾层级缩进与连接目标显示。 */
    public static final int TREE_WIDTH   = 224;
    public static final int TREE_ROW_H   = 14;
    public static final int TREE_INDENT  = 12;
    public static final int PROP_TAB_H   = 18;
    public static final int PROP_TAB_W   = 56;
    public static final int LABEL_WIDTH  = 62;
    public static final int FIELD_HEIGHT = 18;
    public static final int BTN_HEIGHT   = 18;
    public static final int BTN_WIDTH    = 48;
    public static final int SECTION_HDR_H = 14;
    public static final int COLOR_BTN_SZ  = 16;
    public static final int COLOR_BTN_GAP = 3;
    public static final int CONTENT_BOX_H = 80;  // 多行文本框默认高度

    private EditorTheme() {
    }

    /** Industrial title rail: a light label surface, numbered yellow anchor and clipped hatch. */
    public static void drawPanelHeader(GuiGraphics g, Font font, int x, int y, int width,
                                       String index, Component title) {
        if (width < 1) return;
        g.enableScissor(x, y, x + width, y + PANEL_HEADER_H);
        try {
            g.fill(x, y, x + width, y + PANEL_HEADER_H, PANEL_LIGHT);
            g.fill(x, y, x + 4, y + PANEL_HEADER_H, ACCENT);
            g.drawString(font, index, x + 12, y + 9, ACCENT, false);
            g.drawString(font, font.plainSubstrByWidth(title.getString(), Math.max(1, width - 52)),
                    x + 38, y + 9, TEXT_PRIMARY, false);
            for (int i = 0; i < 3; i++) {
                g.fill(x + width - 11 + i * 3, y + 4, x + width - 10 + i * 3, y + 7, ACCENT_DIM);
            }
            g.fill(x, y + PANEL_HEADER_H - 1, x + width, y + PANEL_HEADER_H, BORDER);
        } finally {
            g.disableScissor();
        }
    }

    /** Small diagonal marks confined to a band; never painted underneath reading text. */
    public static void drawHatch(GuiGraphics g, int x, int y, int width, int height, int color) {
        if (width < 1 || height < 1) return;
        g.enableScissor(x, y, x + width, y + height);
        try {
            for (int i = -height; i < width; i += 8) {
                for (int j = 0; j < height; j++) g.fill(x + i + j, y + j, x + i + j + 2, y + j + 1, color);
            }
        } finally {
            g.disableScissor();
        }
    }

    /**
     * 绘制分节标题栏：带背景条、左侧 ACCENT 锚点竖条、底部半透明分割线、暖色阴影标题。
     * 第八轮美化：文字改暖米色 TEXT_WARM + 阴影、左侧 2px ACCENT 竖条（与 DialogTreeWidget 选中项统一视觉语言）、
     * 底线改半透明 DIVIDER（更轻盈），借鉴 Sparkle-Morpher 标题用暖色 + 锚点的视觉风格。
     */
    public static void drawSectionHeader(GuiGraphics graphics, Font font, int x, int y, int width, Component title) {
        graphics.fill(x, y, x + width, y + SECTION_HDR_H, BG_ELEVATED);
        graphics.fill(x, y + 3, x + 2, y + SECTION_HDR_H - 3, ACCENT);
        // 底部分割线：DIVIDER 半透明白，比 BORDER 实色更轻盈
        graphics.fill(x, y + SECTION_HDR_H - 1, x + width, y + SECTION_HDR_H, DIVIDER);
        // 标题文字：暖米色 + 阴影，比灰色无阴影更突出
        graphics.drawString(font, title, x + 6, y + 3, TEXT_WARM, true);
    }
}
