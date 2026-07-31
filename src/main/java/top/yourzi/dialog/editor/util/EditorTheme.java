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
    public static final int TREE_WIDTH   = 180;
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

    /**
     * 绘制分节标题栏：带背景条和底部边框线的标题。
     */
    public static void drawSectionHeader(GuiGraphics graphics, Font font, int x, int y, int width, Component title) {
        graphics.fill(x, y, x + width, y + SECTION_HDR_H, BG_ELEVATED);
        graphics.fill(x, y + SECTION_HDR_H - 1, x + width, y + SECTION_HDR_H, BORDER);
        graphics.drawString(font, title, x + 4, y + 3, TEXT_SECONDARY);
    }
}
