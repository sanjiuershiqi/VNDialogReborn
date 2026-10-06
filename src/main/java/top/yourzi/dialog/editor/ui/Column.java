package top.yourzi.dialog.editor.ui;

import net.minecraft.client.gui.GuiGraphics;

/** Vertical stack: every visible child gets the full inner width and its measured height. */
public class Column extends UiNode {
    private int gap = Theme.GAP;
    private int padding;
    private int background;
    private int borderColor;

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

    public Column background(int color) {
        this.background = color;
        return this;
    }

    public Column border(int color) {
        this.borderColor = color;
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

    @Override
    protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.background != 0) {
            graphics.fill(this.x(), this.y(), this.right(), this.bottom(), this.background);
        }
        if (this.borderColor != 0) {
            Theme.border(graphics, this.x(), this.y(), this.width(), this.height(), this.borderColor);
        }
    }
}
