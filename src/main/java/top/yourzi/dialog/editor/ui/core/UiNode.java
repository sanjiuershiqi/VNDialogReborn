package top.yourzi.dialog.editor.ui.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Retained UI node with a stable parent/child tree and overridable event hooks. */
public class UiNode {
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private UiStyle style = UiStyle.defaults();
    private UiNode parent;
    private boolean visible = true;
    private boolean enabled = true;
    private final List<UiNode> children = new ArrayList<>();

    public UiRect bounds() { return this.bounds; }
    public void setBounds(UiRect bounds) { this.bounds = bounds == null ? new UiRect(0, 0, 0, 0) : bounds; }
    public UiStyle style() { return this.style; }
    public void setStyle(UiStyle style) { this.style = style == null ? UiStyle.defaults() : style; }
    public UiNode parent() { return this.parent; }
    public boolean isVisible() { return this.visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public boolean isEnabled() { return this.enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<UiNode> children() { return Collections.unmodifiableList(this.children); }

    public void addChild(UiNode child) {
        if (child == null || child == this) return;
        if (child.parent != null) child.parent.removeChild(child);
        child.parent = this;
        this.children.add(child);
    }

    public void removeChild(UiNode child) {
        if (this.children.remove(child) && child != null) child.parent = null;
    }

    public boolean hitTest(double x, double y) {
        return this.visible && this.enabled && this.bounds.contains(x, y);
    }

    /** Capture phase hook; parents can consume before a child sees the event. */
    public void handleCapture(UiEvent event) {
    }

    /** Target phase hook; subclasses implement their own interaction here. */
    public void handleEvent(UiEvent event) {
    }

    /** Bubble phase hook; parents can observe an event after the target. */
    public void handleBubble(UiEvent event) {
    }

    /** Called once per frame by a future retained renderer. */
    public void tick(float deltaSeconds) {
        for (UiNode child : this.children) {
            if (child.visible) child.tick(deltaSeconds);
        }
    }

    /** Render hook; the renderer decides how to draw this node and its children. */
    public void render(UiContext context) {
        for (UiNode child : this.children) {
            if (child.visible) child.render(context);
        }
    }
}
