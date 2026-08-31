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
    public static final int BG_DEEPEST     = 0xFF121212;  // 屏幕底色
    public static final int BG_SURFACE     = 0xFF1C1C1C;  // 面板底色
    public static final int BG_ELEVATED    = 0xFF282828;  // 标签栏/分节标题
    public static final int BG_HOVER       = 0xFF353535;  // 悬停
    public static final int BG_SELECTED    = 0xFF1A3354;  // 选中（蓝调）
    public static final int BORDER         = 0xFF383838;  // 分隔线
    public static final int BORDER_LIGHT   = 0xFF444444;  // 高亮分隔线
    public static final int TEXT_PRIMARY   = 0xFFFFFFFF;
    public static final int TEXT_SECONDARY = 0xFFB0B0B0;
    public static final int TEXT_MUTED     = 0xFF707070;
    public static final int ACCENT         = 0xFF4A9EFF;  // 主强调色（蓝）
    public static final int ACCENT_DIM     = 0xFF2A5A8A;
    public static final int DANGER         = 0xFFE05555;  // 删除/危险

    // ===== 视觉美化色板（第八轮，借鉴 Sparkle-Morpher RoulettePanelStyle/RouletteTheme） =====
    // 暖米色文字：比纯白柔和耐看，用于标题/重要值（Sparkle 面板主文字色 0xFFEDE1CC）
    public static final int TEXT_WARM      = 0xFFEDE1CC;
    // 浮层投影：30% 黑，浮层/弹窗四周外扩阴影，制造悬浮感（借鉴 Sparkle SLICE_SHADOW 思路）
    public static final int SHADOW_DROP    = 0x4D000000;
    // 内发光：22% 白，面板顶部 1px 叠层模拟顶光立体感（借鉴 Sparkle SLICE_INNER_GLOW）
    public static final int SHADOW_INNER_GLOW = 0x36FFFFFF;
    // 强调发光：25% 蓝，hover 外发光层（比硬描边柔和）
    public static final int GLOW_ACCENT    = 0x404A9EFF;
    // 玻璃面板色系：半透明叠层产生磨砂观感（无需 GLSL 模糊也能有玻璃感）
    public static final int PANEL_GLASS       = 0x60405058;  // 玻璃底（借鉴 Sparkle GLASS）
    public static final int PANEL_GLASS_HOVER = 0x66576B76;  // 玻璃 hover（借鉴 Sparkle PANEL_HOVER）
    public static final int PANEL_GLASS_BORDER = 0x6EE4F5FF; // 半透明亮蓝边框（玻璃边缘反光）

    // ===== 状态栏语义色（借鉴 Sparkle setStatus 分色，用主题色而非 ChatFormatting） =====
    public static final int STATUS_SUCCESS = 0xFF6AC46A;  // 成功（绿）
    public static final int STATUS_WARNING = 0xFFE0A040;  // 警告（黄/橙）
    public static final int STATUS_ERROR   = 0xFFE05555;  // 错误（红，与 DANGER 同值但语义独立）

    // ===== 语义化半透明色 =====
    // 收编散落在各 Screen 的 0x33FFFFFF / 0xCC000000 / 0x804A9EFF 等魔法数字，
    // 让半透明叠层也走主题系统。语义命名，便于全局调整。
    public static final int OVERLAY_MASK    = 0xCC000000;  // 模态遮罩（InputDialog/Confirm）
    public static final int HOVER_TINT      = 0x33FFFFFF;  // 悬停提亮叠层
    public static final int ACCENT_TINT     = 0x804A9EFF;  // 强调半透明（选中描边等）
    public static final int DIVIDER         = 0x40FFFFFF;  // 分隔线/网格辅助线
    public static final int SCROLLBAR_TRACK = 0x33000000;  // 滚动条轨道
    public static final int SCROLLBAR_THUMB = 0x80B0B0B0;  // 滚动条滑块
    // 浮层/弹出面板底色：比 BG_SURFACE 更暗一档，与投影配合区分层级（收编 DropdownWidget 硬编码 0xFF181818）
    public static final int POPUP_BG       = 0xFF181818;

    // ===== 画布专用色（节点画布视图） =====
    public static final int CANVAS_BG      = 0xFF161616;  // 画布底色（比 BG_SURFACE 更深，衬托节点卡片）
    public static final int EDGE_NEXT      = 0xFF4A9EFF;  // 显式 next 边（与 ACCENT 同值但语义独立）
    public static final int EDGE_IMPLICIT  = 0xFF707070;  // 隐式顺序边（数组顺序回退，虚线）
    /** 选项边调色板：按选项索引取色，与节点端口圆点一一对应。 */
    public static final int[] OPTION_PALETTE = {
            0xFF6AC46A, 0xFFE0A040, 0xFFE070B8, 0xFF58C8D8,
            0xFFB08CFF, 0xFFE05555, 0xFF98D86A, 0xFFD8A858
    };

    // ===== 间距 =====
    public static final int PADDING      = 6;
    public static final int GAP          = 5;
    public static final int GAP_TIGHT    = 3;
    public static final int SECTION_GAP  = 8;
    public static final int ROW_GAP      = 4;   // 行间距
    public static final int SCROLLBAR_W  = 5;

    // ===== 尺寸 =====
    public static final int TOOLBAR_H    = 24;
    public static final int TAB_BAR_H    = 22;
    public static final int STATUS_H     = 14;
    /** 导航侧栏需要容纳节点摘要与状态标记，避免 ID/连线目标挤在同一行。 */
    public static final int TREE_WIDTH   = 224;
    public static final int TREE_ROW_H   = 24;
    public static final int TREE_INDENT  = 8;
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

    /**
     * 绘制分节标题栏：带背景条、左侧 ACCENT 锚点竖条、底部半透明分割线、暖色阴影标题。
     * 第八轮美化：文字改暖米色 TEXT_WARM + 阴影、左侧 2px ACCENT 竖条（与 DialogTreeWidget 选中项统一视觉语言）、
     * 底线改半透明 DIVIDER（更轻盈），借鉴 Sparkle-Morpher 标题用暖色 + 锚点的视觉风格。
     */
    public static void drawSectionHeader(GuiGraphics graphics, Font font, int x, int y, int width, Component title) {
        graphics.fill(x, y, x + width, y + SECTION_HDR_H, BG_ELEVATED);
        // 底部分割线：DIVIDER 半透明白，比 BORDER 实色更轻盈
        graphics.fill(x, y + SECTION_HDR_H - 1, x + width, y + SECTION_HDR_H, DIVIDER);
        // 标题文字：暖米色 + 阴影，比灰色无阴影更突出
        graphics.drawString(font, title, x + 6, y + 3, TEXT_WARM, true);
    }
}
