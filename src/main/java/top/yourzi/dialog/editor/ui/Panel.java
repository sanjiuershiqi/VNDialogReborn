package top.yourzi.dialog.editor.ui;

import net.minecraft.client.gui.GuiGraphics;

/** A container with a background and border, wrapping a single child. */
public class Panel extends UiNode {
    private final UiNode child;
    private int fill = Theme.SURFACE;
    private int border = Theme.BORDER;
    private int padding;

    public Panel(UiNode child) {
        this.child = child;
        if (child != null) {
            this.add(child);
        }
    }

    public static Panel flat(UiNode child, int fill, int border) {
        Panel panel = new Panel(child);
        panel.fill = fill;
        panel.border = border;
        return panel;
    }

    public Panel padding(int padding) {
        this.padding = padding;
        this.invalidateLayout();
        return this;
    }

    public Panel style(int fill, int border) {
        this.fill = fill;
        this.border = border;
        return this;
    }

    public UiNode child() {
        return this.child;
    }

    @Override
    protected void onLayout() {
        if (this.child != null) {
            this.child.setBounds(this.x() + this.padding, this.y() + this.padding,
                    this.width() - this.padding * 2, this.height() - this.padding * 2);
        }
    }

    @Override
    protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.fill != 0) {
            graphics.fill(this.x(), this.y(), this.right(), this.bottom(), this.fill);
        }
        if (this.border != 0) {
            Theme.border(graphics, this.x(), this.y(), this.width(), this.height(), this.border);
        }
    }
}
