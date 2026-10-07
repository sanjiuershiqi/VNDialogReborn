package top.yourzi.dialog.editor.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Vertical scroll container around one content node. The content is measured with
 * {@link UiNode#measureHeight(int)}, clipped to the viewport and only receives pointer input
 * through the viewport (the hit test stops at this node's box).
 */
public class ScrollView extends UiNode {
    private static final int STEP = 20;

    private final UiNode content;
    private double offset;
    private int contentHeight;
    private boolean draggingBar;

    public ScrollView(UiNode content) {
        this.content = content;
        this.add(content);
    }

    public UiNode content() {
        return this.content;
    }

    public void setOffset(double offset) {
        this.offset = offset;
        this.invalidateLayout();
    }

    private int maxOffset() {
        return Math.max(0, this.contentHeight - this.height());
    }

    @Override
    protected void onLayout() {
        int innerWidth = this.width() - Theme.SCROLLBAR - 2;
        this.contentHeight = this.content.measureHeight(innerWidth);
        this.offset = Mth.clamp(this.offset, 0, this.maxOffset());
        this.content.setBounds(this.x(), this.y() - (int) this.offset, innerWidth, Math.max(this.contentHeight, this.height()));
    }

    /** Scrolls the minimum amount needed to show the given absolute vertical span. */
    public void reveal(int top, int bottom) {
        if (top < this.y()) {
            this.setOffset(this.offset - (this.y() - top));
        } else if (bottom > this.bottom()) {
            this.setOffset(this.offset + (bottom - this.bottom()));
        }
    }

    @Override
    protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Theme.clip(graphics, this.x(), this.y(), this.right(), this.bottom());
    }

    @Override
    protected void renderAfterChildren(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Theme.unclip(graphics);
        Theme.scrollbar(graphics, this.right() - Theme.SCROLLBAR, this.y(), this.height(), this.height(),
                this.contentHeight, this.offset, this.draggingBar);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.maxOffset() > 0 && mouseX >= this.right() - Theme.SCROLLBAR - 2) {
            this.draggingBar = true;
            this.host().claimPointer(this);
            this.dragTo(mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingBar) {
            this.dragTo(mouseY);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingBar = false;
        return true;
    }

    private void dragTo(double mouseY) {
        double ratio = (mouseY - this.y()) / Math.max(1, this.height());
        this.setOffset(Mth.clamp(ratio, 0, 1) * this.maxOffset());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.maxOffset() <= 0) {
            return false;
        }
        this.setOffset(this.offset - scrollY * STEP);
        return true;
    }
}
