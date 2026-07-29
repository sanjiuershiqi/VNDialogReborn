package top.yourzi.dialog.editor.util;

import java.util.List;

/**
 * 游标式垂直流式布局：替代硬编码 Y 偏移，自动计算控件位置。
 * 参考 NeoForge LinearLayout 的设计思路，但兼容现有 init(x,y,w,h) 接口。
 *
 * 工作原理：
 * - 维护一个 Y 游标，从起始位置向下推进
 * - 每次添加控件时返回其 Y 坐标，并推进游标
 * - 最终通过 getContentHeight() 获取实际内容总高度
 * - 横向排列支持自动换行，防止高缩放屏幕下按钮溢出
 */
public class PageLayout {
    private final int originX;
    private final int originY;
    private final int totalWidth;
    private int cursorY;
    private int contentHeight;

    public PageLayout(int x, int y, int width) {
        this.originX = x;
        this.originY = y;
        this.totalWidth = width;
        this.cursorY = y;
        this.contentHeight = 0;
    }

    /** 字段区域的 X 起点（标签右侧） */
    public int fieldX() {
        return originX + EditorTheme.LABEL_WIDTH + EditorTheme.GAP;
    }

    /** 字段可用宽度（扣除标签和内边距） */
    public int fieldWidth() {
        return Math.max(40, totalWidth - EditorTheme.LABEL_WIDTH - EditorTheme.GAP * 2);
    }

    /** 标签的 X 位置 */
    public int labelX() {
        return originX + 5;
    }

    /** 当前游标 Y（用于绘制标签等非控件元素） */
    public int currentY() {
        return cursorY;
    }

    /** 推进空白间距 */
    public void spacer(int height) {
        cursorY += height;
        updateHeight();
    }

    /**
     * 开始一个新分节，推进游标并返回标题应绘制的 Y 坐标。
     * 调用方在 render() 中使用返回值绘制分节标题。
     */
    public int section() {
        if (cursorY > originY) {
            cursorY += EditorTheme.SECTION_GAP;
        }
        int headerY = cursorY;
        cursorY += EditorTheme.SECTION_HDR_H + EditorTheme.ROW_GAP;
        updateHeight();
        return headerY;
    }

    /**
     * 添加一个标准高度的字段行，返回字段应放置的 Y 坐标。
     */
    public int fieldRow() {
        int y = cursorY;
        cursorY += EditorTheme.FIELD_HEIGHT + EditorTheme.ROW_GAP;
        updateHeight();
        return y;
    }

    /**
     * 添加一个指定高度的字段行（如多行文本框），返回 Y 坐标。
     */
    public int customRow(int height) {
        int y = cursorY;
        cursorY += height + EditorTheme.ROW_GAP;
        updateHeight();
        return y;
    }

    /**
     * 横向排列多个控件，宽度不足时自动换行。
     * @param sizes 每个控件的 [width, height]
     * @param gap 控件间距
     * @return 每个控件实际放置的 [x, y] 坐标数组
     */
    public int[][] rowLayout(List<int[]> sizes, int gap) {
        int[][] positions = new int[sizes.size()][2];
        int availWidth = fieldWidth();
        int startX = fieldX();
        int x = startX;
        int rowH = 0;
        int rowY = cursorY;

        for (int i = 0; i < sizes.size(); i++) {
            int w = sizes.get(i)[0];
            int h = sizes.get(i)[1];
            // 换行条件：当前行已有控件且放不下下一个
            if (x > startX && x + w > startX + availWidth) {
                cursorY += rowH + EditorTheme.ROW_GAP;
                x = startX;
                rowH = 0;
                rowY = cursorY;
            }
            positions[i][0] = x;
            positions[i][1] = rowY;
            x += w + gap;
            rowH = Math.max(rowH, h);
        }
        cursorY += rowH + EditorTheme.ROW_GAP;
        updateHeight();
        return positions;
    }

    /**
     * 网格排列（如颜色按钮），自动计算列数和换行。
     * @param count 控件数量
     * @param itemWidth 单个控件宽度
     * @param itemHeight 单个控件高度
     * @param gap 控件间距
     * @return 每个控件的 [x, y] 坐标数组
     */
    public int[][] gridLayout(int count, int itemWidth, int itemHeight, int gap) {
        int[][] positions = new int[count][2];
        int availWidth = fieldWidth();
        int startX = fieldX();
        int itemsPerRow = Math.max(1, (availWidth + gap) / (itemWidth + gap));
        int x = startX;
        int rowY = cursorY;
        int rowH = 0;

        for (int i = 0; i < count; i++) {
            if (x + itemWidth > startX + availWidth) {
                cursorY += rowH + gap;
                x = startX;
                rowY = cursorY;
                rowH = 0;
            }
            positions[i][0] = x;
            positions[i][1] = rowY;
            x += itemWidth + gap;
            rowH = Math.max(rowH, itemHeight);
        }
        cursorY += rowH + EditorTheme.ROW_GAP;
        updateHeight();
        return positions;
    }

    /** 获取已布局内容的总高度 */
    public int getContentHeight() {
        updateHeight();
        return contentHeight;
    }

    private void updateHeight() {
        contentHeight = Math.max(contentHeight, cursorY - originY + EditorTheme.PADDING);
    }
}
