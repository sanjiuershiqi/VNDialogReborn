package top.yourzi.dialog.editor.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Retained UI node.
 *
 * <p>Layout contract: a node owns an absolute box in screen coordinates. {@link #onLayout()} runs
 * whenever the box or the child list changed, and each container places its own children. Widgets
 * read {@link #x()} / {@link #y()} while drawing, so nothing has to guess a coordinate.
 *
 * <p>Pointer contract: the host dispatches to the deepest visible node under the cursor, then
 * bubbles to ancestors until one consumes the event. A node that starts a drag claims the pointer
 * through {@link UiHost#claimPointer} until release.
 */
public abstract class UiNode {
    private int x;
    private int y;
    private int width;
    private int height;
    private UiNode parent;
    private final List<UiNode> children = new ArrayList<>();
    private boolean visible = true;
    private boolean enabled = true;
    private boolean focused;
    private boolean layoutDirty = true;
    private UiHost host;
    private Component tooltip;
    private int preferredHeight = Theme.ROW;
    private int preferredWidth = -1;
    private int flex = 1;

    /** Height requested from vertical containers for the given width. */
    public int measureHeight(int width) {
        return this.preferredHeight;
    }

    /** Fixed width requested from {@link Row}; negative means "share remaining space by flex". */
    public int preferredWidth() {
        return this.preferredWidth;
    }

    public int flex() {
        return this.flex;
    }

    @SuppressWarnings("unchecked")
    public <T extends UiNode> T prefHeight(int height) {
        this.preferredHeight = height;
        this.invalidateLayout();
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public <T extends UiNode> T prefWidth(int width) {
        this.preferredWidth = width;
        this.invalidateLayout();
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public <T extends UiNode> T flex(int flex) {
        this.flex = Math.max(0, flex);
        this.invalidateLayout();
        return (T) this;
    }

    public final int x() {
        return this.x;
    }

    public final int y() {
        return this.y;
    }

    public final int width() {
        return this.width;
    }

    public final int height() {
        return this.height;
    }

    public final int right() {
        return this.x + this.width;
    }

    public final int bottom() {
        return this.y + this.height;
    }

    public final UiNode parent() {
        return this.parent;
    }

    public final List<UiNode> children() {
        return this.children;
    }

    public final boolean visible() {
        return this.visible;
    }

    public final boolean enabled() {
        return this.enabled;
    }

    public final void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setVisible(boolean visible) {
        if (this.visible != visible) {
            this.visible = visible;
            this.invalidateLayout();
        }
    }

    public final void setBounds(int x, int y, int width, int height) {
        int w = Math.max(0, width);
        int h = Math.max(0, height);
        if (this.x == x && this.y == y && this.width == w && this.height == h) {
            return;
        }
        this.x = x;
        this.y = y;
        this.width = w;
        this.height = h;
        this.invalidateLayout();
    }

    public final void setPosition(int x, int y) {
        this.setBounds(x, y, this.width, this.height);
    }

    public final void setSize(int width, int height) {
        this.setBounds(this.x, this.y, width, height);
    }

    /** Marks this subtree dirty and propagates up so the next flush re-lays out from the top. */
    public final void invalidateLayout() {
        this.layoutDirty = true;
        if (this.parent != null) {
            this.parent.invalidateLayout();
        }
    }

    /** Closes the overlay layer this node belongs to; a no-op for the workspace tree. */
    public final void dismissLayer() {
        UiNode top = this;
        while (top.parent() != null) {
            top = top.parent();
        }
        if (this.host != null && top != this.host.root()) {
            this.host.close(top);
        }
    }

    public final void flushLayout() {
        if (this.layoutDirty) {
            this.layoutDirty = false;
            if (this.visible && this.width > 0 && this.height > 0) {
                this.onLayout();
            }
        }
        for (UiNode child : this.children) {
            child.flushLayout();
        }
    }

    protected void onLayout() {
    }

    public void add(UiNode child) {
        if (child == null || child == this) {
            return;
        }
        child.parent = this;
        this.children.add(child);
        child.attach(this.host);
        this.invalidateLayout();
    }

    public void addAll(UiNode... nodes) {
        for (UiNode node : nodes) {
            this.add(node);
        }
    }

    public void clear() {
        for (UiNode child : this.children) {
            child.parent = null;
            child.host = null;
        }
        this.children.clear();
        this.invalidateLayout();
    }

    public void remove(UiNode child) {
        if (this.children.remove(child)) {
            child.parent = null;
            child.host = null;
            this.invalidateLayout();
        }
    }

    public final boolean contains(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
    }

    public final void paint(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }
        this.render(graphics, mouseX, mouseY, partialTick);
        for (UiNode child : this.children) {
            child.paint(graphics, mouseX, mouseY, partialTick);
        }
        this.renderAfterChildren(graphics, mouseX, mouseY, partialTick);
    }

    protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    protected void renderAfterChildren(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    // ----- pointer -----

    /** @return true when the event is consumed and must not reach ancestors. */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    public void onMouseMoved(double mouseX, double mouseY) {
    }

    // ----- keyboard -----

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return false;
    }

    public final boolean focused() {
        return this.focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    // ----- host plumbing -----

    public final UiHost host() {
        return this.host;
    }

    final void attach(UiHost host) {
        this.host = host;
        for (UiNode child : this.children) {
            child.attach(host);
        }
    }

    /** True when the cursor is over this node or one of its descendants, honoring overlays. */
    public final boolean isHovered() {
        if (this.host == null) {
            return false;
        }
        for (UiNode node = this.host.hovered(); node != null; node = node.parent) {
            if (node == this) {
                return true;
            }
        }
        return false;
    }

    /** Tooltip shown after the hover delay; null hides it. */
    public Component tooltip() {
        return this.tooltip;
    }

    @SuppressWarnings("unchecked")
    public <T extends UiNode> T withTooltip(Component tooltip) {
        this.tooltip = tooltip;
        return (T) this;
    }

    /** Deepest enabled node under the cursor, searching front to back. */
    final UiNode hitTest(double mouseX, double mouseY) {
        if (!this.visible || !this.contains(mouseX, mouseY)) {
            return null;
        }
        for (int i = this.children.size() - 1; i >= 0; i--) {
            UiNode hit = this.children.get(i).hitTest(mouseX, mouseY);
            if (hit != null) {
                return hit;
            }
        }
        return this.enabled ? this : null;
    }

    /** Ancestor chain from this node up to the root, used for event bubbling. */
    final List<UiNode> bubblePath() {
        List<UiNode> path = new ArrayList<>();
        for (UiNode node = this; node != null; node = node.parent) {
            path.add(node);
        }
        return path;
    }
}
