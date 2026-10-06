package top.yourzi.dialog.editor.ui;

/** Vertical stack: every visible child gets the full inner width and its measured height. */
public class Column extends UiNode {
    private int gap = Theme.GAP;
    private int padding;

    public Column gap(int gap) {
        this.gap = gap;
        this.invalidateLayout();
        return this;
    }

    public Column padding(int padding) {
        this.padding = padding;
        this.invalidateLayout();
        return this;
    }

    @Override
    protected void onLayout() {
        int cursor = this.y() + this.padding;
        int innerWidth = this.width() - this.padding * 2;
        for (UiNode child : this.children()) {
            if (!child.visible()) {
                continue;
            }
            int h = child.measureHeight(innerWidth);
            child.setBounds(this.x() + this.padding, cursor, innerWidth, h);
            cursor += h + this.gap;
        }
    }

    @Override
    public int measureHeight(int width) {
        int total = this.padding * 2;
        int count = 0;
        for (UiNode child : this.children()) {
            if (child.visible()) {
                total += child.measureHeight(width - this.padding * 2);
                count++;
            }
        }
        return total + Math.max(0, count - 1) * this.gap;
    }
}
