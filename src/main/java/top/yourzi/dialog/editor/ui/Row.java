package top.yourzi.dialog.editor.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Horizontal stack. Children with a preferred width keep it; the rest share the remaining space
 * by their flex weight. Every child spans the full row height.
 */
public class Row extends UiNode {
    private int gap = Theme.GAP;

    public Row gap(int gap) {
        this.gap = gap;
        this.invalidateLayout();
        return this;
    }

    @Override
    protected void onLayout() {
        List<UiNode> visible = this.visibleChildren();
        int[] widths = this.distribute(visible, this.width());
        int cursor = this.x();
        for (int i = 0; i < visible.size(); i++) {
            visible.get(i).setBounds(cursor, this.y(), widths[i], this.height());
            cursor += widths[i] + this.gap;
        }
    }

    @Override
    public int measureHeight(int width) {
        List<UiNode> visible = this.visibleChildren();
        int[] widths = this.distribute(visible, width);
        int max = 0;
        for (int i = 0; i < visible.size(); i++) {
            max = Math.max(max, visible.get(i).measureHeight(widths[i]));
        }
        return max == 0 ? super.measureHeight(width) : max;
    }

    private List<UiNode> visibleChildren() {
        List<UiNode> visible = new ArrayList<>();
        for (UiNode child : this.children()) {
            if (child.visible()) {
                visible.add(child);
            }
        }
        return visible;
    }

    private int[] distribute(List<UiNode> visible, int total) {
        int[] widths = new int[visible.size()];
        int fixed = Math.max(0, visible.size() - 1) * this.gap;
        int flexSum = 0;
        for (UiNode child : visible) {
            if (child.preferredWidth() >= 0) {
                fixed += child.preferredWidth();
            } else {
                flexSum += child.flex();
            }
        }
        int remaining = Math.max(0, total - fixed);
        int handed = 0;
        int lastFlex = -1;
        for (int i = 0; i < visible.size(); i++) {
            UiNode child = visible.get(i);
            if (child.preferredWidth() >= 0) {
                widths[i] = child.preferredWidth();
            } else if (flexSum > 0) {
                widths[i] = remaining * child.flex() / flexSum;
                handed += widths[i];
                lastFlex = i;
            }
        }
        if (lastFlex >= 0) {
            widths[lastFlex] += remaining - handed;
        }
        return widths;
    }
}
