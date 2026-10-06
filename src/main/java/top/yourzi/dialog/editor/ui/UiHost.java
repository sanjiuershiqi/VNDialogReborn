package top.yourzi.dialog.editor.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns one UI tree plus its overlay stack and routes every Minecraft input event.
 *
 * <ul>
 *     <li>pointer: hit-test the top layer, then bubble from the deepest node to its ancestors;</li>
 *     <li>drag: the node that claimed the pointer receives drags and the release, nothing else;</li>
 *     <li>keyboard: the focused node first, then the top layer; modal layers swallow the rest;</li>
 *     <li>focus: cleared by any click that does not land on a node which re-requests it.</li>
 * </ul>
 */
public final class UiHost {
    private static final long TOOLTIP_DELAY_MS = 450L;

    private final UiNode root;
    private final List<Layer> layers = new ArrayList<>();
    private int width;
    private int height;
    private UiNode focused;
    private UiNode pointerOwner;
    private UiNode hovered;
    private boolean focusRequested;
    private UiNode tooltipNode;
    private long tooltipSince;

    public UiHost(UiNode root) {
        this.root = root;
        this.root.attach(this);
    }

    public UiNode root() {
        return this.root;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public UiNode hovered() {
        return this.hovered;
    }

    public UiNode focusedNode() {
        return this.focused;
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        this.root.setBounds(0, 0, width, height);
        for (Layer layer : new ArrayList<>(this.layers)) {
            if (layer.modal()) {
                layer.node().setBounds(0, 0, width, height);
            } else {
                this.close(layer);
            }
        }
    }

    // ----- layers -----

    public Layer open(UiNode node, boolean modal, boolean dismissOnOutsideClick) {
        Layer layer = new Layer(node, modal, dismissOnOutsideClick);
        node.attach(this);
        if (modal) {
            node.setBounds(0, 0, this.width, this.height);
            this.focus(null);
        }
        this.layers.add(layer);
        return layer;
    }

    public void close(UiNode node) {
        for (Layer layer : new ArrayList<>(this.layers)) {
            if (layer.node() == node) {
                this.close(layer);
            }
        }
    }

    public void close(Layer layer) {
        if (!this.layers.remove(layer)) {
            return;
        }
        if (this.focused != null && !this.isAttached(this.focused)) {
            this.focus(null);
        }
        if (this.pointerOwner != null && !this.isAttached(this.pointerOwner)) {
            this.pointerOwner = null;
        }
        layer.dismiss();
    }

    public boolean hasLayers() {
        return !this.layers.isEmpty();
    }

    private Layer topLayer() {
        return this.layers.isEmpty() ? null : this.layers.get(this.layers.size() - 1);
    }

    // ----- focus & pointer -----

    public void focus(UiNode node) {
        this.focusRequested = true;
        if (this.focused == node) {
            return;
        }
        if (this.focused != null) {
            this.focused.setFocused(false);
        }
        this.focused = node;
        if (node != null) {
            node.setFocused(true);
        }
    }

    public void claimPointer(UiNode node) {
        this.pointerOwner = node;
    }

    public boolean hasTextFocus() {
        return this.focused instanceof TextBox || this.focused instanceof TextArea;
    }

    private boolean isAttached(UiNode node) {
        UiNode top = node;
        while (top.parent() != null) {
            top = top.parent();
        }
        if (top == this.root) {
            return node.host() == this;
        }
        for (Layer layer : this.layers) {
            if (layer.node() == top) {
                return true;
            }
        }
        return false;
    }

    // ----- frame -----

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.root.flushLayout();
        for (Layer layer : this.layers) {
            layer.flushLayout();
        }
        if (this.focused != null && !this.isAttached(this.focused)) {
            this.focus(null);
        }
        this.hovered = this.pick(mouseX, mouseY);
        this.root.paint(graphics, mouseX, mouseY, partialTick);
        for (int i = 0; i < this.layers.size(); i++) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0f, 0.0f, 200.0f + i * 20.0f);
            this.layers.get(i).paint(graphics, mouseX, mouseY, partialTick);
            graphics.pose().popPose();
        }
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    private UiNode pick(double mouseX, double mouseY) {
        Layer top = this.topLayer();
        if (top != null) {
            return top.node().hitTest(mouseX, mouseY);
        }
        return this.root.hitTest(mouseX, mouseY);
    }

    private void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.pointerOwner != null) {
            this.tooltipNode = null;
            return;
        }
        UiNode source = null;
        for (UiNode node = this.hovered; node != null; node = node.parent()) {
            if (node.tooltip() != null) {
                source = node;
                break;
            }
        }
        if (source != this.tooltipNode) {
            this.tooltipNode = source;
            this.tooltipSince = System.currentTimeMillis();
        }
        if (source == null || System.currentTimeMillis() - this.tooltipSince < TOOLTIP_DELAY_MS) {
            return;
        }
        Component tooltip = source.tooltip();
        if (tooltip != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0f, 0.0f, 300.0f);
            graphics.renderTooltip(Theme.font(), tooltip, mouseX, mouseY);
            graphics.pose().popPose();
        }
    }

    // ----- input -----

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.focusRequested = false;
        boolean consumed;
        Layer top = this.topLayer();
        if (top != null && !top.node().contains(mouseX, mouseY)) {
            if (top.dismissOnOutsideClick()) {
                this.close(top);
                this.focusRequested = true;
                return true;
            }
            consumed = true;
        } else {
            UiNode scope = top != null ? top.node() : this.root;
            consumed = this.bubbleClick(scope.hitTest(mouseX, mouseY), mouseX, mouseY, button);
            if (top != null && top.modal()) {
                consumed = true;
            }
        }
        if (!this.focusRequested) {
            this.focus(null);
        }
        return consumed;
    }

    private boolean bubbleClick(UiNode target, double mouseX, double mouseY, int button) {
        if (target == null) {
            return false;
        }
        for (UiNode node : target.bubblePath()) {
            if (node.enabled() && node.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.pointerOwner == null) {
            return false;
        }
        return this.pointerOwner.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.pointerOwner == null) {
            return false;
        }
        UiNode owner = this.pointerOwner;
        this.pointerOwner = null;
        owner.mouseReleased(mouseX, mouseY, button);
        return true;
    }

    public void mouseMoved(double mouseX, double mouseY) {
        if (this.pointerOwner != null) {
            this.pointerOwner.onMouseMoved(mouseX, mouseY);
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Layer top = this.topLayer();
        if (top != null && !top.node().contains(mouseX, mouseY)) {
            return top.modal();
        }
        UiNode scope = top != null ? top.node() : this.root;
        UiNode target = scope.hitTest(mouseX, mouseY);
        if (target != null) {
            for (UiNode node : target.bubblePath()) {
                if (node.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                    return true;
                }
            }
        }
        return top != null;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.focused != null && this.focused.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        Layer top = this.topLayer();
        if (top == null) {
            return false;
        }
        if (top.node().keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close(top);
            return true;
        }
        return top.modal();
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return this.focused != null && this.focused.charTyped(codePoint, modifiers);
    }
}
